package com.gluconote.ui.home

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gluconote.data.model.Period
import com.gluconote.ui.theme.statusColor
import com.gluconote.util.formatDate
import com.gluconote.util.formatTime

@Composable
fun HomeScreen(navController: NavController) {
    val vm = viewModel<HomeViewModel>()
    val state by vm.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("血糖随手记", style = MaterialTheme.typography.headlineSmall) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewCard("今日记录", state.todayCount.toString())
                OverviewCard("最近一次", state.lastValue?.let { "%.1f".format(it) } ?: "--")
                OverviewCard("近7天平均", if (state.weekAvg > 0) "%.1f".format(state.weekAvg) else "--")
            }
        }

        item {
            Text("快速记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.all.forEach { p ->
                    Button(
                        onClick = { navController.navigate("records?period=${p.key}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${p.label()}  ·  ${p.hint()}")
                    }
                }
            }
        }

        item { Text("最近记录", style = MaterialTheme.typography.titleMedium) }

        if (state.recent.isEmpty()) {
            item { Text("暂无记录", color = MaterialTheme.colorScheme.outline) }
        } else {
            items(state.recent, key = { it.id }) { r ->
                Card(
                    Modifier.fillMaxWidth()
                        .clickable { navController.navigate("records?period=${r.period}") }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${formatTime(r.measureTime)} · ${Period.fromKey(r.period).plainLabel()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                formatDate(r.measureTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            "%.1f".format(r.value),
                            color = statusColor(r.value, r.period, state.settings),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(title: String, value: String) {
    Card(
        Modifier.weight(1f).height(80.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
