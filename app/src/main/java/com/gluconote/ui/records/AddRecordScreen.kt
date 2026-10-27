@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gluconote.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gluconote.data.model.Period
import com.gluconote.data.model.Settings
import com.gluconote.ui.components.PeriodChips
import com.gluconote.ui.components.WheelColumn
import com.gluconote.ui.components.WheelScroll
import com.gluconote.ui.components.WheelTimeDialog
import com.gluconote.ui.components.pad2
import com.gluconote.ui.theme.Motion
import com.gluconote.ui.theme.statusColor
import com.gluconote.ui.theme.toneColor
import com.gluconote.util.GlucoseInput
import com.gluconote.util.formatDate
import com.gluconote.util.formatTime
import com.gluconote.util.levelOf
import com.gluconote.util.startOfDay
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** 保存成功后的停留时长，让长者看清"存进去了"再回首页 */
private const val SAVED_HOLD_MS = 700L

/**
 * 新增：4 步适老引导（状态 → 日期 → 时间 → 血糖）。
 * 编辑：保留单页表单，避免改一条也要走完全程。
 *
 * 交互约定（与产品对齐）：
 *  - 点选只高亮，前进一律由底部大按钮确认，杜绝"点一下跳两步"；
 *  - 底部只有一个主按钮，回上一步走顶栏箭头 / 系统返回；
 *  - 有未保存改动时退出要二次确认。
 */
