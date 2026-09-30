package com.agoose.neonupdater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agoose.neonupdater.ui.MainScreen
import com.agoose.neonupdater.ui.WatchPushTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WatchPushTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onPickApk = vm::onApkPicked,
                    onDownloadLatest = vm::downloadLatest,
                    onToggleScan = vm::toggleScan,
                    onUseEndpoint = vm::useEndpoint,
                    onHostChange = vm::setHost,
                    onPortChange = vm::setPort,
                    onPairPortChange = vm::setPairPort,
                    onPairCodeChange = vm::setPairCode,
                    onShowPairDialog = vm::showPairDialog,
                    onPair = vm::pair,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect,
                    onInstall = vm::install,
                )
            }
        }
    }
}
