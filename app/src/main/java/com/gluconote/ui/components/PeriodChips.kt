package com.gluconote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gluconote.data.model.Period

/**
 * 测量标签选择：单行流式排布的紧凑芯片（替代早期整列堆叠的大卡片，
 * 5 张卡片既占屏又让每次选中重组一大片）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodChips(
    selected: Period,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Period.all.forEach { p ->
            FilterChip(
                selected = p == selected,
                onClick = { onSelect(p) },
                label = { Text(p.label()) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}