@Composable
fun AddRecordScreen(
    navController: NavController,
    idArg: String
) {
    val vm = viewModel<AddRecordViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(idArg) {
        vm.bootstrap(idArg)
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

    // 基线：判定"有没有改过东西"，用于退出确认
    val basePeriod = remember(ui.ready) { ui.periodKey }
    val baseTime = remember(ui.ready) { ui.measureTime }

    var confirmValue by remember { mutableStateOf<Float?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    var savedValue by remember { mutableStateOf<Float?>(null) }
    val isEdit = ui.editingId != null

    fun exit() {
        navController.popBackStack()
    }

    val dirty = if (isEdit) {
        valueText != ui.editingInitialValue || noteText != ui.editingInitialNote ||
            ui.periodKey != basePeriod || minuteBucket(ui.measureTime) != minuteBucket(baseTime)
    } else {
        valueText.isNotBlank() || ui.periodKey != basePeriod ||
            minuteBucket(ui.measureTime) != minuteBucket(baseTime)
    }

    fun requestExit() {
        if (dirty) confirmExit = true else exit()
    }

    fun handleBack() {
        if (isEdit) {
            requestExit()
            return
        }
        // 闸门：带期望步骤，过期的返回事件直接忽略
        if (vm.backFrom(ui.step)) requestExit()
    }

    BackHandler(onBack = { handleBack() })

    fun doSave(v: Float) {
        vm.commit(v, noteText) { savedValue = v }
    }

    fun trySave() {
        val v = GlucoseInput.parse(valueText) ?: return
        if (GlucoseInput.isPlausible(v)) doSave(v) else confirmValue = v
    }

    LaunchedEffect(savedValue) {
        if (savedValue != null) {
            delay(SAVED_HOLD_MS)
            exit()
        }
    }

    if (!ui.ready) {
        Box(Modifier.fillMaxSize())
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (isEdit) {
            EditRecordForm(
                ui = ui,
                valueText = valueText,
                onValueChange = { valueText = it },
                noteText = noteText,
                onNoteChange = { noteText = it },
                onBack = { requestExit() },
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
                onNext = { h, m ->
                    // 第 3 步把滚轮结果一次性写回，滚动过程中不碰 VM，避免全页重组
                    if (ui.step == 2) vm.setHourMinute(h, m)
                    vm.advanceFrom(ui.step)
                },
                onSelectPeriod = { vm.setPeriod(it) },
                onToday = { vm.setDateToToday() },
                onYesterday = { vm.setDateToYesterday() },
                onPickCalendar = { vm.showDatePicker(true) },
                onNow = { vm.setTimeToNow() },
                onSave = { trySave() }
            )
        }

        AnimatedVisibility(
            visible = savedValue != null,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160)),
            exit = fadeOut(tween(120))
        ) {
            SavedOverlay(savedValue ?: 0f)
        }
    }

    confirmValue?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmValue = null },
            title = { Text("数值异常提醒") },
            text = {
                Text(
                    "血糖值 ${String.format(Locale.US, "%.1f", v)} mmol/L 超出常见范围" +
                        "（${GlucoseInput.PLAUSIBLE_MIN.toInt()}–${GlucoseInput.PLAUSIBLE_MAX.toInt()}），" +
                        "请确认是否输错。仍要保存吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmValue = null
                    doSave(v)
                }) { Text("仍然保存") }
            },
            dismissButton = {
                TextButton(onClick = { confirmValue = null }) { Text("取消") }
            }
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("还没保存") },
            text = { Text("这次的记录还没存下来，确定要离开吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    exit()
                }) { Text("离开") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("继续填写") }
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
    onNext: (hour: Int, minute: Int) -> Unit,
    onSelectPeriod: (String) -> Unit,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onPickCalendar: () -> Unit,
    onNow: () -> Unit,
    onSave: () -> Unit
) {
    val step = ui.step
    val coach = coachCopy(step)

    // 时 / 分只在本地存活：滚轮每格都写 VM 会让整页一直重组（旧实现卡顿的主因）
    val startCal = remember { Calendar.getInstance().apply { timeInMillis = ui.measureTime } }
    val hourState = remember { mutableIntStateOf(startCal.get(Calendar.HOUR_OF_DAY)) }
    val minuteState = remember { mutableIntStateOf(startCal.get(Calendar.MINUTE)) }

    // 「用现在的时间」→ 让滚轮滚过去，而不是重建整列
    var scrollToken by remember { mutableIntStateOf(0) }
    var hourScroll by remember { mutableStateOf<WheelScroll?>(null) }
    var minuteScroll by remember { mutableStateOf<WheelScroll?>(null) }

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
                    AddRecordViewModel.LAST_STEP -> GlucoseInput.parse(valueText) != null
                    else -> true
                },
                label = when (step) {
                    AddRecordViewModel.LAST_STEP -> "确认保存"
                    else -> "下一步"
                },
                onPrimary = {
                    when (step) {
                        AddRecordViewModel.LAST_STEP -> onSave()
                        else -> onNext(hourState.intValue, minuteState.intValue)
                    }
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
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) Motion.wizardForward()
                    else Motion.wizardBackward()
                },
                contentKey = { it },
                label = "wizardStep",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
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
                        hourState = hourState,
                        minuteState = minuteState,
                        hourScroll = hourScroll,
                        minuteScroll = minuteScroll,
                        onNow = {
                            onNow()
                            val now = Calendar.getInstance()
                            val h = now.get(Calendar.HOUR_OF_DAY)
                            val m = now.get(Calendar.MINUTE)
                            hourState.intValue = h
                            minuteState.intValue = m
                            scrollToken += 1
                            hourScroll = WheelScroll(h, scrollToken)
                            minuteScroll = WheelScroll(m, scrollToken)
                        }
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
    0 -> CoachCopy("第 1 步 / 共 4 步", "这次是什么状态？", "不确定就选带「推荐」的那格，选好点下面「下一步」。")
    1 -> CoachCopy("第 2 步 / 共 4 步", "哪一天测的？", "多数时候选「今天」，再点「下一步」。")
    2 -> CoachCopy("第 3 步 / 共 4 步", "大概几点测的？", "点「用现在的时间」，或上下滑动选时、分。")
    else -> CoachCopy("第 4 步 / 共 4 步", "血糖是多少？", "对着试纸或仪器，用大键盘慢慢敲。")
}

@Composable
private fun StepProgress(step: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            val color by animateColorAsState(
                targetValue = if (i <= step) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(300),
                label = "stepFill"
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color)
            )
        }
    }
}

/**
 * 已选摘要。高度固定，避免"第 1 步没有摘要、第 2 步突然多一块"把下面内容顶下去。
 */
@Composable
private fun ChoiceTrail(ui: AddRecordUiState, step: Int) {
    val text = if (step == 0) {
        "选好状态后，会在这里显示你选了什么"
    } else {
        val parts = buildList {
            add(Period.fromKey(ui.periodKey).plainLabel())
            if (step >= 2) add(shortDateLabel(ui.measureTime))
            if (step >= 3) add(formatTime(ui.measureTime))
        }
        "已选：" + parts.joinToString(" · ")
    }
    val placeholder = step == 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = MaterialTheme.shapes.large,
        color = if (placeholder) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    ) {
        Box(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                label = "trail"
            ) { t ->
                Text(
                    t,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (placeholder) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
            val container by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                animationSpec = tween(180),
                label = "periodBg"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = tween(180),
                label = "periodBorder"
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(p.key) }
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color = container,
                border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
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
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
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
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
        val otherSelected = pickedStart != todayStart && pickedStart != yesterdayStart
        BigChoiceButton(
            title = if (otherSelected) "其他日期" else "选其他日期…",
            subtitle = if (otherSelected) friendlyDateLabel(measureTime) else "点这里打开日历",
            selected = otherSelected,
            onClick = onCalendar
        )
    }
}

