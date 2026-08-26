package com.sarchiver.app

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sarchiver.app.ui.BrowserViewModel
import com.sarchiver.app.ui.screens.SarchiverAppUi
import com.sarchiver.app.ui.theme.SarchiverTheme

class MainActivity : ComponentActivity() {
    private val vm: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestAllFiles()
        setContent {
            val st by vm.state.collectAsState()
            SarchiverTheme(st.theme) {
                SarchiverAppUi(vm)
            }
        }
    }

    private fun maybeRequestAllFiles() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }
}
