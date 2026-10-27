package com.gluconote.ui.profile

import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context

import android.os.Build

import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.Button

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.FilterChip

import androidx.compose.material3.FilterChipDefaults

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Scaffold

import androidx.compose.material3.SnackbarHost

import androidx.compose.material3.SnackbarHostState

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

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.KeyboardType

import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavController

import com.gluconote.BuildConfig

import com.gluconote.data.model.ThemeMode

import com.gluconote.util.CrashGuard

import kotlinx.coroutines.launch

import java.io.InputStreamReader

@Composable

fun ProfileScreen(navController: NavController) {

    val vm = viewModel<ProfileViewModel>()

    val ui by vm.ui.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(

        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")

    ) { uri ->

        uri ?: return@rememberLauncherForActivityResult

        scope.launch {

            val text = vm.exportText()

            context.contentResolver.openOutputStream(uri)?.use { os ->

                os.write(text.toByteArray(Charsets.UTF_8))

            }

            snackbar.showSnackbar("已生成 CSV，可分享或发送给医生")

        }

    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(

        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()

    ) { uri ->

        uri ?: return@rememberLauncherForActivityResult

        scope.launch {

            try {

                val text = context.contentResolver.openInputStream(uri)

                    ?.use { InputStreamReader(it).readText() } ?: ""

                val n = vm.importText(text)

                snackbar.showSnackbar("导入完成：新增 $n 条")

            } catch (e: Exception) {

                snackbar.showSnackbar("导入失败，请检查文件格式")

            }

        }

    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->

        Column(

            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),

            verticalArrangement = Arrangement.spacedBy(16.dp)

        ) {

            Text("我的", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))

            SectionCard("外观") {
                Text(
                    "主题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.values().forEach { mode ->
                        FilterChip(
                            selected = ui.settings.themeMode == mode.key,
                            onClick = { vm.setThemeMode(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "深色模式下的配色、图表与状态栏图标会一起切换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            SectionCard("目标血糖范围") {

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    NumberField("空腹下限", ui.fastingMinText, vm::setFastingMin, Modifier.weight(1f))

                    NumberField("空腹上限", ui.fastingMaxText, vm::setFastingMax, Modifier.weight(1f))

                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    NumberField("餐后下限", ui.postMinText, vm::setPostMin, Modifier.weight(1f))

                    NumberField("餐后上限", ui.postMaxText, vm::setPostMax, Modifier.weight(1f))

                }

                Spacer(Modifier.height(6.dp))

                Button(

                    onClick = { vm.save(onSaved = { scope.launch { snackbar.showSnackbar("已保存") } }, onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } }) },

                    modifier = Modifier.fillMaxWidth(),

                    shape = MaterialTheme.shapes.small

                ) { Text("保存目标范围") }

            }

            SectionCard("数据备份") {

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    Button(

                        onClick = { exportLauncher.launch("gluconote_export.csv") },

                        modifier = Modifier.weight(1f),

                        shape = MaterialTheme.shapes.small

                    ) { Text("导出 CSV") }

                    Button(

                        onClick = { importLauncher.launch(arrayOf("text/csv", "*/*")) },

                        modifier = Modifier.weight(1f),

                        shape = MaterialTheme.shapes.small

                    ) { Text("导入备份") }

                }

            }

            if (BuildConfig.DEBUG) {

                DebugToolsCard(vm = vm, snackbar = snackbar)

            }

            UpdateCard()

            DiagnosticsCard()

            SectionCard("关于与免责声明") {

                Text(

                    "本应用数据仅供个人日常管理参考，不构成医疗建议，不能替代专业医生的诊断与治疗。如有不适请及时就医。",

                    style = MaterialTheme.typography.bodyMedium,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

            }

            Spacer(Modifier.height(8.dp))

        }

    }

}

/** 小节卡片：统一外观（圆角、零阴影、浅底） */

@Composable

private fun SectionCard(title: String, content: @Composable () -> Unit) {

    Card(

        Modifier.fillMaxWidth(),

        shape = MaterialTheme.shapes.large,

        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),

        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    ) {

        Column(Modifier.fillMaxWidth().padding(16.dp)) {

            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.height(12.dp))

            content()

        }

    }

}

/** 诊断卡片：设备信息 + 崩溃日志查看/复制/清除（配合 CrashGuard） */

