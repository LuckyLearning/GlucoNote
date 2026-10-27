package com.gluconote.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.gluconote.ui.theme.toneColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gluconote.data.model.GlucoseRecord
import com.gluconote.data.model.Period
import com.gluconote.ui.components.RecordCard
import com.gluconote.util.formatDate
import com.gluconote.util.formatTime
import java.util.Locale

@Composable
fun HomeScreen(navController: NavController) {
    val vm = viewModel<HomeViewModel>()
    val state by vm.uiState.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<GlucoseRecord?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "hero") {
            HeroCard(
                lastValue = state.lastValue,
                lastPeriod = state.lastPeriod,
                lastTime = state.lastTime,
                todayCount = state.todayCount,
                weekAvg = state.weekAvg
            )
        }

        item(key = "quick-add") {
            QuickAddBlock(
                onGuidedAdd = {
                    navController.navigate("add") {
                        launchSingleTop = true
                    }
                },
                onPeriodClick = { p ->
                    navController.navigate("add/period/${p.key}") {
                        launchSingleTop = true
                    }
                }
            )
        }

        item(key = "recent-header") {
            Text(
                "最近",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state.recent.isEmpty()) {
            item(key = "empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "还没有记录，点上方「记一笔血糖」开始",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(state.recent, key = { it.id }, contentType = { "record" }) { r ->
                RecordCard(
                    record = r,
                    settings = state.settings,
                    onClick = { navController.navigate("add/edit/${r.id}") { launchSingleTop = true } },
                    onDelete = { deleting = r }
                )
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
    }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除记录") },
            text = { Text("确定删除这条记录？") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.delete(r)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
        )
    }
}

/** 顶部概览：最近一次血糖 + 今日/近7天两个指标 */
@Composable
private fun HeroCard(
    lastValue: Float?,
    lastPeriod: String,
    lastTime: Long?,
    todayCount: Int,
    weekAvg: Float
) {
    val heroColor = MaterialTheme.colorScheme.primaryContainer
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = heroColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                "最近一次血糖",
                style = MaterialTheme.typography.labelLarge,
                color = onHero.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    lastValue?.let { "%.1f".format(Locale.US, it) } ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = onHero
                )
                Spacer(Modifier.padding(start = 8.dp))
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
                if (periodLabel.isBlank()) "暂无记录，点下方记一笔开始"
                else "$periodLabel$timeLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
private fun HeroMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = contentColor.copy(alpha = 0.12f)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}

/**
 * 适老入口：主按钮「记一笔血糖」走完整四步；
 * 五个时段小按钮为捷径（预选状态，从选日期开始）。
 */
@Composable
private fun QuickAddBlock(
    onGuidedAdd: () -> Unit,
    onPeriodClick: (Period) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onGuidedAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(
                    "记一笔血糖",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
            Text(
                "或直接选状态（跳过第 1 步）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Period.all.forEach { p ->
                    Surface(
                        onClick = { onPeriodClick(p) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(p.toneColor(), CircleShape)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when (p) {
                                    Period.FASTING -> "空腹"
                                    Period.BREAKFAST -> "早后"
                                    Period.LUNCH -> "午后"
                                    Period.DINNER -> "晚后"
                                    Period.SLEEP -> "睡前"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
