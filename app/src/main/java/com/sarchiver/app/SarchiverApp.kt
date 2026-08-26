package com.sarchiver.app

import android.app.Application
import com.sarchiver.app.data.transfer.TransferEngine

class SarchiverApp : Application() {
    val transferEngine by lazy { TransferEngine() }
}
