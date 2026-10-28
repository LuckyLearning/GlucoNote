package com.gluconote.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
    var showEditTarget by remember { mutableStateOf(false) }

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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    "血糖随手记",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ① 目标血糖范围：决定达标判断，默认锁定，需「开启修改」后在弹窗内编辑
            SectionCard("目标血糖范围", "用于判断偏高 / 偏低与达标率") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("空腹下限", ui.fastingMinText, {}, Modifier.weight(1f), enabled = false)
                    NumberField("空腹上限", ui.fastingMaxText, {}, Modifier.weight(1f), enabled = false)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("餐后下限", ui.postMinText, {}, Modifier.weight(1f), enabled = false)
                    NumberField("餐后上限", ui.postMaxText, {}, Modifier.weight(1f), enabled = false)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showEditTarget = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) { Text("开启修改") }
            }

            if (showEditTarget) {
                // 弹窗内使用本地副本编辑，确认时才写回 vm；取消则不改动原值
                var dFastingMin by remember(showEditTarget) { mutableStateOf(ui.fastingMinText) }
                var dFastingMax by remember(showEditTarget) { mutableStateOf(ui.fastingMaxText) }
                var dPostMin by remember(showEditTarget) { mutableStateOf(ui.postMinText) }
                var dPostMax by remember(showEditTarget) { mutableStateOf(ui.postMaxText) }
                AlertDialog(
                    onDismissRequest = { showEditTarget = false },
                    title = { Text("修改目标血糖范围") },
                    text = {
                        Column {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NumberField("空腹下限", dFastingMin, { dFastingMin = it }, Modifier.weight(1f))
                                NumberField("空腹上限", dFastingMax, { dFastingMax = it }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NumberField("餐后下限", dPostMin, { dPostMin = it }, Modifier.weight(1f))
                                NumberField("餐后上限", dPostMax, { dPostMax = it }, Modifier.weight(1f))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                vm.setFastingMin(dFastingMin)
                                vm.setFastingMax(dFastingMax)
                                vm.setPostMin(dPostMin)
                                vm.setPostMax(dPostMax)
                                vm.save(
                                    onSaved = {
                                        showEditTarget = false
                                        scope.launch { snackbar.showSnackbar("已保存") }
                                    },
                                    onError = { msg ->
                                        scope.launch { snackbar.showSnackbar(msg) }
                                    }
                                )
                            }
                        ) { Text("确认") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditTarget = false }) { Text("取消") }
                    }
                )
            }

            // ② 外观：样式偏好
            SectionCard("外观", "深色模式下配色、图表与状态栏图标会一起切换") {
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

            // 数据备份：低频且不可逆（导入会写入数据），收纳到页面最末的折叠区。
            // 默认收起，点标题行才展开导出 / 导入 —— 滚动时不会误触；
            // 展开状态刻意不做持久化，每次进「我的」都是收起态。
            var backupOpen by rememberSaveable { mutableStateOf(false) }
            CollapsedSection(
                title = "数据备份",
                subtitle = "CSV 可发给医生，也可用于换机迁移",
                expanded = backupOpen,
                onToggle = { backupOpen = !backupOpen }
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val weak = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { exportLauncher.launch("gluconote_export.csv") },
                        modifier = Modifier.weight(1f),
                        colors = weak
                    ) { Text("导出 CSV", style = MaterialTheme.typography.bodyMedium) }
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("text/csv", "*/*")) },
                        modifier = Modifier.weight(1f),
                        colors = weak
                    ) { Text("导入备份", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 小节卡片：统一外观（圆角、零阴影、浅底 + 可选说明） */
@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * 可收纳的弱化区块：默认收起，点标题行展开内容。
 *
 * 用于低频、不希望被顺手点到的功能（如数据备份）。折叠态只占一行文字高度，
 * 既不占主视线，也让"误触"在物理上不可能发生 —— 必须先展开才看得到操作按钮。
 * 折叠态用页面底色（不上卡片），箭头随展开状态 180° 旋转，动画 200ms。
 */
@Composable
private fun CollapsedSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "chevron"
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起$title" else "展开$title",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp).rotate(chevron)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(
                animationSpec = tween(160, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(100))
        ) {
            Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                content()
            }
        }
    }
}

/** 诊断卡片：设备信息 + 崩溃日志查看/复制/清除（配合 CrashGuard） */
@Composable
private fun DiagnosticsCard() {
    val context = LocalContext.current
    var crashText by remember {
        mutableStateOf(
            CrashGuard.lastCrashFile(context.applicationContext as android.app.Application)?.readText()
        )
    }
    var showCrash by remember { mutableStateOf(false) }

    SectionCard("诊断") {
        Text(
            "设备：${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
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
                TextButton(onClick = { showCrash = true }) {
                    Text("查看崩溃日志", color = MaterialTheme.colorScheme.error)
                }
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

    SectionCard("调试工具（仅 Debug 包）", "小数数据约 14 天演示；大数据约 2.5 年、数千条") {
        Text(
            "用于验证记录分页、统计与图表在真实数据量下的表现。",
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
            ) { Text("加载调试数据") }
            Button(
                onClick = { confirmClear = true },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                enabled = !loadingLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text("清空数据") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { confirmLarge = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            enabled = !loadingLarge
        ) {
            if (loadingLarge) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
                Text("正在写入大数据…")
            } else {
                Text("加载近几年大数据")
            }
        }
    }

    if (confirmLoad) {
        AlertDialog(
            onDismissRequest = { confirmLoad = false },
            title = { Text("加载调试数据？") },
            text = { Text("将清空现有记录并写入约 14 天演示数据。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLoad = false
                    scope.launch {
                        val n = vm.loadDebugData()
                        snackbar.showSnackbar(
                            if (n >= 0) "已加载调试数据：$n 条" else "仅 Debug 包可用"
                        )
                    }
                }) { Text("加载") }
            },
            dismissButton = { TextButton(onClick = { confirmLoad = false }) { Text("取消") } }
        )
    }

    if (confirmLarge) {
        AlertDialog(
            onDismissRequest = { if (!loadingLarge) confirmLarge = false },
            title = { Text("加载近几年大数据？") },
            text = {
                Text(
                    "将清空现有记录并写入约 2.5 年、数千条血糖记录（用于分页/统计压测），可能需要几秒钟。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLarge = false
                    loadingLarge = true
                    scope.launch {
                        val n = vm.loadLargeDebugData()
                        loadingLarge = false
                        snackbar.showSnackbar(
                            if (n >= 0) "已加载大数据：$n 条" else "仅 Debug 包可用"
                        )
                    }
                }) { Text("加载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLarge = false }, enabled = !loadingLarge) { Text("取消") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部记录？") },
            text = { Text("此操作不可恢复；目标范围不会被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        val ok = vm.clearAllData()
                        snackbar.showSnackbar(if (ok) "已清空" else "仅 Debug 包可用")
                    }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
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
        modifier = modifier,
        singleLine = true,
        enabled = enabled,
        shape = MaterialTheme.shapes.small
    )
}
