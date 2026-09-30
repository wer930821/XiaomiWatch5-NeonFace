package com.agoose.neonupdater.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agoose.neonupdater.UiState
import com.agoose.neonupdater.adb.AdbEndpoint
import com.agoose.neonupdater.adb.EndpointKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onPickApk: (android.net.Uri) -> Unit,
    onDownloadLatest: () -> Unit,
    onToggleScan: () -> Unit,
    onUseEndpoint: (AdbEndpoint) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPairPortChange: (String) -> Unit,
    onPairCodeChange: (String) -> Unit,
    onShowPairDialog: (Boolean) -> Unit,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onInstall: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPickApk(uri)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("NeonFace 更新器") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionBanner(state)

            StepCard(step = 1, title = "取得最新版錶盤") {
                Button(
                    onClick = onDownloadLatest,
                    enabled = !state.downloadingLatest && !state.busy,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.downloadingLatest) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.downloadingLatest) "下載中…" else "下載最新版 Neon Core Blue")
                }

                state.apk?.let { apk ->
                    Text(apk.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        apk.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${apk.version} · ${apk.sizeText}" + if (apk.isWearApk) " · Wear OS" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                } ?: Text(
                    "也可以自行選擇其他 Wear OS APK。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FilledTonalButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !state.staging,
                    shape = CircleShape,
                ) {
                    if (state.staging) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.apk == null) "選擇其他 APK" else "改選其他 APK")
                }
            }

            StepCard(step = 2, title = "連線手錶") {
                Text(
                    "手錶開啟：設定 → 開發人員選項 → 無線偵錯。手機與手錶必須在同一個 Wi-Fi 網路。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onToggleScan, shape = CircleShape) {
                        Icon(
                            if (state.scanning) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.scanning) "停止掃描" else "掃描網路")
                    }
                    if (state.scanning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }

                if (state.endpoints.isNotEmpty()) {
                    state.endpoints.forEach { endpoint ->
                        AssistChip(
                            onClick = { onUseEndpoint(endpoint) },
                            label = {
                                Text(
                                    "${endpoint.host}:${endpoint.port}  ·  " +
                                        if (endpoint.kind == EndpointKind.PAIRING) "pair" else "connect"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                } else if (state.scanning) {
                    Text(
                        "正在搜尋無線偵錯裝置…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.host,
                        onValueChange = onHostChange,
                        label = { Text("手錶 IP") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = state.port,
                        onValueChange = onPortChange,
                        label = { Text("連接埠") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onShowPairDialog(true) },
                        enabled = !state.busy,
                        shape = CircleShape,
                    ) { Text("配對…") }

                    if (state.connected) {
                        FilledTonalButton(
                            onClick = onDisconnect,
                            enabled = !state.busy,
                            shape = CircleShape,
                        ) { Text("中斷連線") }
                    } else {
                        Button(
                            onClick = onConnect,
                            enabled = state.canConnect,
                            shape = CircleShape,
                        ) { Text("連線") }
                    }
                }

                state.localIp?.let {
                    Text(
                        "This phone is $it — the watch should be on the same subnet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            StepCard(step = 3, title = "安裝到手錶") {
                Button(
                    onClick = onInstall,
                    enabled = state.canInstall,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("安裝最新版錶盤")
                }

                if (state.busy) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.busyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val progress = state.progress
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                    }
                }
            }

            LogCard(state.log)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showPairDialog) {
        PairDialog(
            state = state,
            onHostChange = onHostChange,
            onPairPortChange = onPairPortChange,
            onPairCodeChange = onPairCodeChange,
            onDismiss = { onShowPairDialog(false) },
            onConfirm = onPair,
        )
    }
}

@Composable
private fun ConnectionBanner(state: UiState) {
    val connected = state.connected
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (connected) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (connected) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (connected) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (connected) "已連線" else "尚未連線",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (connected) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.deviceLabel ?: "透過無線偵錯配對並連線",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connected) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepCard(step: Int, title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        step.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("執行紀錄", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                log.takeLast(80).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PairDialog(
    state: UiState,
    onHostChange: (String) -> Unit,
    onPairPortChange: (String) -> Unit,
    onPairCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("與手錶配對") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "在手錶的無線偵錯頁面點「使用配對碼配對裝置」，輸入畫面上的配對連接埠與 6 位數配對碼。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.host,
                    onValueChange = onHostChange,
                    label = { Text("Watch IP") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.pairPort,
                    onValueChange = onPairPortChange,
                    label = { Text("配對連接埠") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = state.pairCode,
                    onValueChange = onPairCodeChange,
                    label = { Text("6 位數配對碼") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                enabled = state.host.isNotBlank() &&
                    state.pairPort.isNotBlank() &&
                    state.pairCode.length == 6,
            ) { Text("配對") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun BoldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}
