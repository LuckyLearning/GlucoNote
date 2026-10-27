@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gluconote.ui.records

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import com.gluconote.ui.components.PeriodChips
import com.gluconote.ui.components.WheelColumn
import com.gluconote.ui.components.WheelTimeDialog
import com.gluconote.ui.components.pad2
import com.gluconote.ui.theme.Motion
import com.gluconote.ui.theme.statusColor
import com.gluconote.ui.theme.toneColor
import com.gluconote.util.formatDate
import com.gluconote.util.formatTime
import com.gluconote.util.levelOf
import com.gluconote.util.startOfDay
import java.util.Calendar

/**
 * 新增：4 步适老引导（状态 → 日期 → 时间 → 血糖）。
 * 编辑：保留单页表单，避免改一条也要走完全程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    navController: NavController,
    periodArg: String,
    idArg: String
) {
    val vm = viewModel<AddRecordViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(periodArg, idArg) {
        vm.bootstrap(periodArg, idArg)
    }

    var valueText by rememberSaveable { mutableStateOf("") }
    var noteText by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.ready, ui.editingId, ui.editingInitialValue, ui.editingInitialNote) {
        if (ui.ready && !seeded) {
            valueText = ui.editingInitialValue
            noteText = ui.editingInitialNote
            seeded = true
        }
    }

    var confirmValue by remember { mutableStateOf<Float?>(null) }
    val isEdit = ui.editingId != null

    fun exit() {
        navController.popBackStack()
    }

    fun handleBack() {
        if (isEdit || vm.goBackOrExit()) exit()
    }

    BackHandler(onBack = { handleBack() })

    fun trySave() {
        val v = valueText.toFloatOrNull()
        when {
            v == null || v <= 0f ->
                Toast.makeText(context, "请输入有效血糖值", Toast.LENGTH_SHORT).show()
            v < 1.0f || v > 50.0f -> confirmValue = v
            else -> vm.commit(v, noteText) {
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                exit()
            }
        }
    }

    if (!ui.ready) {
        Box(Modifier.fillMaxSize())
        return
    }

    if (isEdit) {
        EditRecordForm(
            ui = ui,
            valueText = valueText,
            onValueChange = { valueText = it },
            noteText = noteText,
            onNoteChange = { noteText = it },
            onBack = { exit() },
            onSave = { trySave() },
            onPeriod = { vm.setPeriod(it) },
            onShowDate = { vm.showDatePicker(true) }
        )
    } else {
        GuidedAddScaffold(
            ui = ui,
            valueText = valueText,
            onValueChange = { valueText = it },
            onBack = { handleBack() },
            onNext = { vm.nextStep() },
            onSelectPeriod = { key ->
                vm.setPeriod(key)
                vm.nextStep()
            },
            onToday = {
                vm.setDateToToday()
                vm.nextStep()
            },
            onYesterday = {
                vm.setDateToYesterday()
                vm.nextStep()
            },
            onPickCalendar = { vm.showDatePicker(true) },
            onNow = { vm.setTimeToNow() },
            onHourMinute = { h, m -> vm.setHourMinute(h, m) },
            onSave = { trySave() }
        )
    }

    confirmValue?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmValue = null },
            title = { Text("数值异常提醒") },
            text = {
                Text("血糖值 $v mmol/L 超出常见范围（1.0–50.0），请确认是否输错。仍要保存吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmValue = null
                    vm.commit(v, noteText) {
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        exit()
                    }
                }) { Text("仍然保存") }
            },
            dismissButton = {
                TextButton(onClick = { confirmValue = null }) { Text("取消") }
            }
        )
    }

    if (ui.showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = ui.measureTime)
        DatePickerDialog(
            onDismissRequest = { vm.showDatePicker(false) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) {
                        val src = Calendar.getInstance().apply { timeInMillis = ui.measureTime }
                        val merged = Calendar.getInstance().apply {
                            timeInMillis = picked
                            set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, src.get(Calendar.MINUTE))
                        }
                        vm.setMeasureTime(merged.timeInMillis)
                        if (!isEdit && ui.step == 1) vm.nextStep()
                    }
                    vm.showDatePicker(false)
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { vm.showDatePicker(false) }) { Text("取消") }
            }
        ) {
            DatePicker(state)
        }
    }
}

@Composable
private fun GuidedAddScaffold(
    ui: AddRecordUiState,
    valueText: String,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSelectPeriod: (String) -> Unit,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onPickCalendar: () -> Unit,
    onNow: () -> Unit,
    onHourMinute: (Int, Int) -> Unit,
    onSave: () -> Unit
) {
    val step = ui.step
    val coach = coachCopy(step)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "记一笔血糖",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            WizardBottomBar(
                canProceed = when (step) {
                    0 -> ui.periodKey.isNotBlank()
                    1, 2 -> true
                    else -> valueText.toFloatOrNull()?.let { it > 0f } == true
                },
                onBack = onBack,
                onPrimary = {
                    when (step) {
                        0 -> onSelectPeriod(ui.periodKey)
                        1, 2 -> onNext()
                        else -> onSave()
                    }
                },
                primaryLabel = when (step) {
                    3 -> "确认保存"
                    else -> "下一步"
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            StepProgress(step = step, total = 4)
            Spacer(Modifier.height(8.dp))
            Text(
                coach.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                coach.ask,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                coach.tip,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            ChoiceTrail(ui = ui, step = step)
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = { Motion.contentTransform() },
                label = "wizardStep",
                modifier = Modifier.weight(1f)
            ) { s ->
                when (s) {
                    0 -> StepPeriod(
                        selectedKey = ui.periodKey,
                        recommendedKey = ui.recommendedPeriodKey,
                        onSelect = onSelectPeriod
                    )
                    1 -> StepDate(
                        measureTime = ui.measureTime,
                        onToday = onToday,
                        onYesterday = onYesterday,
                        onCalendar = onPickCalendar
                    )
                    2 -> StepTime(
                        measureTime = ui.measureTime,
                        onNow = {
                            onNow()
                        },
                        onHourMinute = onHourMinute
                    )
                    else -> StepValue(
                        valueText = valueText,
                        onValueChange = onValueChange,
                        periodKey = ui.periodKey,
                        settings = ui.settings
                    )
                }
            }
        }
    }
}

private data class CoachCopy(val label: String, val ask: String, val tip: String)

private fun coachCopy(step: Int): CoachCopy = when (step) {
    0 -> CoachCopy("第 1 步 / 共 4 步", "这次是什么状态？", "不确定就点带「推荐」的那一格。")
    1 -> CoachCopy("第 2 步 / 共 4 步", "哪一天测的？", "多数时候选「今天」就行。")
    2 -> CoachCopy("第 3 步 / 共 4 步", "大概几点测的？", "可以点「现在」，或滑动选择时、分。")
    else -> CoachCopy("第 4 步 / 共 4 步", "血糖是多少？", "对着试纸或仪器，用大键盘慢慢敲。")
}

@Composable
private fun StepProgress(step: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (i <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun ChoiceTrail(ui: AddRecordUiState, step: Int) {
    if (step == 0) return
    val parts = buildList {
        add(Period.fromKey(ui.periodKey).plainLabel())
        if (step >= 2) add(friendlyDateLabel(ui.measureTime))
        if (step >= 3) add(formatTime(ui.measureTime))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    ) {
        Text(
            "已选：" + parts.joinToString(" · "),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun StepPeriod(
    selectedKey: String,
    recommendedKey: String,
    onSelect: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Period.all.forEach { p ->
            val selected = p.key == selectedKey
            val recommended = p.key == recommendedKey
            Surface(
                onClick = { onSelect(p.key) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                tonalElevation = if (selected) 2.dp else 0.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(p.toneColor(), CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.label(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (recommended) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        "推荐",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            p.hint(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDate(
    measureTime: Long,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onCalendar: () -> Unit
) {
    val todayStart = startOfDay(System.currentTimeMillis())
    val yesterdayStart = todayStart - 24L * 60 * 60 * 1000
    val pickedStart = startOfDay(measureTime)
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BigChoiceButton(
            title = "今天",
            subtitle = friendlyDateLabel(todayStart),
            selected = pickedStart == todayStart,
            onClick = onToday
        )
        BigChoiceButton(
            title = "昨天",
            subtitle = friendlyDateLabel(yesterdayStart),
            selected = pickedStart == yesterdayStart,
            onClick = onYesterday
        )
        OutlinedButton(
            onClick = onCalendar,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                if (pickedStart != todayStart && pickedStart != yesterdayStart)
                    "已选 ${friendlyDateLabel(measureTime)} · 打开日历"
                else "选其他日期…",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun StepTime(
    measureTime: Long,
    onNow: () -> Unit,
    onHourMinute: (Int, Int) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = measureTime }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val now = Calendar.getInstance()
    val isNow = hour == now.get(Calendar.HOUR_OF_DAY) && minute == now.get(Calendar.MINUTE)
    var wheelEpoch by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalButton(
            onClick = {
                onNow()
                wheelEpoch++
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                if (isNow) "现在（已选）" else "用现在的时间",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "${hour.pad2()}:${minute.pad2()}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "上下滑动选择时、分",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        key(wheelEpoch) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelColumn(
                    values = (0..23).map { "${it.pad2()} 时" },
                    initialIndex = hour,
                    onValueChange = { onHourMinute(it, minute) },
                    modifier = Modifier.width(120.dp),
                    itemHeight = 48.dp
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                WheelColumn(
                    values = (0..59).map { "${it.pad2()} 分" },
                    initialIndex = minute,
                    onValueChange = { onHourMinute(hour, it) },
                    modifier = Modifier.width(120.dp),
                    itemHeight = 48.dp
                )
            }
        }
    }
}

@Composable
private fun StepValue(
    valueText: String,
    onValueChange: (String) -> Unit,
    periodKey: String,
    settings: Settings
) {
    val parsed = valueText.toFloatOrNull()
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (valueText.isBlank()) "—" else valueText,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 64.sp
        )
        Text(
            "mmol/L",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (parsed != null && parsed > 0f) {
            Spacer(Modifier.height(8.dp))
            val level = levelOf(parsed, periodKey, settings)
            val (label, arrow) = when (level) {
                "low" -> "偏低" to "↓"
                "high" -> "偏高" to "↑"
                else -> "正常" to "→"
            }
            val color = statusColor(parsed, periodKey, settings)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.14f)
            ) {
                Text(
                    "$label $arrow",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        NumericKeypad(
            onDigit = { d ->
                if (valueText.length >= 5) return@NumericKeypad
                if (d == "." && valueText.contains('.')) return@NumericKeypad
                if (d == "." && valueText.isEmpty()) {
                    onValueChange("0.")
                } else {
                    onValueChange(valueText + d)
                }
            },
            onBackspace = {
                if (valueText.isNotEmpty()) onValueChange(valueText.dropLast(1))
            },
            onClear = { onValueChange("") }
        )
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    Surface(
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                key,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) {
            Text("清空", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BigChoiceButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WizardBottomBar(
    canProceed: Boolean,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    primaryLabel: String
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("返回", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onPrimary,
                enabled = canProceed,
                modifier = Modifier
                    .weight(1.4f)
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(primaryLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRecordForm(
    ui: AddRecordUiState,
    valueText: String,
    onValueChange: (String) -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onPeriod: (String) -> Unit,
    onShowDate: () -> Unit
) {
    var showTime by remember { mutableStateOf(false) }
    val vm = viewModel<AddRecordViewModel>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "测量标签",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    PeriodChips(
                        selected = Period.fromKey(ui.periodKey),
                        onSelect = { onPeriod(it.key) }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "测量时间（点击修改）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FieldBox(formatDate(ui.measureTime), Modifier.weight(1f), onShowDate)
                        FieldBox(formatTime(ui.measureTime), Modifier.weight(1f)) { showTime = true }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = onValueChange,
                        label = { Text("血糖值 (mmol/L)") },
                        placeholder = { Text("如 6.2") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = onNoteChange,
                        label = { Text("备注") },
                        placeholder = { Text("可选") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("保存修改")
                    }
                }
            }
        }
    }

    if (showTime) {
        WheelTimeDialog(
            initialTimeMillis = ui.measureTime,
            onConfirm = { h, m ->
                vm.setHourMinute(h, m)
                showTime = false
            },
            onDismiss = { showTime = false }
        )
    }
}

@Composable
private fun FieldBox(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, fontSize = 15.sp)
        }
    }
}

private fun friendlyDateLabel(ts: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    val base = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    val today = startOfDay(System.currentTimeMillis())
    val day = startOfDay(ts)
    val diff = ((day - today) / (24L * 60 * 60 * 1000)).toInt()
    return when (diff) {
        0 -> "今天（$base）"
        -1 -> "昨天（$base）"
        1 -> "明天（$base）"
        else -> {
            val weeks = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val w = weeks[cal.get(Calendar.DAY_OF_WEEK) - 1]
            "$base 周$w"
        }
    }
}
