package com.sarchiver.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sarchiver.app.SarchiverApp
import com.sarchiver.app.data.archive.ArchiveEngine
import com.sarchiver.app.data.archive.ArchiveFormat
import com.sarchiver.app.data.mtp.MtpClient
import com.sarchiver.app.data.storage.StorageRepository
import com.sarchiver.app.data.transfer.TransferEngine
import com.sarchiver.app.data.transfer.TransferService
import com.sarchiver.app.domain.FsNode
import com.sarchiver.app.domain.SortMode
import com.sarchiver.app.domain.StorageKind
import com.sarchiver.app.domain.TransferProgress
import com.sarchiver.app.domain.ViewMode
import com.sarchiver.app.ui.theme.ThemeMode
import com.sarchiver.app.util.FileSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class BrowserState(
    val path: String = "",
    val crumbs: List<String> = emptyList(),
    val items: List<FsNode> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val sort: SortMode = SortMode.NAME,
    val viewMode: ViewMode = ViewMode.LIST,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val clipboardCut: Boolean = false,
    val clipboard: List<String> = emptyList(),
    val bookmarks: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val transfers: List<TransferProgress> = emptyList(),
    val storageRoots: List<FsNode> = emptyList(),
    val mtpDevices: List<String> = emptyList(),
    val properties: FsNode? = null,
    val propertiesSize: Long? = null,
    val showCreateFolder: Boolean = false,
    val showCompress: Boolean = false,
    val archiveEntries: List<com.sarchiver.app.domain.ArchiveEntryInfo>? = null,
    val archiveFile: String? = null,
    val permissionNeeded: Boolean = false,
)

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = StorageRepository(app)
    private val engine: TransferEngine = (app as SarchiverApp).transferEngine
    private val mtp = MtpClient(app)

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state

    init {
        val start = Environment.getExternalStorageDirectory()?.absolutePath
            ?: app.filesDir.absolutePath
        open(start)
        refreshRoots()
    }

    fun refreshRoots() {
        viewModelScope.launch(Dispatchers.IO) {
            val roots = storage.roots()
            val mtpNames = mtp.mtpDevices().map { it.productName ?: it.deviceName }
            _state.update { it.copy(storageRoots = roots, mtpDevices = mtpNames) }
        }
    }

    fun open(path: String, pushHistory: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true, error = null, selected = emptySet(), archiveEntries = null) }
            try {
                val items = storage.list(path)
                _state.update { st ->
                    val hist = if (pushHistory) {
                        val trimmed = st.history.take(st.historyIndex + 1) + path
                        trimmed
                    } else st.history
                    val idx = if (pushHistory) hist.lastIndex else st.historyIndex
                    st.copy(
                        path = path,
                        crumbs = path.split('/').filter { it.isNotEmpty() },
                        items = FileSort.sort(FileSort.filter(items, st.query, null), st.sort),
                        loading = false,
                        history = hist,
                        historyIndex = idx,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun back() {
        val st = _state.value
        if (st.historyIndex > 0) {
            val p = st.history[st.historyIndex - 1]
            _state.update { it.copy(historyIndex = it.historyIndex - 1) }
            open(p, pushHistory = false)
        } else {
            File(st.path).parent?.let { open(it) }
        }
    }

    fun forward() {
        val st = _state.value
        if (st.historyIndex < st.history.lastIndex) {
            val p = st.history[st.historyIndex + 1]
            _state.update { it.copy(historyIndex = it.historyIndex + 1) }
            open(p, pushHistory = false)
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        val path = _state.value.path
        viewModelScope.launch(Dispatchers.IO) {
            val items = storage.list(path)
            _state.update { st -> st.copy(items = FileSort.sort(FileSort.filter(items, q, null), st.sort)) }
        }
    }

    fun setSort(mode: SortMode) {
        _state.update { st -> st.copy(sort = mode, items = FileSort.sort(st.items, mode)) }
    }

    fun toggleView() {
        _state.update { it.copy(viewMode = if (it.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) }
    }

    fun cycleTheme() {
        _state.update {
            it.copy(
                theme = when (it.theme) {
                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.SYSTEM
                }
            )
        }
    }

    fun toggleSelect(id: String) {
        _state.update {
            val s = it.selected.toMutableSet()
            if (!s.add(id)) s.remove(id)
            it.copy(selected = s)
        }
    }

    fun selectAll() {
        _state.update { it.copy(selected = it.items.map { n -> n.id }.toSet()) }
    }

    fun clearSelect() = _state.update { it.copy(selected = emptySet()) }

    fun createFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                storage.createFolder(_state.value.path, name)
                open(_state.value.path, false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
        _state.update { it.copy(showCreateFolder = false) }
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                storage.rename(path, newName)
                open(_state.value.path, false)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteSelected() {
        val sel = _state.value.selected.toList()
        viewModelScope.launch(Dispatchers.IO) {
            sel.forEach { storage.delete(it) }
            open(_state.value.path, false)
        }
    }

    fun copySelected(cut: Boolean) {
        _state.update { it.copy(clipboard = it.selected.toList(), clipboardCut = cut, selected = emptySet()) }
    }

    fun paste() {
        val st = _state.value
        val destDir = File(st.path)
        startTransfer("Paste") { job ->
            st.clipboard.forEach { srcPath ->
                val src = File(srcPath)
                val dest = File(destDir, src.name)
                engine.copyTree(job, src, dest)
                if (st.clipboardCut) storage.delete(srcPath)
            }
        }
        _state.update { it.copy(clipboard = emptyList()) }
    }

    fun compressSelected(format: ArchiveFormat, destName: String, level: Int, password: CharArray?) {
        val files = _state.value.selected.map { File(it) }
        val dest = File(_state.value.path, destName)
        startTransfer("Compress ${dest.name}") { _ ->
            when (format) {
                ArchiveFormat.ZIP -> ArchiveEngine.createZip(files, dest, level, password)
                ArchiveFormat.SEVEN_Z -> ArchiveEngine.create7z(files, dest)
                ArchiveFormat.TAR -> ArchiveEngine.createTar(files, dest)
                ArchiveFormat.TAR_GZ -> ArchiveEngine.createTar(files, dest, gzip = true)
                ArchiveFormat.TAR_XZ -> ArchiveEngine.createTar(files, dest, xz = true)
                else -> error("Create not supported for $format")
            }
        }
        _state.update { it.copy(showCompress = false, selected = emptySet()) }
    }

    fun extract(path: String, destDir: String, password: CharArray? = null) {
        startTransfer("Extract ${File(path).name}") { _ ->
            ArchiveEngine.extractAll(File(path), File(destDir), password)
        }
    }

    fun inspectArchive(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = ArchiveEngine.list(File(path))
                _state.update { it.copy(archiveEntries = entries, archiveFile = path) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun showProperties(node: FsNode) {
        _state.update { it.copy(properties = node, propertiesSize = null) }
        if (node.isDirectory) {
            viewModelScope.launch(Dispatchers.IO) {
                val size = storage.folderSize(node.path)
                _state.update { if (it.properties?.id == node.id) it.copy(propertiesSize = size) else it }
            }
        }
    }

    fun dismissProperties() = _state.update { it.copy(properties = null) }

    fun bookmark(path: String) {
        _state.update {
            val b = if (path in it.bookmarks) it.bookmarks - path else it.bookmarks + path
            it.copy(bookmarks = b)
        }
    }

    fun share(path: String): Intent {
        val ctx = getApplication<Application>()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(path))
        return Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun openWith(path: String): Intent {
        val ctx = getApplication<Application>()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(path))
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun duplicate(path: String) {
        val src = File(path)
        val dest = File(src.parentFile, src.nameWithoutExtension + " copy" + if (src.extension.isEmpty()) "" else ".${src.extension}")
        startTransfer("Duplicate") { job -> engine.copyTree(job, src, dest) }
    }

    fun connectMtp() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dev = mtp.mtpDevices().firstOrNull() ?: error("No MTP device attached")
                if (!mtp.hasPermission(dev)) {
                    mtp.requestPermission(dev)
                    _state.update { it.copy(error = "Grant USB permission, then try again") }
                    return@launch
                }
                val session = mtp.open(dev)
                val ids = mtp.storageIds(session)
                if (ids.isEmpty()) {
                    mtp.close(session)
                    error("MTP device reported no storage")
                }
                val nodes = mtp.listNodes(session, ids[0], 0xFFFFFFFF.toInt())
                mtp.close(session)
                _state.update { it.copy(items = nodes, path = "mtp://${dev.deviceId}", loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setShowCreateFolder(v: Boolean) = _state.update { it.copy(showCreateFolder = v) }
    fun setShowCompress(v: Boolean) = _state.update { it.copy(showCompress = v) }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun closeArchive() = _state.update { it.copy(archiveEntries = null, archiveFile = null) }

    private fun startTransfer(title: String, block: suspend (String) -> Unit) {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(Intent(ctx, TransferService::class.java))
        val job = engine.newJob(title, 1, 0)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block(job)
                engine.finish(job)
                open(_state.value.path, false)
            } catch (e: Exception) {
                engine.finish(job, e.message)
                _state.update { it.copy(error = e.message) }
            }
            collectTransfers()
        }
        collectTransfers()
    }

    private fun collectTransfers() {
        val list = engine.active.values.map { it.value }
        _state.update { it.copy(transfers = list) }
    }
}
