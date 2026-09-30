package com.agoose.neonupdater

import android.app.Application
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agoose.neonupdater.adb.AdbDiscovery
import com.agoose.neonupdater.adb.AdbEndpoint
import com.agoose.neonupdater.adb.AdbTransfer
import com.agoose.neonupdater.adb.EndpointKind
import com.agoose.neonupdater.adb.WatchAdb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val apk: StagedApk? = null,
    val staging: Boolean = false,
    val downloadingLatest: Boolean = false,
    val scanning: Boolean = false,
    val endpoints: List<AdbEndpoint> = emptyList(),
    val host: String = "",
    val port: String = "",
    val pairPort: String = "",
    val pairCode: String = "",
    val showPairDialog: Boolean = false,
    val connected: Boolean = false,
    val deviceLabel: String? = null,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val progress: Float? = null,
    val log: List<String> = emptyList(),
    val localIp: String? = null,
    val updaterChecking: Boolean = false,
    val updaterAvailable: Boolean = false,
    val updaterDownloading: Boolean = false,
    val updaterLatestVersion: String? = null,
    val updaterLatestCode: Int? = null,
) {
    val canConnect: Boolean get() = host.isNotBlank() && port.toIntOrNull() != null && !busy
    val canInstall: Boolean get() = connected && apk != null && !busy
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val discovery = AdbDiscovery(application)

    private val _state = MutableStateFlow(
        UiState(
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            port = prefs.getString(KEY_PORT, "").orEmpty(),
            localIp = AdbDiscovery.localIpv4(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        log("Ready. Turn on Wireless debugging on the watch to begin.")
        checkUpdaterUpdate(silent = true)
    }

    // ---------------------------------------------------------------- self update

    fun checkUpdaterUpdate(silent: Boolean = false) {
        if (_state.value.updaterChecking || _state.value.updaterDownloading) return
        viewModelScope.launch {
            _state.update { it.copy(updaterChecking = true) }
            if (!silent) log("正在檢查 NeonFace 更新器版本…")
            try {
                val info = withContext(Dispatchers.IO) {
                    val url = URL(UPDATER_INFO_URL + "?t=" + System.currentTimeMillis())
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        requestMethod = "GET"
                        setRequestProperty("Cache-Control", "no-cache")
                    }
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IOException("HTTP " + connection.responseCode)
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    val json = JSONObject(body)
                    Pair(json.getInt("updaterVersionCode"), json.optString("updaterVersionName"))
                }
                val currentCode = BuildConfig.VERSION_CODE
                val available = info.first > currentCode
                _state.update {
                    it.copy(
                        updaterAvailable = available,
                        updaterLatestCode = info.first,
                        updaterLatestVersion = info.second.ifBlank { null },
                    )
                }
                if (available) {
                    UpdateNotifier.notifyUpdaterUpdate(getApplication(), info.second.ifBlank { null })
                }
                if (!silent) {
                    log(
                        if (available) "發現更新器新版 ${info.second}（${info.first}），目前版本 ${BuildConfig.VERSION_NAME}（$currentCode）。"
                        else "更新器已是最新版 ${BuildConfig.VERSION_NAME}（$currentCode）。"
                    )
                }
            } catch (t: Throwable) {
                if (!silent) log("檢查更新器失敗：" + (t.message ?: t.javaClass.simpleName))
            } finally {
                _state.update { it.copy(updaterChecking = false) }
            }
        }
    }

    fun updateUpdater() {
        if (_state.value.updaterDownloading || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(updaterDownloading = true) }
            log("正在下載新版 NeonFace 更新器…")
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().filesDir, "updates").apply { mkdirs() }
                    val target = File(dir, "NeonFace-Updater.apk")
                    val connection = (URL(UPDATER_APK_URL).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 60_000
                        requestMethod = "GET"
                    }
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IOException("下載失敗：HTTP " + connection.responseCode)
                    }
                    connection.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    connection.disconnect()
                    target
                }
                launchUpdaterInstaller(apkFile)
            } catch (t: Throwable) {
                log("更新更新器失敗：" + (t.message ?: t.javaClass.simpleName))
            } finally {
                _state.update { it.copy(updaterDownloading = false) }
            }
        }
    }

    private fun launchUpdaterInstaller(apkFile: File) {
        val app = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            log("請允許「安裝未知應用程式」，允許後回到更新器再按一次更新。")
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + app.packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            return
        }

        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        log("已下載新版，正在開啟 Android 安裝畫面…")
        app.startActivity(intent)
    }

    // ---------------------------------------------------------------- APK

    fun onApkPicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(staging = true) }
            try {
                val staged = withContext(Dispatchers.IO) { ApkStager.stage(getApplication(), uri) }
                _state.update { it.copy(apk = staged) }
                log("Selected ${staged.label} · ${staged.packageName} · ${staged.sizeText}")
                if (!staged.isWearApk) {
                    log("Note: this APK does not declare android.hardware.type.watch. It may still install.")
                }
            } catch (t: Throwable) {
                log("Could not read that APK: ${t.message}")
            } finally {
                _state.update { it.copy(staging = false) }
            }
        }
    }

    fun downloadLatest() {
        if (_state.value.downloadingLatest || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(downloadingLatest = true) }
            log("正在下載最新版 Neon Core Blue…")
            try {
                val staged = withContext(Dispatchers.IO) {
                    val url = URL(LATEST_APK_URL)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 60_000
                        requestMethod = "GET"
                    }
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IOException("下載失敗：HTTP " + connection.responseCode)
                    }
                    val target = File(getApplication<Application>().cacheDir, "NeonCoreBlue-watch.apk")
                    connection.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    connection.disconnect()
                    ApkStager.stageFile(getApplication(), target, "NeonCoreBlue-watch.apk")
                }
                _state.update { it.copy(apk = staged) }
                log("已下載 " + staged.label + " · " + staged.version + " · " + staged.sizeText)
            } catch (t: Throwable) {
                log("下載最新版失敗：" + t.message)
            } finally {
                _state.update { it.copy(downloadingLatest = false) }
            }
        }
    }


    fun downloadCyber() {
        if (_state.value.downloadingLatest || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(downloadingLatest = true) }
            log("正在下載最新版 Cyber Neon City…")
            try {
                val staged = withContext(Dispatchers.IO) {
                    val url = URL(CYBER_APK_URL)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 60_000
                        requestMethod = "GET"
                    }
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IOException("下載失敗：HTTP " + connection.responseCode)
                    }
                    val target = File(getApplication<Application>().cacheDir, "CyberNeonCity-watch.apk")
                    connection.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    connection.disconnect()
                    ApkStager.stageFile(getApplication(), target, "CyberNeonCity-watch.apk")
                }
                _state.update { it.copy(apk = staged) }
                log("已下載 " + staged.label + " · " + staged.version + " · " + staged.sizeText)
            } catch (t: Throwable) {
                log("下載 Cyber Neon City 失敗：" + t.message)
            } finally {
                _state.update { it.copy(downloadingLatest = false) }
            }
        }
    }

    // ---------------------------------------------------------------- discovery

    fun toggleScan() {
        if (_state.value.scanning) stopScan() else startScan()
    }

    private fun startScan() {
        if (scanJob != null) return
        _state.update { it.copy(scanning = true, endpoints = emptyList()) }
        log("Scanning the Wi-Fi network for ADB devices…")
        scanJob = viewModelScope.launch {
            discovery.endpoints().collect { endpoints ->
                val previous = _state.value.endpoints.map { it.id }.toSet()
                endpoints.filter { it.id !in previous }.forEach {
                    log("Found ${it.kind.label} endpoint at ${it.host}:${it.port}")
                }
                _state.update { it.copy(endpoints = endpoints) }
            }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanning = false) }
    }

    fun useEndpoint(endpoint: AdbEndpoint) {
        when (endpoint.kind) {
            EndpointKind.CONNECT -> _state.update {
                it.copy(host = endpoint.host, port = endpoint.port.toString())
            }
            EndpointKind.PAIRING -> _state.update {
                it.copy(
                    host = endpoint.host,
                    pairPort = endpoint.port.toString(),
                    showPairDialog = true,
                )
            }
        }
    }

    // ---------------------------------------------------------------- form fields

    fun setHost(value: String) = _state.update { it.copy(host = value.trim()) }
    fun setPort(value: String) = _state.update { it.copy(port = value.filter(Char::isDigit)) }
    fun setPairPort(value: String) = _state.update { it.copy(pairPort = value.filter(Char::isDigit)) }
    fun setPairCode(value: String) = _state.update { it.copy(pairCode = value.filter(Char::isDigit).take(6)) }
    fun showPairDialog(show: Boolean) {
        _state.update { current ->
            if (!show) return@update current.copy(showPairDialog = false)
            val pairing = current.endpoints.firstOrNull {
                it.kind == EndpointKind.PAIRING && (current.host.isBlank() || it.host == current.host)
            } ?: current.endpoints.firstOrNull { it.kind == EndpointKind.PAIRING }
            current.copy(
                host = pairing?.host ?: current.host,
                pairPort = pairing?.port?.toString() ?: current.pairPort,
                showPairDialog = true,
            )
        }
    }

    // ---------------------------------------------------------------- pair / connect

    fun pair() {
        val current = _state.value
        val discoveredPairing = current.endpoints.firstOrNull {
            it.kind == EndpointKind.PAIRING && it.host == current.host
        }
        val host = discoveredPairing?.host ?: current.host
        val port = discoveredPairing?.port ?: current.pairPort.toIntOrNull()
        val code = current.pairCode
        if (host.isBlank() || port == null || code.length != 6) {
            log("Pairing needs an address, the pairing port and the 6-digit code.")
            return
        }
        _state.update { it.copy(showPairDialog = false) }
        runAdb("Pairing…") { manager ->
            log("正在使用配對連接埠 $host:$port 配對…")
            manager.pair(host, port, code)
            log("Paired. Now connect using the port shown on the Wireless debugging screen.")
            _state.update { it.copy(pairCode = "") }
        }
    }

    fun connect() {
        val current = _state.value
        val host = current.host
        val port = current.port.toIntOrNull() ?: return
        prefs.edit().putString(KEY_HOST, host).putString(KEY_PORT, port.toString()).apply()

        runAdb("Connecting…") { manager ->
            log("Connecting to $host:$port…")
            if (manager.isConnected) manager.disconnect()
            if (!manager.connect(host, port)) throw IOException("The device refused the connection")
            val model = AdbTransfer.shell(manager, "getprop ro.product.model").trim()
            val release = AdbTransfer.shell(manager, "getprop ro.build.version.release").trim()
            val label = listOf(model, release.takeIf { it.isNotBlank() }?.let { "Android $it" })
                .filterNot { it.isNullOrBlank() }
                .joinToString(" · ")
                .ifBlank { "$host:$port" }
            _state.update { it.copy(connected = true, deviceLabel = label) }
            log("Connected to $label")
        }
    }

    fun disconnect() {
        runAdb("Disconnecting…") { manager ->
            manager.disconnect()
            _state.update { it.copy(connected = false, deviceLabel = null) }
            log("Disconnected.")
        }
    }

    // ---------------------------------------------------------------- install

    fun install() {
        val apk = _state.value.apk ?: return
        val remotePath = "/data/local/tmp/watchpush-${System.currentTimeMillis()}.apk"

        runAdb("Installing…") { manager ->
            log("Pushing ${apk.fileName} (${apk.sizeText})…")
            _state.update { it.copy(progress = 0f) }
            AdbTransfer.push(manager, File(apk.path), remotePath) { sent, total ->
                if (total > 0) _state.update { it.copy(progress = (sent.toFloat() / total)) }
            }
            _state.update { it.copy(progress = null, busyLabel = "Installing on watch…") }
            log("Transfer complete. Running pm install on the watch…")

            val output = AdbTransfer.shell(manager, "pm install -r -t -d $remotePath").trim()
            runCatching { AdbTransfer.shell(manager, "rm -f $remotePath") }

            if (output.contains("Success", ignoreCase = true)) {
                log("✓ ${apk.label} installed on the watch.")
            } else {
                throw IOException(output.ifBlank { "pm install returned no output" })
            }
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun runAdb(label: String, block: (WatchAdb) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, busyLabel = label) }
            try {
                runInterruptible(Dispatchers.IO) {
                    block(WatchAdb.get(getApplication()))
                }
            } catch (t: Throwable) {
                log("✗ ${t.message ?: t.javaClass.simpleName}")
                if (t is IOException || t is IllegalStateException) {
                    _state.update { it.copy(connected = false, deviceLabel = null) }
                }
            } finally {
                _state.update { it.copy(busy = false, busyLabel = "", progress = null) }
            }
        }
    }

    private fun log(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _state.update { it.copy(log = (it.log + "$stamp  $line").takeLast(200)) }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }

    companion object {
        private const val PREFS = "watchpush"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val LATEST_APK_URL = "https://github.com/wer930821/XiaomiWatch5-NeonFace/releases/download/latest/NeonCoreBlue-watch.apk"
        private const val CYBER_APK_URL = "https://github.com/wer930821/XiaomiWatch5-NeonFace/releases/download/latest/CyberNeonCity-watch.apk"
        private const val UPDATER_APK_URL = "https://github.com/wer930821/XiaomiWatch5-NeonFace/releases/download/latest/NeonFace-Updater.apk"
        private const val UPDATER_INFO_URL = "https://github.com/wer930821/XiaomiWatch5-NeonFace/releases/download/latest/update-info.json"
    }
}

val EndpointKind.label: String
    get() = when (this) {
        EndpointKind.PAIRING -> "pairing"
        EndpointKind.CONNECT -> "connect"
    }
