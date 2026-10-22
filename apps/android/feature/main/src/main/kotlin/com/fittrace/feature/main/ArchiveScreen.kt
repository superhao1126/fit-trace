package com.fittrace.feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.fittrace.core.model.RecordKind
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordsScreen(
    state: RecordsState,
    kind: RecordKind?,
    date: LocalDate?,
    onKind: (RecordKind?) -> Unit,
    onDate: (LocalDate?) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    onOpen: (RecordItem) -> Unit,
    onCreate: (RecordKind) -> Unit,
    onBack: (() -> Unit)? = null,
    month: YearMonth = YearMonth.from(date ?: LocalDate.now()),
    onMonth: (YearMonth) -> Unit = {},
) {
    if (kind == RecordKind.PHOTO) {
        PhotoArchiveScreen(
            state,
            month,
            onMonth,
            onRefresh,
            onMore,
            onOpen,
            { onCreate(RecordKind.PHOTO) },
            onBack,
            { onKind(null) },
        )
        return
    }
    var calendar by remember { mutableStateOf(false) }
    var monthPicker by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    PullToRefreshBox(state.loading && state.items.isNotEmpty(), onRefresh) {
        InsightsPage(
            "档案",
            "每一次记录，都有迹可循",
            { if (kind != null) onCreate(kind) else add = true },
            onBack,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    TextButton(onClick = { monthPicker = true }) {
                        Text(
                            "${month.year}年${month.monthValue}月 ⌄",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Surface(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    TextButton(onClick = { calendar = true }) {
                        Text(
                            date?.format(DateTimeFormatter.ofPattern("M月d日 ⌄")) ?: "选择日期 ⌄",
                            fontSize = 12.sp,
                            color =
                                if (date != null) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            InsightsSegments(
                listOf("全部", "身体", "饮食", "运动", "照片"),
                kind?.let { it.ordinal + 1 } ?: 0,
            ) {
                onKind(if (it == 0) null else RecordKind.entries[it - 1])
            }
            when {
                state.items.isEmpty() && state.loading ->
                    Column(
                        Modifier.fillMaxWidth().semantics { contentDescription = "正在加载档案" },
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        InsightsSkeleton(Modifier.width(130.dp).height(14.dp))
                        repeat(4) {
                            InsightsCard {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    InsightsSkeleton(Modifier.size(38.dp))
                                    Column(
                                        Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        InsightsSkeleton(Modifier.width(110.dp).height(13.dp))
                                        InsightsSkeleton(Modifier.fillMaxWidth(.8f).height(10.dp))
                                    }
                                }
                            }
                        }
                    }
                state.items.isEmpty() && state.error != null ->
                    InsightsCard {
                        InsightsEmpty("档案暂时加载失败", "检查网络后重试，记录不会丢失", "重新加载", onRefresh, error = true)
                    }
                state.items.isEmpty() ->
                    InsightsCard {
                        InsightsEmpty(
                            "这段时间还没有记录",
                            "换个日期看看，或为当天补上一条",
                            "添加记录",
                            { if (kind != null) onCreate(kind) else add = true },
                        )
                        if (date != null || kind != null)
                            TextButton(
                                onClick = {
                                    onKind(null)
                                    onDate(null)
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text("查看整月记录 ›", fontSize = 12.sp)
                            }
                    }
                else -> {
                    if (state.error != null && !state.loadMoreError)
                        InsightsRetry("刷新失败 · 当前为上次加载内容", onRefresh)
                    state.items
                        .groupBy { archiveDay(it.time) }
                        .forEach { (day, items) ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    day.format(
                                        DateTimeFormatter.ofPattern(
                                            "M月d日 · EEEE",
                                            Locale.SIMPLIFIED_CHINESE,
                                        )
                                    ),
                                    Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (date != null)
                                    TextButton(
                                        onClick = { onDate(null) },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp),
                                    ) {
                                        Text("查看整月", fontSize = 12.sp)
                                    }
                            }
                            items.forEach { item -> ArchiveRow(item) { onOpen(item) } }
                        }
                    if (state.error != null && state.loadMoreError)
                        InsightsRetry("更早的记录加载失败", onMore, "已加载的 ${state.items.size} 条记录继续保留")
                    else if (state.items.size < state.total) {
                        OutlinedButton(
                            onClick = onMore,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                        ) {
                            Text(if (state.loading) "正在加载…" else "加载更多", fontSize = 12.sp)
                        }
                        Text(
                            "已显示 ${state.items.size} / ${state.total} 条记录",
                            Modifier.align(Alignment.CenterHorizontally),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else
                        Text(
                            "已显示${if (date != null) "当天" else "本月"}全部 ${state.items.size} 条${kind?.let { archiveKindLabel(it) }.orEmpty()}记录",
                            Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
        }
    }
    if (calendar)
        RecordDateDialog(
            date ?: month.atDay(if (month == YearMonth.now()) LocalDate.now().dayOfMonth else 1),
            { calendar = false },
        ) {
            onDate(it)
            calendar = false
        }
    if (monthPicker)
        ArchiveMonthPicker(month, { monthPicker = false }) {
            onMonth(it)
            monthPicker = false
        }
    if (add)
        ModalBottomSheet(onDismissRequest = { add = false }) {
            Text(
                "添加记录",
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            RecordKind.entries.forEach { category ->
                ListItem(
                    headlineContent = { Text("记${archiveKindLabel(category)}") },
                    modifier =
                        Modifier.clickable {
                            add = false
                            onCreate(category)
                        },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
}

internal fun archiveDay(time: String): LocalDate =
    OffsetDateTime.parse(time).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()

private fun archiveKindLabel(kind: RecordKind) =
    when (kind) {
        RecordKind.BODY -> "身体"
        RecordKind.MEAL -> "饮食"
        RecordKind.WORKOUT -> "运动"
        RecordKind.PHOTO -> "照片"
    }

@Composable
private fun ArchiveRow(item: RecordItem, onOpen: () -> Unit) {
    val resource =
        when (item.kind) {
            RecordKind.BODY -> R.drawable.home_body
            RecordKind.MEAL -> R.drawable.home_meal
            RecordKind.WORKOUT -> R.drawable.home_workout
            RecordKind.PHOTO -> R.drawable.home_photo
        }
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(14.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (item.kind == RecordKind.PHOTO && item.image != null)
                    AsyncImage(
                        item.image,
                        item.title,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                else Image(painterResource(resource), null, Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    item.title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val time =
                    OffsetDateTime.parse(item.time)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    listOf(time, item.summary).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.value.isNotBlank())
                    Text(
                        item.value,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                Text(
                    "›",
                    fontSize = 18.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchiveMonthPicker(
    month: YearMonth,
    onDismiss: () -> Unit,
    onMonth: (YearMonth) -> Unit,
) {
    var year by remember { mutableIntStateOf(month.year) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = { year-- },
                modifier = Modifier.semantics { contentDescription = "上一年" },
            ) {
                Text("‹")
            }
            Text("${year}年", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(
                onClick = { year++ },
                enabled = year < YearMonth.now().year,
                modifier = Modifier.semantics { contentDescription = "下一年" },
            ) {
                Text("›")
            }
        }
        repeat(4) { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) { col ->
                    val value = YearMonth.of(year, row * 3 + col + 1)
                    TextButton(
                        onClick = { onMonth(value) },
                        enabled = value <= YearMonth.now(),
                        modifier = Modifier.weight(1f).semantics { selected = value == month },
                    ) {
                        Text(
                            "${value.monthValue}月",
                            color =
                                if (value == month) MaterialTheme.colorScheme.secondary
                                else colorForMonth(value),
                        )
                    }
                }
            }
        }
        TextButton(
            onClick = { onMonth(YearMonth.now()) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("回到本月")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun colorForMonth(month: YearMonth) =
    if (month > YearMonth.now()) MaterialTheme.colorScheme.onSurface.copy(alpha = .3f)
    else MaterialTheme.colorScheme.onSurface
