package com.gluconote.ui.records

import android.widget.Toast
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.ui.components.PeriodChips
import com.gluconote.ui.theme.statusColor
import com.gluconote.util.formatDate
import com.gluconote.util.formatDateTime
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

    var confirmValue by remember { mutableStateOf<Float?>(null) }
    var deleting by remember { mutableStateOf<GlucoseRecord?>(null) }

    val grouped = records.groupBy { formatDate(it.measureTime) }.toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        if (ui.editingId != null) "编辑记录" else "新增记录",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    PeriodChips(selected = Period.fromKey(ui.periodKey), onSelect = { vm.setPeriod(it.key) })
                    Spacer(Modifier.height(12.dp))
                    Text("测量时间（点击可修改）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedBox(formatDate(ui.measureTime)) { vm.showDatePicker(true) }
                        OutlinedBox(formatTime(ui.measureTime)) { vm.showTimePicker(true) }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ui.valueText,
                        onValueChange = vm::setValue,
                        label = { Text("血糖值 (mmol/L)") },
                        placeholder = { Text("如 6.2") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ui.note,
                        onValueChange = vm::setNote,
                        label = { Text("备注") },
                        placeholder = { Text("可选，如 吃了面条") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val v = ui.valueText.toFloatOrNull()
                                when {
                                    v == null || v <= 0f -> Toast.makeText(context, "请输入有效血糖值", Toast.LENGTH_SHORT).show()
                                    v < 1.0f || v > 50.0f -> confirmValue = v
                                    else -> vm.commit(v) { Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (ui.editingId != null) "保存修改" else "保存记录") }
                        if (ui.editingId != null) {
                            TextButton(onClick = vm::reset, modifier = Modifier.weight(1f)) { Text("取消") }
                        }
                    }
                }
            }
        }

        item { Text("历史记录", style = MaterialTheme.typography.titleMedium) }
        if (grouped.isEmpty()) {
            item { Text("暂无记录，上方添加第一条", color = MaterialTheme.colorScheme.outline) }
        }
        grouped.forEach { (date, items) ->
            item { Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            items(items, key = { it.id }) { r ->
                Card(
                    Modifier.fillMaxWidth().clickable { vm.startEdit(r) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${formatTime(r.measureTime)} · ${Period.fromKey(r.period).plainLabel()}", style = MaterialTheme.typography.bodyMedium)
                            Text(formatDateTime(r.measureTime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            if (r.note.isNotEmpty()) Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%.1f".format(r.value), color = statusColor(r.value, r.period, settings), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { deleting = r }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    confirmValue?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmValue = null },
            title = { Text("数值异常提醒") },
            text = { Text("血糖值 $v mmol/L 超出常见范围（1.0–50.0），请确认是否输错。仍要保存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmValue = null
                    vm.commit(v) { Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show() }
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
        val src = Calendar.getInstance().apply { timeInMillis = ui.measureTime }
        val state = rememberTimePickerState(initialHour = src.get(Calendar.HOUR_OF_DAY), initialMinute = src.get(Calendar.MINUTE))
        AlertDialog(
            onDismissRequest = { vm.showTimePicker(false) },
            confirmButton = {
                TextButton(onClick = {
                    val merged = Calendar.getInstance().apply {
                        timeInMillis = ui.measureTime
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                    }
                    vm.setMeasureTime(merged.timeInMillis)
                    vm.showTimePicker(false)
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { vm.showTimePicker(false) }) { Text("取消") } },
            text = { TimePicker(state) }
        )
    }
}

@Composable
private fun OutlinedBox(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() }.weight(1f),
        shape = MaterialTheme.shapes.small,
        border = OutlinedButtonDefaults.outlinedButtonBorder(enabled = true),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(Modifier.padding(12.dp)) {
            Text(text)
        }
    }
}
