package com.sarchiver.app

import android.app.Application

class SarchiverApp : Application() {
    val transferEngine by lazy { data.transfer.TransferEngine() }
}