@Composable

private fun DiagnosticsCard() {

    val context = LocalContext.current

    var crashText by remember { mutableStateOf(CrashGuard.lastCrashFile(context.applicationContext as android.app.Application)?.readText()) }

    var showCrash by remember { mutableStateOf(false) }

    SectionCard("诊断") {

        Text(

            "设备：${Build.MANUFACTURER} ${Build.MODEL}\n" +

                "系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",

            style = MaterialTheme.typography.bodySmall,

            color = MaterialTheme.colorScheme.onSurfaceVariant

        )

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            TextButton(

                onClick = {

                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

                    cm.setPrimaryClip(

                        ClipData.newPlainText(

                            "gluconote-diagnostics",

                            "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}(API ${Build.VERSION.SDK_INT})"

                        )

                    )

                    Toast.makeText(context, "设备信息已复制", Toast.LENGTH_SHORT).show()

                }

            ) { Text("复制设备信息") }

            if (crashText != null) {

                TextButton(onClick = { showCrash = true }) { Text("查看崩溃日志", color = MaterialTheme.colorScheme.error) }

                TextButton(onClick = {

                    CrashGuard.clear(context.applicationContext as android.app.Application)

                    crashText = null

                    Toast.makeText(context, "已清除", Toast.LENGTH_SHORT).show()

                }) { Text("清除") }

            }

        }

    }

    if (showCrash && crashText != null) {

        AlertDialog(

            onDismissRequest = { showCrash = false },

            title = { Text("最近一次崩溃") },

            text = {

                Column(Modifier.verticalScroll(rememberScrollState())) {

                    Text(crashText!!, style = MaterialTheme.typography.bodySmall)

                }

            },

            confirmButton = { TextButton(onClick = { showCrash = false }) { Text("关闭") } }

        )

    }

}

/** Debug-only tools: small demo / multi-year bulk / clear. Gated by BuildConfig.DEBUG. */

@Composable

