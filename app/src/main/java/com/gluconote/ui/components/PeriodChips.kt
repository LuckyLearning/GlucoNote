package com.gluconote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gluconote.data.model.Period

@Composable
fun PeriodChips(
    selected: Period,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Period.all.forEach { p ->
            val isSel = p == selected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(p) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isSel) 2.dp else 0.dp
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        p.label(),
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        p.hint(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
