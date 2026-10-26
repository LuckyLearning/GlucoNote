@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.gluconote.ui.components


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 闹钟式滚轮选择器（参考系统时钟 App 的交互）：
 *  - 无限循环滚动（把列表重复多份、从中间开始，两个方向都转不完）；
 *  - 中心行高亮 + 上下渐隐；
 *  - 吸附滚动（snap fling），停稳即回调选中项。
 *
 * @param values 选项文本（顺序即取值顺序）
 * @param initialIndex 初始选中下标
 * @param onValueChange 停在某个选项时回调（下标）
 */
@Composable
fun WheelColumn(
    values: List<String>,
    initialIndex: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 44.dp
) {
    require(visibleCount % 2 == 1) { "visibleCount 必须是奇数" }
    val count = values.size
    val repeats = 21 // 奇数份，正中间开始，两个方向各转 50 圈
    val total = count * repeats
    val centerOffset = visibleCount / 2
    val startIndex = (repeats / 2) * count + initialIndex.coerceIn(0, count - 1)

    val listState = rememberLazyListState(startIndex - centerOffset)
    val haptics = LocalHapticFeedback.current
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val scrim = MaterialTheme.colorScheme.surface

    // 当前停在哪一行（以中心行算）
    val selectedIndex by remember(count) {
        derivedStateOf {
            (listState.firstVisibleItemIndex + centerOffset).mod(count)
        }
    }

    LaunchedEffect(count) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onValueChange((it + centerOffset).mod(count))
            }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        // 中心高亮带
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(highlight)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(itemHeight * visibleCount),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
        ) {
            items(total) { index ->
                val valueIndex = index.mod(count)
                val selected = valueIndex == selectedIndex
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(120),
                    label = "wheelItem"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        values[valueIndex],
                        style = if (selected) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.titleLarge,
                        color = color
                    )
                }
            }
        }
        // 上下渐隐（叠在列表上；中间留足整行全透明，避免压住选中行）
        Column(Modifier.matchParentSize()) {
            Box(
                Modifier.fillMaxWidth().height(itemHeight * (centerOffset - 0.5f))
                    .background(Brush.verticalGradient(listOf(scrim, scrim.copy(alpha = 0f))))
            )
            Box(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(itemHeight * (centerOffset - 0.5f))
                    .background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0f), scrim)))
            )
        }
    }
}

/** 两位数补零，用于分钟列 */
fun Int.pad2(): String = if (this < 10) "0$this" else "$this"