@Composable
private fun StepTime(
    hourState: MutableIntState,
    minuteState: MutableIntState,
    hourScroll: WheelScroll?,
    minuteScroll: WheelScroll?,
    onNow: () -> Unit
) {
    val hour = hourState.intValue
    val minute = minuteState.intValue
    val now = Calendar.getInstance()
    val isNow = hour == now.get(Calendar.HOUR_OF_DAY) && minute == now.get(Calendar.MINUTE)
    val hours = remember { (0..23).map { "${it.pad2()} 时" } }
    val minutes = remember { (0..59).map { "${it.pad2()} 分" } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalButton(
            onClick = onNow,
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
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelColumn(
                values = hours,
                initialIndex = hour,
                onValueChange = { hourState.intValue = it },
                modifier = Modifier.width(120.dp),
                itemHeight = 48.dp,
                scrollRequest = hourScroll
            )
            Text(
                ":",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            WheelColumn(
                values = minutes,
                initialIndex = minute,
                onValueChange = { minuteState.intValue = it },
                modifier = Modifier.width(120.dp),
                itemHeight = 48.dp,
                scrollRequest = minuteScroll
            )
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
    val parsed = GlucoseInput.parse(valueText)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 小屏 / 横屏时把按键压扁，保证整块键盘都在屏幕里
        val keyHeight = (maxHeight / 9).coerceIn(48.dp, 64.dp)
        val numberSize = if (maxHeight < 520.dp) 52.sp else 64.sp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = valueText,
                transitionSpec = {
                    (slideInVertically(tween(120)) { -it / 3 } + fadeIn(tween(120))) togetherWith
                        (slideOutVertically(tween(90)) { it / 3 } + fadeOut(tween(90)))
                },
                contentAlignment = Alignment.Center,
                label = "glucoseValue"
            ) { t ->
                Text(
                    if (t.isBlank()) "—" else t,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = numberSize
                )
            }
            Text(
                "mmol/L",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (parsed != null) {
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
                if (!GlucoseInput.isPlausible(parsed)) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            "这个值超出常见范围（${GlucoseInput.PLAUSIBLE_MIN.toInt()}–" +
                                "${GlucoseInput.PLAUSIBLE_MAX.toInt()}），保存前再核对一次",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            NumericKeypad(
                keyHeight = keyHeight,
                onDigit = { d -> GlucoseInput.append(valueText, d)?.let(onValueChange) },
                onBackspace = { onValueChange(GlucoseInput.backspace(valueText)) },
                onClear = { onValueChange("") }
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    keyHeight: Dp,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = remember {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "⌫")
        )
    }
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
                    KeypadKey(
                        label = key,
                        height = keyHeight,
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                else -> onDigit(key)
                            }
                        }
                    )
                }
            }
        }
        TextButton(
            onClick = onClear,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 48.dp)
        ) {
            Text("清空", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 数字键：按下时轻微内缩 + 一次轻触感，让长者确认"按到了" */
@Composable
private fun RowScope.KeypadKey(label: String, height: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(80),
        label = "keyScale"
    )
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interaction,
        modifier = Modifier
            .weight(1f)
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
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
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "bigChoiceBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(180),
        label = "bigChoiceBorder"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
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
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/** 底部只有一个主按钮：前进 / 保存都从这里走，避免"点选即前进"和"点按钮前进"两套逻辑打架 */
@Composable
private fun WizardBottomBar(
    canProceed: Boolean,
    label: String,
    onPrimary: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onPrimary,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 60.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 保存成功的确认画面：比 Toast 停留久、位置固定，长者能看清"确实存进去了" */
@Composable
private fun SavedOverlay(value: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "已保存",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${String.format(Locale.US, "%.1f", value)} mmol/L",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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

/** 只比到分钟：秒 / 毫秒的零头不算"改过时间"，否则点一下「现在」就提示未保存 */
private fun minuteBucket(ts: Long): Long = ts / 60_000L

/** 摘要行里用的短日期："今天" / "昨天" / "明天" / "10月25日" */
private fun shortDateLabel(ts: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    val base = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    val diff = ((startOfDay(ts) - startOfDay(System.currentTimeMillis())) / (24L * 60 * 60 * 1000)).toInt()
    return when (diff) {
        0 -> "今天"
        -1 -> "昨天"
        1 -> "明天"
        else -> base
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
