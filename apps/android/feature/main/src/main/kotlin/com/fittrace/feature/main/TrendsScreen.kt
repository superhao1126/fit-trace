package com.fittrace.feature.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 两块内容独立加载与失败：统计失败不能抹掉已经取得的身体曲线。
data class TrendsState(
    val days: Int = 7,
    val data: BodyTrends? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val insights: PeriodInsights? = null,
    val insightsLoading: Boolean = true,
    val insightsError: String? = null,
)

class TrendsViewModel(private val repository: RecordsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(TrendsState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var insightRequest: Job? = null

    init {
        load(7)
    }

    fun load(days: Int = mutable.value.days) {
        request?.cancel()
        insightRequest?.cancel()
        mutable.value =
            if (days == mutable.value.days) mutable.value.copy(loading = true, error = null)
            else TrendsState(days)
        request =
            viewModelScope.launch {
                when (val result = repository.trends(days)) {
                    is AppResult.Success ->
                        mutable.value =
                            mutable.value.copy(data = result.data, loading = false, error = null)
                    is AppResult.Failure ->
                        mutable.value =
                            mutable.value.copy(
                                loading = false,
                                error = result.error.recordMessage(),
                            )
                }
            }
        loadInsights()
    }

    fun loadInsights() {
        insightRequest?.cancel()
        val days = mutable.value.days
        mutable.value = mutable.value.copy(insightsLoading = true, insightsError = null)
        insightRequest =
            viewModelScope.launch {
                val offset = ZonedDateTime.now().offset.totalSeconds / 60
                when (val result = repository.insights(days, offset)) {
                    is AppResult.Success ->
                        mutable.value =
                            mutable.value.copy(insights = result.data, insightsLoading = false)
                    is AppResult.Failure ->
                        mutable.value =
                            mutable.value.copy(
                                insightsLoading = false,
                                insightsError = result.error.recordMessage(),
                            )
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendsScreen(
    state: TrendsState,
    onDays: (Int) -> Unit,
    onRecord: () -> Unit,
    onInsightsRetry: () -> Unit = { onDays(state.days) },
) {
    var metric by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember(state.data, metric) { mutableIntStateOf(-1) }
    val points =
        state.data
            ?.let { listOf(it.weight, it.bodyFat, it.waist)[metric].points }
            .orEmpty()
            .sortedBy { trendInstant(it.recordedAt) }
    val unit = listOf("kg", "%", "cm")[metric]
    PullToRefreshBox(state.loading && state.data != null, { onDays(state.days) }) {
        InsightsPage("趋势", "看清变化，找到自己的节奏", onRecord) {
            InsightsSegments(listOf("7 天", "30 天", "90 天"), listOf(7, 30, 90).indexOf(state.days)) {
                onDays(listOf(7, 30, 90)[it])
            }
            if (state.error != null && state.data != null)
                InsightsRetry("刷新失败 · 当前为上次加载内容", { onDays(state.days) })
            InsightsCard {
                InsightsSegments(listOf("体重", "体脂", "腰围"), metric) { metric = it }
                when {
                    state.loading && state.data == null ->
                        Column(
                            Modifier.height(300.dp).semantics { contentDescription = "正在加载趋势" },
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            InsightsSkeleton(Modifier.size(92.dp, 38.dp))
                            repeat(3) { InsightsSkeleton(Modifier.fillMaxWidth().height(2.dp)) }
                            InsightsSkeleton(Modifier.fillMaxWidth().height(38.dp))
                        }
                    state.error != null && state.data == null ->
                        InsightsEmpty(
                            "趋势暂时加载失败",
                            "稍后再试，已记录的数据仍然保留",
                            "重新加载",
                            { onDays(state.days) },
                            error = true,
                        )
                    points.isEmpty() ->
                        InsightsEmpty(
                            "这段时间还没有${listOf("体重", "体脂", "腰围")[metric]}记录",
                            "记一次身体数据，慢慢积累变化",
                            "记录身体数据",
                            onRecord,
                        )
                    else -> {
                        val point = points.getOrNull(selected) ?: points.last()
                        Text(
                            "${if (selected < 0) "最近一次" else "所选记录"} · ${trendDate(point.recordedAt).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                point.value.number(),
                                fontSize = 34.sp,
                                lineHeight = 42.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                unit,
                                Modifier.padding(start = 8.dp)
                                    .align(Alignment.Bottom)
                                    .padding(bottom = 6.dp),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            val change = points.last().value - points.first().value
                            Text(
                                if (points.size == 1) "仅 1 次记录"
                                else "较起始 ${if (change > 0) "+" else ""}${change.number()} $unit",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        TrendChart(points, state.data!!.from, state.data.to, selected) {
                            selected = it
                        }
                        if (points.size == 1)
                            Text(
                                "再记录一次，就能看到变化",
                                Modifier.align(Alignment.CenterHorizontally)
                                    .padding(vertical = 12.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TrendStat("起始", "${points.first().value.number()} $unit")
                                TrendStat("最低", "${points.minOf { it.value }.number()} $unit")
                                TrendStat(
                                    "平均",
                                    "${points.map { it.value }.average().number()} $unit",
                                )
                            }
                    }
                }
            }
            Text("同期饮食与训练", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            when {
                state.insightsLoading && state.insights == null ->
                    InsightsCard {
                        repeat(2) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(30.dp),
                            ) {
                                repeat(2) {
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        InsightsSkeleton(Modifier.fillMaxWidth().height(13.dp))
                                        InsightsSkeleton(Modifier.width(80.dp).height(20.dp))
                                    }
                                }
                            }
                        }
                    }
                state.insights == null && state.insightsError != null ->
                    InsightsRetry(
                        "饮食与训练暂时无法获取",
                        onInsightsRetry,
                        if (state.data != null) "体重趋势已加载，可稍后重试。" else "恢复网络后重新加载",
                    )
                state.insights != null -> {
                    if (state.insightsError != null)
                        InsightsRetry("同期统计刷新失败", onInsightsRetry, "以下保留上次结果")
                    val data = state.insights
                    InsightsCard {
                        if (data.mealCount == 0 && data.workoutCount == 0) {
                            Text(
                                "记录饮食与运动后，\n在这里查看同期的记录情况。",
                                Modifier.padding(vertical = 10.dp),
                                fontSize = 13.sp,
                                lineHeight = 26.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                TrendStat(
                                    "日均已记录热量",
                                    "${data.averageCalories.number()} kcal",
                                    Modifier.weight(1f),
                                )
                                TrendStat("运动次数", "${data.workoutCount} 次", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth()) {
                                TrendStat(
                                    "饮食记录天数",
                                    "${data.mealDays} / ${data.days} 天",
                                    Modifier.weight(1f),
                                )
                                TrendStat("运动总时长", "${data.workoutMinutes} 分钟", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Text(
                "热量仅汇总已填写食物，不代表完整全天摄入。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun trendInstant(time: String): Instant =
    runCatching { Instant.parse(time) }
        .getOrElse {
            runCatching { OffsetDateTime.parse(time).toInstant() }
                .getOrElse {
                    LocalDate.parse(time).atStartOfDay(ZoneId.systemDefault()).toInstant()
                }
        }

private fun trendDate(time: String) = trendInstant(time).atZone(ZoneId.systemDefault())

@Composable
private fun TrendChart(
    points: List<TrendPoint>,
    from: String,
    to: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val color = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val start = minOf(trendInstant(from), trendInstant(points.first().recordedAt)).toEpochMilli()
    val end = maxOf(trendInstant(to), trendInstant(points.last().recordedAt)).toEpochMilli()
    val times =
        points.map {
            if (start == end) .5f
            else
                ((trendInstant(it.recordedAt).toEpochMilli() - start).toDouble() / (end - start))
                    .toFloat()
        }
    val min = points.minOf { it.value }
    val max = points.maxOf { it.value }
    val margin = ((max - min) * .2).coerceAtLeast(.5)
    val lower = min - margin
    val upper = max + margin
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(
            Modifier.height(130.dp).width(40.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(upper, (lower + upper) / 2, lower).forEach {
                Text(
                    it.number(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(
                Modifier.fillMaxWidth()
                    .height(130.dp)
                    .semantics {
                        contentDescription = "身体趋势图，${points.size} 个记录点"
                        customActions =
                            points.mapIndexed { index, point ->
                                CustomAccessibilityAction(
                                    "${trendDate(point.recordedAt)}，${point.value.number()}"
                                ) {
                                    onSelect(index)
                                    true
                                }
                            }
                    }
                    .pointerInput(points, from, to) {
                        detectTapGestures { tap ->
                            val width = size.width - 12.dp.toPx()
                            onSelect(
                                times.indices.minBy {
                                    kotlin.math.abs(6.dp.toPx() + times[it] * width - tap.x)
                                }
                            )
                        }
                    }
            ) {
                val padding = 6.dp.toPx()
                val locations =
                    points.mapIndexed { index, point ->
                        Offset(
                            padding + times[index] * (size.width - padding * 2),
                            size.height -
                                padding -
                                ((point.value - lower) / (upper - lower)).toFloat() *
                                    (size.height - padding * 2),
                        )
                    }
                repeat(3) { index ->
                    val y = padding + index * (size.height - padding * 2) / 2
                    drawLine(line, Offset(padding, y), Offset(size.width - padding, y), 1.dp.toPx())
                }
                locations.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 2.dp.toPx()) }
                locations.forEachIndexed { index, location ->
                    drawCircle(
                        color,
                        if (index == selected || points.size == 1) 5.dp.toPx() else 3.dp.toPx(),
                        location,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(start, start + (end - start) / 2, end).forEach {
                    Text(
                        Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M/d")),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
