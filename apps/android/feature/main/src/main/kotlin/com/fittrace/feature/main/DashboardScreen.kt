package com.fittrace.feature.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.FitCard
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Chinese = Locale.SIMPLIFIED_CHINESE
private val kinds = RecordKind.entries
private val labels = listOf("记体重", "记饮食", "记训练", "记照片")
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
    var week by
        rememberSaveable(state.date.toString()) {
            mutableStateOf(
                state.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
            )
        }
    val data = state.data
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
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                        ),
                        endY = 1400f,
                    )
                ),
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
                .padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.date == today) "今天"
                        else state.date.format(DateTimeFormatter.ofPattern("M月d日")),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        state.date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Chinese)),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.date != today)
                    TextButton(onClick = { onDate(today) }) { Text("今天", fontSize = 12.sp) }
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
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier.weight(1f)
                            .height(64.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .semantics {
                                contentDescription = date.toString()
                                this.selected = selected
                                if (date > today) disabled()
                            }
                            .clickable(enabled = date <= today) { onDate(date) },
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = color.copy(alpha = if (date > today) .3f else 1f),
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        if (date == today && !selected)
                            Text("·", color = MaterialTheme.colorScheme.primary, lineHeight = 4.sp)
                    }
                }
            }
            if (state.loading)
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().semantics { contentDescription = "正在加载记录" }
                )
            state.error?.let { error ->
                HomeCard {
                    Text(
                        if (data != null) "$error，以下保留上次结果" else error,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
            }
            if (data != null) {
                FitCard {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "当天已记录热量",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                data.calories.display(),
                                modifier = Modifier.alignByBaseline(),
                                fontSize = 36.sp,
                                lineHeight = 44.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (data.calories != null)
                                Text(
                                    "kcal",
                                    modifier = Modifier.alignByBaseline(),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                        }
                        // 没有热量目标接口：这里是品牌装饰，不绘制虚构的完成进度。
                        Box(
                            Modifier.padding(top = 4.dp).width(168.dp).height(88.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val arcColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f)
                            Canvas(Modifier.fillMaxSize()) {
                                val diameter = size.width - 24.dp.toPx()
                                drawArc(
                                    color = arcColor,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft =
                                        androidx.compose.ui.geometry.Offset(
                                            12.dp.toPx(),
                                            8.dp.toPx(),
                                        ),
                                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                            Image(
                                painterResource(R.drawable.design_sprout),
                                null,
                                Modifier.padding(bottom = 2.dp).size(64.dp),
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
                            if (data.workoutCount == 0) "—" else data.workoutMinutes.toString(),
                            "分钟",
                            Modifier.weight(1f),
                            true,
                        )
                        Metric(
                            "饮食",
                            if (data.mealCount == 0) "—" else data.mealCount.toString(),
                            "餐",
                            Modifier.weight(1f),
                            true,
                        )
                    }
                }
                if (state.date == today)
                    HomeCard {
                        val goal = data.goal
                        Row(
                            Modifier.fillMaxWidth().clickable { onAction("体重目标") },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (goal == null) "设置体重目标"
                                    else
                                        "${when(goal.type) { "LOSE_FAT" -> "减脂"
 "GAIN_MUSCLE" -> "增肌"
 else -> "保持" }} · 目标 ${goal.targetWeight.display()} kg",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    if (goal == null) "给记录一个方向"
                                    else
                                        data.weight?.let {
                                            "相差 ${kotlin.math.abs(it - goal.targetWeight).display()} kg"
                                        } ?: "记录体重后查看变化",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                            Text("›", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                else
                    HomeCard {
                        Text(
                            "正在查看 ${state.date.monthValue}月${state.date.dayOfMonth}日记录",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onAction("全部记录") }) { Text("查看全部档案 ›") }
                    }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                kinds.forEachIndexed { index, _ ->
                    Surface(
                        Modifier.weight(1f).heightIn(min = 80.dp).clickable {
                            onAction(labels[index])
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
                                    Modifier.size(36.dp),
                                )
                            }
                            Text(labels[index], fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
            if (data != null) {
                if (data.records.isEmpty())
                    HomeCard {
                        Text(
                            if (state.date == today) "从今天的一次记录开始" else "这一天还没有记录",
                            fontSize = 19.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "体重、饮食、训练或照片\n选一项记下来，慢慢看见变化",
                            fontSize = 14.sp,
                            lineHeight = 25.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                        )
                    }
                else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "当天记录",
                            Modifier.weight(1f),
                            fontSize = 19.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = { onAction("全部记录") }) { Text("全部 ›") }
                    }
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
                                        Text(record.value, fontSize = 13.sp)
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
    if (calendar) {
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            Instant.ofEpochMilli(utcTimeMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate() <= today

                        override fun isSelectableYear(year: Int): Boolean = year <= today.year
                    },
            )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let {
                            onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        calendar = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("取消") } },
        ) {
            DatePicker(picker, title = { Text("选择记录日期", Modifier.padding(24.dp)) })
        }
    }
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = if (small) 19.sp else 30.sp,
            lineHeight = if (small) 26.sp else 38.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            unit,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .clickable { onTab(index) },
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
