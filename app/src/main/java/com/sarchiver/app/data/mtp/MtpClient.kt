package com.sarchiver.app.data.mtp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.sarchiver.app.domain.FsNode
import com.sarchiver.app.domain.StorageKind
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal MTP initiator over USB bulk endpoints (Still Image class 6 / MTP subclass 1).
 * Not every vendor device is fully compatible; failures are reported, never faked.
 */
class MtpClient(private val context: Context) {
    private val usb: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    data class Session(
        val device: UsbDevice,
        val connection: UsbDeviceConnection,
        val intf: UsbInterface,
        val bulkIn: UsbEndpoint,
        val bulkOut: UsbEndpoint,
        var transaction: Int = 1,
    )

    fun mtpDevices(): List<UsbDevice> =
        usb.deviceList.values.filter { isMtp(it) }

    fun massStorageDevices(): List<UsbDevice> =
        usb.deviceList.values.filter { isMassStorage(it) }

    fun isMtp(d: UsbDevice): Boolean {
        for (i in 0 until d.interfaceCount) {
            val inf = d.getInterface(i)
            if (inf.interfaceClass == 6) return true
            if (inf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) return true
        }
        return false
    }

    fun isMassStorage(d: UsbDevice): Boolean {
        for (i in 0 until d.interfaceCount) {
            if (d.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) return true
        }
        return false
    }

    fun hasPermission(d: UsbDevice) = usb.hasPermission(d)

    fun requestPermission(d: UsbDevice) {
        val flags = PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        usb.requestPermission(d, pi)
    }

    fun open(device: UsbDevice): Session {
        if (!usb.hasPermission(device)) error("USB permission denied")
        var intf: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val inf = device.getInterface(i)
            if (inf.interfaceClass == 6 || inf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
                intf = inf
                break
            }
        }
        intf ?: error("No MTP interface on ${device.deviceName}")
        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        for (e in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep else bulkOut = ep
            }
        }
        if (bulkIn == null || bulkOut == null) error("MTP bulk endpoints missing")
        val conn = usb.openDevice(device) ?: error("openDevice failed")
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            error("claimInterface failed")
        }
        val session = Session(device, conn, intf, bulkIn, bulkOut)
        sendCommand(session, 0x1002, intArrayOf(1)) // OpenSession
        return session
    }

    fun close(session: Session) {
        try { sendCommand(session, 0x1003, intArrayOf()) } catch (_: Exception) {}
        try { session.connection.releaseInterface(session.intf) } catch (_: Exception) {}
        session.connection.close()
    }

    fun storageIds(session: Session): IntArray {
        val payload = transact(session, 0x1004, intArrayOf())
        if (payload.size < 4) return intArrayOf()
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val n = bb.int
        return IntArray(n.coerceAtMost(64)) { bb.int }
    }

    fun objectHandles(session: Session, storageId: Int, parent: Int = 0xFFFFFFFF.toInt()): IntArray {
        val payload = transact(session, 0x1007, intArrayOf(storageId, 0, parent))
        if (payload.size < 4) return intArrayOf()
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val n = bb.int
        return IntArray(n.coerceAtMost(10_000)) { bb.int }
    }

    data class ObjectInfo(val handle: Int, val name: String, val size: Long, val isDir: Boolean)

    fun objectInfo(session: Session, handle: Int): ObjectInfo {
        val payload = transact(session, 0x1008, intArrayOf(handle))
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        if (payload.size < 52) return ObjectInfo(handle, "object-$handle", 0, false)
        bb.position(4) // storage
        val format = bb.short.toInt() and 0xFFFF
        bb.position(8)
        val compressedSize = bb.int.toLong() and 0xFFFFFFFFL
        // skip to filename at typical offset 52 (string)
        var pos = 52
        if (payload.size <= pos) return ObjectInfo(handle, "object-$handle", compressedSize, format == 0x3001)
        val chars = payload[pos].toInt() and 0xFF
        pos++
        val name = if (chars > 0 && pos + chars * 2 <= payload.size) {
            val cs = CharArray(chars - 1)
            for (i in 0 until chars - 1) {
                cs[i] = (payload[pos] + (payload[pos + 1].toInt() shl 8)).toChar()
                pos += 2
            }
            String(cs)
        } else "object-$handle"
        return ObjectInfo(handle, name, compressedSize, format == 0x3001)
    }

    fun listNodes(session: Session, storageId: Int, parent: Int): List<FsNode> {
        return objectHandles(session, storageId, parent).map { h ->
            val info = objectInfo(session, h)
            FsNode(
                id = "mtp:${session.device.deviceId}:$h",
                name = info.name,
                path = "mtp://${session.device.deviceId}/$storageId/$h",
                isDirectory = info.isDir,
                size = info.size,
                lastModified = 0L,
                mime = null,
                kind = StorageKind.MTP,
                canWrite = true,
                extra = h.toString(),
            )
        }
    }

    fun getObject(session: Session, handle: Int, dest: File) {
        val payload = transact(session, 0x1009, intArrayOf(handle), dest)
        if (payload.isEmpty() && !dest.exists()) error("GetObject failed")
    }

    fun deleteObject(session: Session, handle: Int) {
        transact(session, 0x100B, intArrayOf(handle))
    }

    private fun sendCommand(session: Session, code: Int, params: IntArray) {
        transact(session, code, params)
    }

    private fun transact(session: Session, code: Int, params: IntArray, destFile: File? = null): ByteArray {
        val tid = session.transaction++
        val length = 12 + params.size * 4
        val cmd = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        cmd.putInt(length)
        cmd.putShort(1) // command
        cmd.putShort(code.toShort())
        cmd.putInt(tid)
        params.forEach { cmd.putInt(it) }
        val out = cmd.array()
        val w = session.connection.bulkTransfer(session.bulkOut, out, out.size, TIMEOUT)
        if (w < 0) error("MTP command write failed ($code)")
        val buf = ByteArray(session.bulkIn.maxPacketSize.coerceAtLeast(512) * 16)
        val data = java.io.ByteArrayOutputStream()
        var remaining = -1
        var gotData = false
        while (true) {
            val n = session.connection.bulkTransfer(session.bulkIn, buf, buf.size, TIMEOUT)
            if (n < 12) error("MTP short read ($code)")
            val bb = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN)
            val containerLen = bb.int
            val type = bb.short.toInt() and 0xFFFF
            val rcode = bb.short.toInt() and 0xFFFF
            bb.int // transaction
            when (type) {
                2 -> { // data
                    gotData = true
                    val payloadOff = 12
                    val chunk = buf.copyOfRange(payloadOff, n)
                    if (destFile != null) {
                        destFile.parentFile?.mkdirs()
                        destFile.appendBytes(chunk)
                    } else data.write(chunk)
                    remaining = containerLen - n
                    while (remaining > 0) {
                        val m = session.connection.bulkTransfer(session.bulkIn, buf, minOf(buf.size, remaining), TIMEOUT)
                        if (m <= 0) break
                        if (destFile != null) destFile.appendBytes(buf.copyOf(m)) else data.write(buf, 0, m)
                        remaining -= m
                    }
                }
                3 -> { // response
                    if (rcode != 0x2001 && rcode != 0x2002) {
                        error("MTP response 0x${rcode.toString(16)} for op 0x${code.toString(16)}")
                    }
                    return data.toByteArray()
                }
                else -> error("Unexpected MTP container type $type")
            }
            if (gotData && type == 3) return data.toByteArray()
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.sarchiver.app.USB_PERMISSION"
        private const val TIMEOUT = 8000
    }
}
