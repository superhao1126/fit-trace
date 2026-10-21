package com.fittrace.feature.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.designsystem.*
import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.model.*
import java.time.OffsetDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TrendsState(
    val days: Int = 30,
    val data: BodyTrends? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class TrendsViewModel(private val repository: RecordsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(TrendsState())
    val state = mutable.asStateFlow()
    private var request: Job? = null

    init {
        load(30)
    }

    fun load(days: Int = mutable.value.days) {
        request?.cancel()
        mutable.value =
            if (days == mutable.value.days) mutable.value.copy(loading = true, error = null)
            else TrendsState(days)
        request =
            viewModelScope.launch {
                when (val result = repository.trends(days)) {
                    is AppResult.Success -> mutable.value = TrendsState(days, result.data, false)
                    is AppResult.Failure ->
                        mutable.value =
                            mutable.value.copy(
                                loading = false,
                                error = result.error.recordMessage(),
                            )
                }
            }
    }
}

@Composable
internal fun TrendsScreen(state: TrendsState, onDays: (Int) -> Unit, onRecord: () -> Unit) {
    var metric by remember { mutableIntStateOf(0) }
    var selected by remember(state.data, metric) { mutableIntStateOf(-1) }
    val series = state.data?.let { listOf(it.weight, it.bodyFat, it.waist)[metric] }
    val points = series?.points.orEmpty()
    val unit = listOf("kg", "%", "cm")[metric]
    FitPage(
        "身体趋势",
        action = {
            TextButton(onClick = { onDays(state.days) }, enabled = !state.loading) { Text("刷新") }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(7, 30, 90).forEach { days ->
                FilterChip(days == state.days, { onDays(days) }, { Text("$days 天") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("体重", "体脂率", "腰围").forEachIndexed { index, label ->
                FilterChip(index == metric, { metric = index }, { Text(label) })
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            FitMessage(if (state.data != null) "$it，以下保留上次结果" else it, { onDays(state.days) }, true)
        }
        if (state.data != null) {
            if (points.isEmpty()) {
                FitMessage("这段时间还没有${listOf("体重", "体脂率", "腰围")[metric]}记录")
                FitSaveButton(false, onRecord, "记录身体数据")
            } else {
                val point = points.getOrNull(selected) ?: points.last()
                FitCard {
                    Text(
                        "${point.value.number()} $unit",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(point.recordedAt.localTime(), style = MaterialTheme.typography.bodySmall)
                    TrendChart(points, selected) { selected = it }
                    Text(
                        if (points.size == 1) "只有一次记录，继续记录后查看变化" else "点击图表查看数值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FitCard {
                    Text("${state.days} 天内 · ${points.size} 次记录")
                    Text(
                        "变化 ${if(points.size > 1) (points.last().value - points.first().value).number() else "—"} $unit"
                    )
                    Text(
                        "最低 ${points.minOf { it.value }.number()} · 最高 ${points.maxOf { it.value }.number()} $unit"
                    )
                    Text("平均 ${points.map { it.value }.average().number()} $unit")
                }
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<TrendPoint>, selected: Int, onSelect: (Int) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val start = OffsetDateTime.parse(points.first().recordedAt).toInstant().toEpochMilli()
    val end = OffsetDateTime.parse(points.last().recordedAt).toInstant().toEpochMilli()
    val times =
        points.map {
            if (start == end) .5f
            else
                ((OffsetDateTime.parse(it.recordedAt).toInstant().toEpochMilli() - start)
                        .toDouble() / (end - start))
                    .toFloat()
        }
    Canvas(
        Modifier.fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = "身体趋势图，${points.size} 个记录点" }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val width = size.width - 32.dp.toPx()
                    onSelect(
                        times.indices.minBy {
                            kotlin.math.abs(16.dp.toPx() + times[it] * width - tap.x)
                        }
                    )
                }
            }
    ) {
        val padding = 16.dp.toPx()
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        val range = (max - min).coerceAtLeast(1.0)
        val locations =
            points.mapIndexed { index, point ->
                Offset(
                    padding + times[index] * (size.width - padding * 2),
                    size.height -
                        padding -
                        ((point.value - min) / range).toFloat() * (size.height - padding * 2),
                )
            }
        repeat(4) { index ->
            val y = padding + index * (size.height - 2 * padding) / 3
            drawLine(line, Offset(padding, y), Offset(size.width - padding, y), 1.dp.toPx())
        }
        locations.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 3.dp.toPx()) }
        locations.forEachIndexed { index, location ->
            drawCircle(color, if (index == selected) 7.dp.toPx() else 4.dp.toPx(), location)
        }
    }
}
