package com.fittrace.feature.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.*
import com.fittrace.core.designsystem.FitCard
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Chinese = Locale.SIMPLIFIED_CHINESE
private val kinds = RecordKind.entries
private val labels = listOf("记体重", "记饮食", "记运动", "记照片")
private val icons =
    listOf(
        R.drawable.home_body,
        R.drawable.home_meal,
        R.drawable.home_workout,
        R.drawable.home_photo,
    )
private val categoryColors =
    listOf(Color(0xFFE7EFF9), Color(0xFFFAEDE1), Color(0xFFEEE9F8), Color(0xFFE3F1EC))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    onDate: (LocalDate) -> Unit,
    onRefresh: () -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onRecord: (DiaryRecord) -> Unit = {},
) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    var supplement by remember { mutableStateOf(false) }
    var week by
        rememberSaveable(state.date.toString()) {
            mutableStateOf(
                state.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
            )
        }
    val scroll = rememberScrollState()
    val data = state.data
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val horizontalPadding =
        if (
            with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } <
                360.dp
        )
            16.dp
        else 20.dp
    PullToRefreshBox(
        isRefreshing = state.loading && data != null,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier.offset(y = with(LocalDensity.current) { -scroll.value.toDp() })
                .fillMaxWidth()
                .height(265.dp)
                .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                .background(
                    Brush.linearGradient(
                        if (dark) listOf(Color(0xFF29482A), Color(0xFF164F36), Color(0xFF1D2D23))
                        else listOf(Color(0xFFDDF8B4), Color(0xFFADF4CA), Color(0xFFEDF6EB))
                    )
                )
        )
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = horizontalPadding)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.date == today) "今日记录" else "历史记录",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (state.date == today) "记录日常，看见改变"
                        else state.date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Chinese)),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.date != today)
                    TextButton(onClick = { onDate(today) }) { Text("回到今天", fontSize = 12.sp) }
                TextButton(onClick = { calendar = true }) { Text("日历") }
            }
            var drag by remember { mutableFloatStateOf(0f) }
            Row(
                Modifier.fillMaxWidth().pointerInput(week) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(drag) > 48.dp.toPx()) {
                                val next = LocalDate.parse(week).plusWeeks(if (drag < 0) 1 else -1)
                                if (next <= today) week = next.toString()
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        },
                    )
                }
            ) {
                repeat(7) { index ->
                    val date = LocalDate.parse(week).plusDays(index.toLong())
                    val selected = date == state.date
                    val color =
                        if (selected) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier.weight(1f)
                            .padding(horizontal = 3.dp)
                            .height(54.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.surface
                                else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .semantics {
                                contentDescription = date.toString()
                                this.selected = selected
                                if (date > today) disabled()
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = date <= today,
                            ) {
                                onDate(date)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            listOf("一", "二", "三", "四", "五", "六", "日")[index],
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = color.copy(alpha = if (selected) 1f else .55f),
                        )
                        Text(
                            date.dayOfMonth.toString(),
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = color.copy(alpha = if (date > today) .3f else 1f),
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        if (date == today && !selected)
                            Text("·", color = MaterialTheme.colorScheme.primary, lineHeight = 4.sp)
                    }
                }
            }
            if (state.loading && data == null) HomeLoadingCard()
            state.error?.let { error ->
                if (data != null) {
                    FitMessage("刷新失败 · 当前为上次加载内容", onRefresh, error = true)
                } else
                    HomeCard {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painterResource(R.drawable.design_sprout),
                                null,
                                Modifier.size(80.dp),
                            )
                            Text(
                                "首页暂时加载失败",
                                Modifier.padding(top = 24.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                error,
                                Modifier.padding(top = 12.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onRefresh,
                                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(.8f),
                            ) {
                                Text("重试")
                            }
                        }
                    }
            }
            if (data != null) {
                FitCard {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (state.date == today) "今日已记录热量" else "当天已记录热量",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            data.calories.display(),
                            fontSize = 34.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "kcal",
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 没有热量目标接口：这里是品牌装饰，不绘制虚构的完成进度。
                        Box(
                            Modifier.padding(top = 4.dp).width(180.dp).height(62.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val arcColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                            val brandColor = MaterialTheme.colorScheme.primary
                            Canvas(Modifier.fillMaxSize()) {
                                val diameter = size.width - 24.dp.toPx()
                                drawArc(
                                    color = arcColor,
                                    startAngle = 180f,
                                    sweepAngle = 68f,
                                    useCenter = false,
                                    topLeft =
                                        androidx.compose.ui.geometry.Offset(
                                            12.dp.toPx(),
                                            8.dp.toPx(),
                                        ),
                                    size =
                                        androidx.compose.ui.geometry.Size(diameter, 96.dp.toPx()),
                                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                                )
                                drawArc(
                                    color = brandColor,
                                    startAngle = 292f,
                                    sweepAngle = 68f,
                                    useCenter = false,
                                    topLeft =
                                        androidx.compose.ui.geometry.Offset(
                                            12.dp.toPx(),
                                            8.dp.toPx(),
                                        ),
                                    size =
                                        androidx.compose.ui.geometry.Size(diameter, 96.dp.toPx()),
                                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                            Image(
                                painterResource(R.drawable.design_sprout),
                                null,
                                Modifier.padding(bottom = 4.dp).size(44.dp),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Metric("体重", data.weight.display(), "kg", Modifier.weight(1f), true)
                        Metric(
                            "运动",
                            if (data.workoutCount == 0) "未记录" else data.workoutMinutes.toString(),
                            if (data.workoutCount == 0) "" else "分钟",
                            Modifier.weight(1f),
                            true,
                        )
                        Metric(
                            "饮食",
                            if (data.mealCount == 0) "未记录" else data.mealCount.toString(),
                            if (data.mealCount == 0) "" else "餐",
                            Modifier.weight(1f),
                            true,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color =
                        if (state.date == today) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onAction(if (state.date == today) "体重目标" else "全部记录") }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val goal = data.goal
                        if (state.date == today) {
                            Text("⚑", Modifier.padding(end = 12.dp), fontSize = 22.sp)
                            Text(
                                if (goal == null) "设置一个体重目标"
                                else
                                    "${when(goal.type) { "LOSE_FAT" -> "减脂"
 "GAIN_MUSCLE" -> "增肌"
 else -> "保持" }} · 目标 ${goal.targetWeight.display()} kg",
                                Modifier.weight(1f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (goal == null) "去设置 ›" else "调整 ›",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        } else {
                            Text(
                                "正在查看 ${state.date.monthValue}月${state.date.dayOfMonth}日的记录",
                                Modifier.weight(1f),
                                fontSize = 12.sp,
                            )
                            Box {
                                TextButton(
                                    onClick = { supplement = true },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text("补记 ›", fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = supplement,
                                    onDismissRequest = { supplement = false },
                                ) {
                                    labels.forEachIndexed { index, label ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                supplement = false
                                                onAction(if (index == 2) "记训练" else label)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.loading && data == null) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp),
                        )
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                kinds.forEachIndexed { index, _ ->
                    Surface(
                        Modifier.weight(1f).heightIn(min = 80.dp).clickable {
                            onAction(if (index == 2) "记训练" else labels[index])
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Image(
                                    painterResource(
                                        listOf(
                                            R.drawable.quick_body,
                                            R.drawable.quick_meal,
                                            R.drawable.quick_workout,
                                            R.drawable.quick_photo,
                                        )[index]
                                    ),
                                    null,
                                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                                )
                            }
                            Text(labels[index], fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            if (state.loading && data == null) {
                Box(
                    Modifier.fillMaxWidth()
                        .height(108.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(18.dp),
                        )
                )
                Text(
                    "正在读取当天记录…",
                    Modifier.align(Alignment.CenterHorizontally),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (data == null && state.error != null) {
                Text(
                    "也可以直接记录，或从底栏查看其他页面",
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (data != null) {
                Row(
                    Modifier.fillMaxWidth().height(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.date == today) "今日记录" else "当天记录",
                        Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { onAction("全部记录") },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("全部 ›", fontSize = 12.sp)
                    }
                }
                if (data.records.isEmpty())
                    HomeCard {
                        Text(
                            if (state.date == today) "从今天的一次记录开始" else "这一天还没有记录",
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "体重、饮食、运动或照片，选一项记下来",
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                        )
                    }
                else {
                    data.records.forEach { record ->
                        val index = record.kind.ordinal
                        HomeCard {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onRecord(record) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(42.dp)
                                        .background(
                                            categoryColors[index],
                                            RoundedCornerShape(12.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(painterResource(icons[index]), null, Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            record.title,
                                            Modifier.weight(1f),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            record.value,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    Text(
                                        "${record.recordedAt.timeLabel()} · ${record.subtitle}",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
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
                val trend = data.trend
                if (
                    trend.bodyRecordedDays > 0 ||
                        trend.mealRecordedDays > 0 ||
                        trend.workoutCount > 0
                ) {
                    Text(
                        "近 7 天",
                        fontSize = 19.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    HomeCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Metric(
                                "体重变化",
                                trend.weightChange.display(),
                                "kg",
                                Modifier.weight(1f),
                                true,
                            )
                            Metric(
                                "饮食记录",
                                trend.mealRecordedDays.toString(),
                                "天",
                                Modifier.weight(1f),
                                true,
                            )
                            Metric(
                                "训练时长",
                                trend.workoutMinutes.toString(),
                                "分钟",
                                Modifier.weight(1f),
                                true,
                            )
                        }
                        Text(
                            "${trend.from} 至 ${trend.to} · 已记录 ${trend.bodyRecordedDays} 天体重",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
    if (calendar)
        FitDatePickerDialog(
            date = state.date,
            onDismiss = { calendar = false },
            onSave = {
                onDate(it)
                calendar = false
            },
            maxDate = today,
        )
}

@Composable
private fun HomeCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier,
    small: Boolean = false,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (unit.isEmpty()) value else "$value $unit",
            fontSize = if (small) 15.sp else 30.sp,
            lineHeight = if (small) 22.sp else 38.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun HomeNavigation(tab: Int, onTab: (Int) -> Unit) {
    val navIcons =
        listOf(
            R.drawable.home_home,
            R.drawable.home_trends,
            R.drawable.home_archive,
            R.drawable.home_profile,
        )
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(76.dp)) {
            listOf("首页", "趋势", "档案", "我的").forEachIndexed { index, label ->
                Column(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = tab == index }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onTab(index)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(navIcons[index]),
                        null,
                        Modifier.size(20.dp),
                        tint =
                            if (tab == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        label,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color =
                            if (tab == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun Double?.display(): String =
    this?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.ROOT, "%.1f", it)
    } ?: "—"

private fun String.timeLabel(): String =
    runCatching {
            OffsetDateTime.parse(this)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        .getOrDefault("")

@Composable
private fun HomeLoadingCard() {
    val color = MaterialTheme.colorScheme.primaryContainer
    HomeCard {
        Column(
            Modifier.fillMaxWidth().height(194.dp).semantics { contentDescription = "正在加载记录" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box(Modifier.size(110.dp, 14.dp).background(color, RoundedCornerShape(6.dp)))
            Box(Modifier.size(120.dp, 32.dp).background(color, RoundedCornerShape(12.dp)))
            Box(Modifier.size(180.dp, 60.dp).background(color, RoundedCornerShape(14.dp)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                repeat(3) {
                    Box(Modifier.size(65.dp, 18.dp).background(color, RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}
