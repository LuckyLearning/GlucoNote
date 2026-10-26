package com.gluconote.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gluconote.data.model.Period
import com.gluconote.ui.components.DonutChart
import com.gluconote.ui.components.TrendLineChart
import com.gluconote.ui.theme.Motion
import com.gluconote.ui.theme.NormalColorLight
import com.gluconote.util.Granularity
import com.gluconote.util.RangeType
import com.gluconote.util.RateResult
import com.gluconote.util.StatsResult
import java.util.Locale

@Composable
fun StatsScreen() {
    val vm = viewModel<StatsViewModel>()
    val ui by vm.ui.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "filter") {
            Column {
                Text("\u6309\u6807\u7b7e\u7b5b\u9009", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = ui.filterPeriod == null,
                            onClick = { vm.setFilter(null) },
                            label = { Text("\u5168\u90e8") },
                            colors = chipColors()
                        )
                    }
                    items(Period.all, key = { it.key }) { p ->
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
                Text("\u8d8b\u52bf\u56fe", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RangeType.entries, key = { it.name }) { t ->
                        FilterChip(
                            selected = ui.rangeType == t,
                            onClick = { vm.setRangeType(t) },
                            label = { Text(rangeLabel(t)) },
                            colors = chipColors()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = Triple(ui.rangeType, ui.filterPeriod, ui.trend.size),
                    transitionSpec = { Motion.contentTransform() },
                    label = "trend"
                ) { _ ->
                    if (ui.trend.isEmpty()) {
                        Text("\u8be5\u9009\u8303\u56f4\u6682\u65e0\u6570\u636e", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        TrendLineChart(points = ui.trend, settings = ui.settings, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item(key = "rate") {
            Column {
                val rangePart = rangeLabel(ui.rangeType)
                val rateTitle = if (ui.filterPeriodLabel != null) {
                    "\u8fbe\u6807\u7387 \u00b7 ${ui.filterPeriodLabel} \u00b7 $rangePart"
                } else {
                    "\u8fbe\u6807\u7387 \u00b7 $rangePart"
                }
                Text(rateTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = Pair(ui.filterPeriod, ui.rangeType),
                    transitionSpec = { Motion.contentTransform() },
                    label = "rate"
                ) { _ ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ui.ratePeriods.forEach { p ->
                        val r = ui.rateMap[p] ?: RateResult(0, 0)
                        if (ui.filterPeriod == null && r.total == 0) return@forEach
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
                                Text(
                                    "${p.label()}\uff08${r.inRange}/${r.total}\uff09",
                                    style = MaterialTheme.typography.bodyMedium
                                )
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
        }

        item(key = "bucket") {
            Column {
                Text("\u65e5 / \u6708 / \u5e74\u805a\u5408\u7edf\u8ba1", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Granularity.entries, key = { it.name }) { g ->
                        FilterChip(
                            selected = ui.granularity == g,
                            onClick = { vm.setGranularity(g) },
                            label = { Text(granularityLabel(g)) },
                            colors = chipColors()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = Pair(ui.granularity, ui.filterPeriod),
                    transitionSpec = { Motion.contentTransform() },
                    label = "buckets"
                ) { _ ->
                    if (ui.buckets.isEmpty()) {
                        Text("\u6682\u65e0\u6570\u636e", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.buckets.forEach { b ->
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
                                        "\u5747\u503c %.1f \u00b7 \u6700\u9ad8 %.1f \u00b7 \u6700\u4f4e %.1f \u00b7 \u8fbe\u6807 %.0f%% \u00b7 %d \u6b21"
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
        }

        item(key = "range") {
            Column {
                Text("\u533a\u95f4\u7edf\u8ba1", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RangeCard("\u8fd1 7 \u5929", ui.range7, Modifier.weight(1f))
                    RangeCard("\u8fd1 30 \u5929", ui.range30, Modifier.weight(1f))
                }
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

private fun rangeLabel(t: RangeType) = when (t) {
    RangeType.DAY -> "\u8fd1 1 \u5929"
    RangeType.WEEK -> "\u8fd1 7 \u5929"
    RangeType.MONTH -> "\u8fd1 30 \u5929"
    RangeType.YEAR -> "\u8fd1 1 \u5e74"
}

private fun granularityLabel(g: Granularity) = when (g) {
    Granularity.DAY -> "\u6309\u65e5"
    Granularity.MONTH -> "\u6309\u6708"
    Granularity.YEAR -> "\u6309\u5e74"
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
                Text("\u6682\u65e0\u6570\u636e", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MetricLine("\u5e73\u5747", "%.1f".format(Locale.US, s.avg))
                MetricLine("\u6700\u9ad8 / \u6700\u4f4e", "%.1f / %.1f".format(Locale.US, s.max, s.min))
                MetricLine("\u6807\u51c6\u5dee", "%.1f\uff08%d \u6b21\uff09".format(Locale.US, s.std, s.count))
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
