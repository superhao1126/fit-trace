package com.fittrace.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 五行滚轮，中间行选中；滑动自动吸附，读屏可逐项调整，无键盘输入。 */
@Composable
internal fun FitWheelPicker(
    values: List<Int>,
    value: Int,
    label: String,
    unit: String,
    onValueChange: (Int) -> Unit,
    onScrolling: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    key(values) {
        val state =
            rememberLazyListState(
                initialFirstVisibleItemIndex = values.indexOf(value).coerceAtLeast(0)
            )
        val scope = rememberCoroutineScope()
        val update by rememberUpdatedState(onValueChange)
        val scrolling by rememberUpdatedState(onScrolling)
        val rowHeight = 44.dp
        fun centerIndex(): Int? {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            return layout.visibleItemsInfo
                .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
                ?.index
        }
        LaunchedEffect(value) {
            if (!state.isScrollInProgress)
                state.scrollToItem(values.indexOf(value).coerceAtLeast(0))
        }
        LaunchedEffect(state) {
            snapshotFlow { state.isScrollInProgress }
                .collect { busy ->
                    scrolling(busy)
                    if (!busy) centerIndex()?.let { values.getOrNull(it)?.let(update) }
                }
        }
        DisposableEffect(Unit) { onDispose { scrolling(false) } }
        fun select(index: Int) {
            scope.launch { state.animateScrollToItem(index.coerceIn(values.indices)) }
        }
        Box(modifier.height(rowHeight * 5).clip(RoundedCornerShape(14.dp))) {
            Box(
                Modifier.align(Alignment.Center)
                    .fillMaxWidth()
                    .height(rowHeight)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp),
                    )
            )
            LazyColumn(
                state = state,
                modifier =
                    Modifier.fillMaxSize().semantics {
                        contentDescription = label
                        stateDescription = "$value$unit"
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(
                                value.toFloat(),
                                values.first().toFloat()..values.last().toFloat(),
                                (values.size - 2).coerceAtLeast(0),
                            )
                        setProgress { target ->
                            select(
                                values.indexOf(
                                    target.toInt().coerceIn(values.first(), values.last())
                                )
                            )
                            true
                        }
                        customActions =
                            listOf(
                                CustomAccessibilityAction("增加$unit") {
                                    select(values.indexOf(value) + 1)
                                    true
                                },
                                CustomAccessibilityAction("减少$unit") {
                                    select(values.indexOf(value) - 1)
                                    true
                                },
                            )
                    },
                contentPadding = PaddingValues(vertical = rowHeight * 2),
                flingBehavior = rememberSnapFlingBehavior(state),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(values.size, key = { values[it] }) { index ->
                    val item = values[index]
                    val center = centerIndex() ?: values.indexOf(value)
                    val distance = kotlin.math.abs(index - center)
                    Box(
                        Modifier.fillMaxWidth()
                            .height(rowHeight)
                            .graphicsLayer {
                                alpha =
                                    when (distance) {
                                        0 -> 1f
                                        1 -> .55f
                                        else -> .25f
                                    }
                                scaleX = if (distance == 0) 1f else .9f
                                scaleY = scaleX
                            }
                            .clickable { select(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (unit == "年") item.toString() else item.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                            color =
                                if (distance == 0) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
