package com.agoose.neonupdater

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agoose.neonupdater.ui.MainScreen
import com.agoose.neonupdater.ui.WatchPushTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val updaterViewModel: MainViewModel by viewModels()
    private var foregroundUpdateCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        UpdateNotifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        }

        val updateWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "neonface-update-check",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateWork,
        )
        setContent {
            WatchPushTheme {
                val vm = updaterViewModel
                val state by vm.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onCheckUpdater = { vm.checkUpdaterUpdate(silent = false) },
                    onUpdateUpdater = vm::updateUpdater,
                    onDismissUpdater = vm::dismissUpdaterDialog,
                    onPickApk = vm::onApkPicked,
                    onDownloadLatest = vm::downloadLatest,
                    onDownloadCyber = vm::downloadCyber,
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

    override fun onResume() {
        super.onResume()
        updaterViewModel.checkUpdaterUpdate(silent = true)
    }

    override fun onStart() {
        super.onStart()
        foregroundUpdateCheckJob?.cancel()
        foregroundUpdateCheckJob = lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                updaterViewModel.checkUpdaterUpdate(silent = true)
            }
        }
    }

    override fun onStop() {
        foregroundUpdateCheckJob?.cancel()
        foregroundUpdateCheckJob = null
        super.onStop()
    }
}
