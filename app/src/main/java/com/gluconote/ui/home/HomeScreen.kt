package com.gluconote.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gluconote.data.model.Period
import com.gluconote.ui.components.RecordCard
import com.gluconote.util.formatDate
import com.gluconote.util.formatTime
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val vm = viewModel<HomeViewModel>()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "hero") {
            HeroCard(
                lastValue = state.lastValue,
                lastPeriod = state.lastPeriod,
                lastTime = state.recent.firstOrNull()?.measureTime,
                settings = state.settings,
                todayCount = state.todayCount,
                weekAvg = state.weekAvg
            )
        }

        item(key = "quick-title") {
            Text("快速记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }

        item(key = "quick-chips") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Period.all.forEach { p ->
                    FilterChip(
                        selected = false,
                        onClick = { navController.navigate("records?period=${p.key}") },
                        label = { Text(p.label()) }
                    )
                }
            }
        }

        item(key = "recent-title") {
            Text("最近记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }

        if (state.recent.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text("还没有记录，点上面的标签开始", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(state.recent, key = { it.id }, contentType = { "record" }) { r ->
                RecordCard(record = r, settings = state.settings) {
                    navController.navigate("records?period=${r.period}")
                }
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }
}

/** 顶部概览卡：最近一次血糖 + 状态 + 两个小指标 */
@Composable
private fun HeroCard(
    lastValue: Float?,
    lastPeriod: String,
    lastTime: Long?,
    settings: com.gluconote.data.model.Settings,
    todayCount: Int,
    weekAvg: Float
) {
    val heroColor = MaterialTheme.colorScheme.primaryContainer
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .padding(0.dp)
    ) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = heroColor),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    "最近一次血糖",
                    style = MaterialTheme.typography.labelLarge,
                    color = onHero.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        lastValue?.let { "%.1f".format(Locale.US, it) } ?: "--",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = onHero
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        "mmol/L",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onHero.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                val periodLabel = if (lastPeriod.isNotBlank()) Period.fromKey(lastPeriod).plainLabel() else ""
                val timeLabel = lastTime?.let { " · ${formatTime(it)} ${formatDate(it)}" } ?: ""
                Text(
                    if (periodLabel.isBlank()) "暂无记录，点下方标签开始"
                    else "$periodLabel$timeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric("今日记录", todayCount.toString(), Modifier.weight(1f), onHero)
                    HeroMetric(
                        "近7天平均",
                        if (weekAvg > 0) "%.1f".format(Locale.US, weekAvg) else "--",
                        Modifier.weight(1f),
                        onHero
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(title: String, value: String, modifier: Modifier = Modifier, contentColor: androidx.compose.ui.graphics.Color) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = contentColor.copy(alpha = 0.12f)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.75f))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}
