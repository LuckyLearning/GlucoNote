package com.gluconote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gluconote.util.formatTime
import java.util.Calendar

/**
 * 闹钟式时间选择弹窗：两列滚轮（时 / 分）+ 摘要行，替代
 * material3 的表盘 TimePicker（在手机上单手操作困难）。
 *
 * 交互对齐系统时钟 App：滚轮循环滚动、停稳即选中、底部显示将设置的时间。
 */
@Composable
fun WheelTimeDialog(
    initialTimeMillis: Long,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val src = Calendar.getInstance().apply { timeInMillis = initialTimeMillis }
    var hour by remember { mutableIntStateOf(src.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(src.get(Calendar.MINUTE)) }

    val hours = (0..23).map { it.pad2() }
    val minutes = (0..59).map { it.pad2() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("选择测量时间", style = MaterialTheme.typography.titleMedium)
                Text(
                    "将记录为 ${formatTime(withHourMinute(hour, minute))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                WheelColumn(
                    values = hours,
                    initialIndex = hour,
                    onValueChange = { hour = it },
                    modifier = Modifier.width(96.dp)
                )
                WheelColumn(
                    values = minutes,
                    initialIndex = minute,
                    onValueChange = { minute = it },
                    modifier = Modifier.width(96.dp)
                )
            }
        }
    )
}

/** 用指定时分构造一个毫秒时间戳（只为了复用 formatTime 的格式化） */
private fun withHourMinute(hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
