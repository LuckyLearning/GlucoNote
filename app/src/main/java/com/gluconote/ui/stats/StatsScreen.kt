package com.gluconote.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gluconote.data.model.Period
import com.gluconote.ui.components.DonutChart
import com.gluconote.ui.components.TrendLineChart
import com.gluconote.ui.theme.NormalColorLight
import com.gluconote.util.DAY_MS
import com.gluconote.util.Granularity
import com.gluconote.util.RangeType
import com.gluconote.util.RateResult
import com.gluconote.util.StatsResult
import com.gluconote.util.computeBucketStats
import com.gluconote.util.computeRangeStats
import com.gluconote.util.computeRateByPeriod
import com.gluconote.util.computeTrend
import java.util.Locale

@Composable
fun StatsScreen() {
    val vm = viewModel<StatsViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()

    val trend = remember(ui.records, ui.rangeType, ui.filterPeriod) {
        computeTrend(ui.records, ui.rangeType, ui.filterPeriod)
    }
    val rateMap = remember(ui.records, ui.settings) { computeRateByPeriod(ui.records, ui.settings) }
    val buckets = remember(ui.records, ui.granularity, ui.settings, ui.filterPeriod) {
        computeBucketStats(ui.records, ui.granularity, ui.settings, ui.filterPeriod)
    }
    val range7 = remember(ui.records, ui.filterPeriod) {
        computeRangeStats(ui.records, System.currentTimeMillis() - 7 * DAY_MS, System.currentTimeMillis(), ui.filterPeriod)
    }
    val range30 = remember(ui.records, ui.filterPeriod) {
        computeRangeStats(ui.records, System.currentTimeMillis() - 30 * DAY_MS, System.currentTimeMillis(), ui.filterPeriod)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "filter") {
            Column {
                Text("按标签筛选", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // 横向滚动：6 个芯片一行放不下，旧版 Row 会溢出屏幕
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = ui.filterPeriod == null,
                            onClick = { vm.setFilter(null) },
                            label = { Text("全部") },
                            colors = chipColors()
                        )
                    }
                    items(Period.all) { p ->
                        FilterChip(
                            selected = ui.filterPeriod == p.key,
                            onClick = { vm.setFilter(p.key) },
                            label = { Text(p.label()) },
                            colors = chipColors()
                        )
                    }
                }
            }
        }

        item(key = "trend") {
            Column {
                Text("趋势图", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RangeType.entries) { t ->
                        FilterChip(
                            selected = ui.rangeType == t,
                            onClick = { vm.setRangeType(t) },
                            label = { Text(rangeLabel(t)) },
                            colors = chipColors()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TrendLineChart(points = trend, settings = ui.settings, modifier = Modifier.fillMaxWidth())
            }
        }

        item(key = "rate") {
            Column {
                Text("达标率（按标签）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Period.all.forEach { p ->
                        val r = rateMap[p] ?: RateResult(0, 0)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DonutChart(
                                rate = r.rate,
                                color = if (r.rate >= 0.8f) NormalColorLight else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.height(56.dp).fillMaxWidth(0.18f)
                            )
                            Column(Modifier.weight(1f)) {
                                Text("${p.label()}（${r.inRange}/${r.total}）", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { r.rate },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = if (r.rate >= 0.8f) NormalColorLight else MaterialTheme.colorScheme.tertiary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "bucket") {
            Column {
                Text("年 / 月 / 日区间分析", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Granularity.entries) { g ->
                        FilterChip(
                            selected = ui.granularity == g,
                            onClick = { vm.setGranularity(g) },
                            label = { Text(granularityLabel(g)) },
                            colors = chipColors()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (buckets.isEmpty()) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        buckets.forEach { b ->
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(b.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "均值 %.1f · 最高 %.1f · 最低 %.1f · 达标 %.0f%% · %d 次"
                                            .format(Locale.US, b.avg, b.max, b.min, b.rate * 100, b.count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "range") {
            Column {
                Text("区间统计", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RangeCard("近 7 天", range7, Modifier.weight(1f))
                    RangeCard("近 30 天", range30, Modifier.weight(1f))
                }
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }
}

/** FilterChip 的选中配色统一：选中即实心主色 */
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

private fun rangeLabel(t: RangeType) = when (t) {
    RangeType.DAY -> "近 1 天"
    RangeType.WEEK -> "近 7 天"
    RangeType.MONTH -> "近 30 天"
    RangeType.YEAR -> "近 1 年"
}

private fun granularityLabel(g: Granularity) = when (g) {
    Granularity.DAY -> "按天"
    Granularity.MONTH -> "按月"
    Granularity.YEAR -> "按年"
}

@Composable
private fun RangeCard(title: String, s: StatsResult, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            if (s.count == 0) {
                Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MetricLine("平均", "%.1f".format(Locale.US, s.avg))
                MetricLine("最高 / 最低", "%.1f / %.1f".format(Locale.US, s.max, s.min))
                MetricLine("标准差", "%.1f（%d 次）".format(Locale.US, s.std, s.count))
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
