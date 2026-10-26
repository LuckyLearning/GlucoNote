package com.gluconote.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gluconote.data.model.Period
import com.gluconote.ui.components.DonutChart
import com.gluconote.ui.components.TrendLineChart
import com.gluconote.util.DAY_MS
import com.gluconote.util.Granularity
import com.gluconote.util.RangeType
import com.gluconote.util.computeBucketStats
import com.gluconote.util.computeRangeStats
import com.gluconote.util.computeRateByPeriod
import com.gluconote.util.computeTrend

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
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("统计", style = MaterialTheme.typography.headlineSmall) }

        item {
            Text("按标签筛选", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = ui.filterPeriod == null, onClick = { vm.setFilter(null) }, label = { Text("全部") })
                Period.all.forEach { p ->
                    FilterChip(selected = ui.filterPeriod == p.key, onClick = { vm.setFilter(p.key) }, label = { Text(p.label()) })
                }
            }
        }

        item {
            Text("趋势图", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RangeType.values().forEach { t ->
                    FilterChip(selected = ui.rangeType == t, onClick = { vm.setRangeType(t) }, label = { Text(t.name) })
                }
            }
            Spacer(Modifier.height(8.dp))
            TrendLineChart(points = trend, settings = ui.settings, modifier = Modifier.fillMaxWidth())
        }

        item {
            Text("达标率（按标签）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Period.all.forEach { p ->
                    val r = rateMap[p] ?: com.gluconote.util.RateResult(0, 0)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DonutChart(rate = r.rate, modifier = Modifier.height(64.dp).fillMaxWidth(0.2f))
                        Column(Modifier.weight(1f)) {
                            Text("${p.label()}  (${r.inRange}/${r.total})")
                            LinearProgressIndicator(progress = r.rate, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        item {
            Text("年/月/日区间分析", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Granularity.values().forEach { g ->
                    FilterChip(selected = ui.granularity == g, onClick = { vm.setGranularity(g) }, label = { Text(g.name) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (buckets.isEmpty()) {
                Text("暂无数据", color = MaterialTheme.colorScheme.outline)
            } else {
                buckets.forEach { b ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(b.label, style = MaterialTheme.typography.bodyMedium)
                            Text("均值 %.1f · 最高 %.1f · 最低 %.1f · 达标 %.0f%% · %d次".format(b.avg, b.max, b.min, b.rate * 100, b.count))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item {
            Text("区间统计", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RangeCard("近7天", range7, Modifier.weight(1f))
                RangeCard("近30天", range30, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RangeCard(title: String, s: com.gluconote.util.StatsResult, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(4.dp))
            if (s.count == 0) {
                Text("暂无数据", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("平均 %.1f".format(s.avg))
                Text("最高 %.1f / 最低 %.1f".format(s.max, s.min))
                Text("标准差 %.1f · %d次".format(s.std, s.count))
            }
        }
    }
}