private fun DebugToolsCard(vm: ProfileViewModel, snackbar: SnackbarHostState) {

    val scope = rememberCoroutineScope()

    var confirmLoad by remember { mutableStateOf(false) }

    var confirmLarge by remember { mutableStateOf(false) }

    var confirmClear by remember { mutableStateOf(false) }

    var loadingLarge by remember { mutableStateOf(false) }

    SectionCard("\u8c03\u8bd5\u5de5\u5177\uff08\u4ec5 Debug \u5305\uff09") {

        Text(

            "\u5c0f\u6570\u636e\uff1a\u7ea6 14 \u5929\u6f14\u793a\u3002\u5927\u6570\u636e\uff1a\u7ea6 2.5 \u5e74\u3001\u6570\u5343\u6761\uff0c\u7528\u4e8e\u9a8c\u8bc1\u8bb0\u5f55\u5206\u9875\u4e0e\u7edf\u8ba1\u3002",

            style = MaterialTheme.typography.bodySmall,

            color = MaterialTheme.colorScheme.onSurfaceVariant

        )

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            Button(

                onClick = { confirmLoad = true },

                modifier = Modifier.weight(1f),

                shape = MaterialTheme.shapes.small,

                enabled = !loadingLarge

            ) { Text("\u52a0\u8f7d\u8c03\u8bd5\u6570\u636e") }

            Button(

                onClick = { confirmClear = true },

                modifier = Modifier.weight(1f),

                shape = MaterialTheme.shapes.small,

                enabled = !loadingLarge,

                colors = androidx.compose.material3.ButtonDefaults.buttonColors(

                    containerColor = MaterialTheme.colorScheme.errorContainer,

                    contentColor = MaterialTheme.colorScheme.onErrorContainer

                )

            ) { Text("\u6e05\u7a7a\u6570\u636e") }

        }

        Spacer(Modifier.height(8.dp))

        Button(

            onClick = { confirmLarge = true },

            modifier = Modifier.fillMaxWidth(),

            shape = MaterialTheme.shapes.small,

            enabled = !loadingLarge

        ) {

            if (loadingLarge) {

                androidx.compose.material3.CircularProgressIndicator(

                    modifier = Modifier.size(18.dp),

                    strokeWidth = 2.dp,

                    color = MaterialTheme.colorScheme.onPrimary

                )

                Spacer(Modifier.size(8.dp))

                Text("\u6b63\u5728\u5199\u5165\u5927\u6570\u636e\u2026")

            } else {

                Text("\u52a0\u8f7d\u8fd1\u51e0\u5e74\u5927\u6570\u636e")

            }

        }

    }

    if (confirmLoad) {

        AlertDialog(

            onDismissRequest = { confirmLoad = false },

            title = { Text("\u52a0\u8f7d\u8c03\u8bd5\u6570\u636e\uff1f") },

            text = { Text("\u5c06\u6e05\u7a7a\u73b0\u6709\u8bb0\u5f55\u5e76\u5199\u5165\u7ea6 14 \u5929\u6f14\u793a\u6570\u636e\u3002") },

            confirmButton = {

                TextButton(onClick = {

                    confirmLoad = false

                    scope.launch {

                        val n = vm.loadDebugData()

                        snackbar.showSnackbar(if (n >= 0) "\u5df2\u52a0\u8f7d\u8c03\u8bd5\u6570\u636e\uff1a$n \u6761" else "\u4ec5 Debug \u5305\u53ef\u7528")

                    }

                }) { Text("\u52a0\u8f7d") }

            },

            dismissButton = { TextButton(onClick = { confirmLoad = false }) { Text("\u53d6\u6d88") } }

        )

    }

    if (confirmLarge) {

        AlertDialog(

            onDismissRequest = { if (!loadingLarge) confirmLarge = false },

            title = { Text("\u52a0\u8f7d\u8fd1\u51e0\u5e74\u5927\u6570\u636e\uff1f") },

            text = { Text("\u5c06\u6e05\u7a7a\u73b0\u6709\u8bb0\u5f55\u5e76\u5199\u5165\u7ea6 2.5 \u5e74\u3001\u6570\u5343\u6761\u8840\u7cd6\u8bb0\u5f55\uff08\u7528\u4e8e\u5206\u9875/\u7edf\u8ba1\u538b\u6d4b\uff09\uff0c\u53ef\u80fd\u9700\u8981\u51e0\u79d2\u949f\u3002") },

            confirmButton = {

                TextButton(onClick = {

                    confirmLarge = false

                    loadingLarge = true

                    scope.launch {

                        val n = vm.loadLargeDebugData()

                        loadingLarge = false

                        snackbar.showSnackbar(if (n >= 0) "\u5df2\u52a0\u8f7d\u5927\u6570\u636e\uff1a$n \u6761" else "\u4ec5 Debug \u5305\u53ef\u7528")

                    }

                }) { Text("\u52a0\u8f7d") }

            },

            dismissButton = { TextButton(onClick = { confirmLarge = false }, enabled = !loadingLarge) { Text("\u53d6\u6d88") } }

        )

    }

    if (confirmClear) {

        AlertDialog(

            onDismissRequest = { confirmClear = false },

            title = { Text("\u6e05\u7a7a\u5168\u90e8\u8bb0\u5f55\uff1f") },

            text = { Text("\u6b64\u64cd\u4f5c\u4e0d\u53ef\u6062\u590d\uff1b\u76ee\u6807\u8303\u56f4\u4e0d\u4f1a\u88ab\u6e05\u9664\u3002") },

            confirmButton = {

                TextButton(onClick = {

                    confirmClear = false

                    scope.launch {

                        val ok = vm.clearAllData()

                        snackbar.showSnackbar(if (ok) "\u5df2\u6e05\u7a7a" else "\u4ec5 Debug \u5305\u53ef\u7528")

                    }

                }) { Text("\u6e05\u7a7a") }

            },

            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("\u53d6\u6d88") } }

        )

    }

}

@Composable

private fun NumberField(

    label: String,

    value: String,

    onValue: (String) -> Unit,

    modifier: Modifier = Modifier

) {

    OutlinedTextField(

        value = value,

        onValueChange = { raw ->

            // Digits + at most one decimal place; reject garbage like 7.07.7

            if (raw.isEmpty() || raw.matches(Regex("^\\d{0,2}(\\.\\d{0,1})?$"))) {

                onValue(raw)

            }

        },

        label = { Text(label) },

        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),

        modifier = modifier.padding(vertical = 2.dp),

        singleLine = true,

        shape = MaterialTheme.shapes.small

    )

}

