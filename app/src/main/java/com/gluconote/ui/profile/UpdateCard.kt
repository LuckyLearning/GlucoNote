package com.gluconote.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gluconote.update.UpdateCheckResult
import com.gluconote.update.UpdateChecker
import com.gluconote.update.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

/** 「检查更新」卡片的界面状态 */
private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Message(val text: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val received: Long, val total: Long) : UpdateUiState
    data class Downloaded(val info: UpdateInfo, val apk: File) : UpdateUiState
}

/**
 * 「我的」页的更新卡片：检查更新 → 弹窗确认 → 下载（带进度）→ 拉起安装器。
 */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("版本更新", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            Text(
                "当前版本 ${UpdateChecker.currentVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))

            when (val s = state) {
                is UpdateUiState.Checking -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在检查更新…", style = MaterialTheme.typography.bodySmall)
                }
                is UpdateUiState.Downloading -> {
                    val fraction = if (s.total > 0) s.received.toFloat() / s.total else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "正在下载 ${UpdateChecker.formatSize(s.received)}" +
                            if (s.total > 0) " / ${UpdateChecker.formatSize(s.total)}" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> Unit
            }

            when (val s = state) {
                is UpdateUiState.Message -> Text(
                    s.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                is UpdateUiState.Available -> Text(
                    "发现新版本 ${s.info.version}" +
                        (UpdateChecker.formatSize(s.info.apkSizeBytes)
                            .takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
                is UpdateUiState.Downloaded -> Text(
                    "已下载 ${s.info.version}，可以安装",
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> Unit
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            state = UpdateUiState.Checking
                            state = when (val r = UpdateChecker.checkLatest()) {
                                is UpdateCheckResult.Available -> UpdateUiState.Available(r.info)
                                is UpdateCheckResult.UpToDate ->
                                    UpdateUiState.Message("已是最新版本（${r.latestVersion}）")
                                is UpdateCheckResult.Failed ->
                                    UpdateUiState.Message("${r.reason}\n可到浏览器打开发布页手动下载")
                            }
                        }
                    },
                    enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading
                ) { Text("检查更新") }

                if (state is UpdateUiState.Downloaded) {
                    OutlinedButton(onClick = {
                        val s = state as UpdateUiState.Downloaded
                        if (UpdateChecker.canInstall(context)) {
                            UpdateChecker.install(context, s.apk)
                        } else {
                            UpdateChecker.unknownSourcesIntent(context)?.let {
                                runCatching { context.startActivity(it) }
                            }
                        }
                    }) { Text("安装") }
                }
            }
        }
    }

    // 新版本确认弹窗：展示更新说明，确认后开始下载
    val pending = (state as? UpdateUiState.Available)?.info
    pending?.let { info ->
        AlertDialog(
            onDismissRequest = { state = UpdateUiState.Idle },
            title = { Text("发现新版本 ${info.version}") },
            text = {
                Column {
                    if (info.notes.isNotBlank()) {
                        Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("暂无更新说明", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state = UpdateUiState.Downloading(0, info.apkSizeBytes)
                    scope.launch {
                        try {
                            val apk = UpdateChecker.download(context, info) { received, total ->
                                state = UpdateUiState.Downloading(received, total)
                            }
                            state = UpdateUiState.Downloaded(info, apk)
                        } catch (t: Throwable) {
                            state = UpdateUiState.Message(
                                "下载失败：${t.message ?: "网络异常"}\n可在浏览器打开发布页手动下载"
                            )
                        }
                    }
                }) { Text("下载并安装") }
            },
            dismissButton = { TextButton(onClick = { state = UpdateUiState.Idle }) { Text("稍后再说") } }
        )
    }
}
