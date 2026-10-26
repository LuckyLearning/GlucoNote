package com.gluconote.ui.records

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.ui.components.PeriodChips
import com.gluconote.ui.components.RecordCard
import com.gluconote.ui.components.WheelTimeDialog
import com.gluconote.util.formatDate
import com.gluconote.util.formatTime
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(navController: NavController, periodArg: String) {
    val vm = viewModel<RecordsViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(periodArg) {
        if (periodArg.isNotBlank()) vm.initPeriod(periodArg)
    }

    // ── 输入态放本地：逐字输入不再触发整个页面重组（卡顿主因之一）──
    var valueText by rememberSaveable { mutableStateOf("") }
    var noteText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(ui.editingId) {
        valueText = ui.editingInitialValue
        noteText = ui.editingInitialNote
    }

    var confirmValue by remember { mutableStateOf<Float?>(null) }
    var deleting by remember { mutableStateOf<GlucoseRecord?>(null) }

    // 分组结果随 records 记忆化：只在数据真变化时重算（原先每次重组都跑一遍 groupBy）
    val grouped = remember(records) { records.groupBy { formatDate(it.measureTime) } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "form") {
            ElevatedSection(title = if (ui.editingId != null) "编辑记录" else "新增记录") {
                PeriodChips(selected = Period.fromKey(ui.periodKey), onSelect = { vm.setPeriod(it.key) })
                Spacer(Modifier.height(12.dp))
                Text("测量时间（点击修改）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FieldBox(formatDate(ui.measureTime), Modifier.weight(1f)) { vm.showDatePicker(true) }
                    FieldBox(formatTime(ui.measureTime), Modifier.weight(1f)) { vm.showTimePicker(true) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
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
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    placeholder = { Text("可选，如 吃了面条") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val v = valueText.toFloatOrNull()
                            when {
                                v == null || v <= 0f ->
                                    Toast.makeText(context, "请输入有效血糖值", Toast.LENGTH_SHORT).show()
                                v < 1.0f || v > 50.0f -> confirmValue = v
                                else -> vm.commit(v, noteText) { Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) { Text(if (ui.editingId != null) "保存修改" else "保存记录") }
                    if (ui.editingId != null) {
                        TextButton(
                            onClick = vm::reset,
                            modifier = Modifier.weight(1f)
                        ) { Text("取消") }
                    }
                }
            }
        }

        item(key = "history-title") {
            Text("历史记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }

        if (grouped.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无记录，上方添加第一条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        grouped.forEach { (date, items) ->
            item(key = "date-$date") {
                Text(
                    date,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
            items(items, key = { it.id }, contentType = { "record" }) { r ->
                RecordCard(
                    record = r,
                    settings = settings,
                    onClick = { vm.startEdit(r) },
                    onDelete = { deleting = r }
                )
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }

    confirmValue?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmValue = null },
            title = { Text("数值异常提醒") },
            text = { Text("血糖值 $v mmol/L 超出常见范围（1.0–50.0），请确认是否输错。仍要保存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmValue = null
                    vm.commit(v, noteText) { Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() }
                }) { Text("仍然保存") }
            },
            dismissButton = { TextButton(onClick = { confirmValue = null }) { Text("取消") } }
        )
    }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除记录") },
            text = { Text("确定删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = { deleting = null; vm.delete(r) {} }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
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
            dismissButton = { TextButton(onClick = { vm.showDatePicker(false) }) { Text("取消") } }
        ) {
            DatePicker(state)
        }
    }

    if (ui.showTimePicker) {
        WheelTimeDialog(
            initialTimeMillis = ui.measureTime,
            onConfirm = { h, m ->
                val merged = Calendar.getInstance().apply {
                    timeInMillis = ui.measureTime
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                }
                vm.setMeasureTime(merged.timeInMillis)
                vm.showTimePicker(false)
            },
            onDismiss = { vm.showTimePicker(false) }
        )
    }
}

/** 通用小节卡片 */
@Composable
private fun ElevatedSection(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
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
