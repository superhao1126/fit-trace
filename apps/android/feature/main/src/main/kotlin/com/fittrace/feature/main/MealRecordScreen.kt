package com.fittrace.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MealRecordScreen(
    state: EditorState,
    model: RecordEditorViewModel,
    onBack: () -> Unit,
) {
    val draft = state.draft
    var editing by rememberSaveable { mutableStateOf(draft.id == null) }
    var picker by rememberSaveable { mutableStateOf(draft.id == null && draft.foods.isEmpty()) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var index by remember { mutableStateOf<Int?>(null) }
    var calendar by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    var meal by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var readingPhoto by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val catalog = remember(context) { catalogFoods(context) }
    val time = OffsetDateTime.parse(draft.time).atZoneSameInstant(ZoneId.systemDefault())
    val back = {
        if (!state.saving && !readingPhoto) {
            if (state.dirty) leave = true else onBack()
        }
    }
    BackHandler { back() }
    val known = draft.foods.mapNotNull { it.calories }
    val total = if (known.isEmpty()) "—" else known.sum().toString()
    val unknown = draft.foods.count { it.calories == null }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!state.loading && !state.loadingFailed)
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .imePadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${if (editing) "保存到" else "记录于"} ${time.format(DateTimeFormatter.ofPattern("M月d日"))} · ${mealLabels[draft.type] ?: "饮食"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (!editing) editing = true
                            else if (draft.foods.isEmpty()) picker = true else model.save()
                        },
                        enabled = !state.saving && !readingPhoto,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (state.saving)
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        else
                            Text(
                                if (!editing) "编辑这餐"
                                else if (draft.foods.isEmpty()) "添加食物"
                                else if (state.error != null) "重新保存" else "保存这餐",
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                    }
                    if (draft.id != null && !editing)
                        TextButton(
                            onClick = { deleting = true },
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                        }
                }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(430.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    )
            )
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = back,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.width(32.dp).semantics { contentDescription = "返回" },
                    ) {
                        Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        if (editing) "核对这餐" else "饮食记录详情",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        TextButton(
                            onClick = { calendar = true },
                            enabled = editing && !state.saving,
                        ) {
                            Text(
                                time.format(DateTimeFormatter.ofPattern("M月d日")),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Surface(
                        Modifier.weight(1.3f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        TextButton(onClick = { meal = true }, enabled = editing && !state.saving) {
                            Text(
                                "${mealLabels[draft.type] ?: "饮食"} · ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                when {
                    state.loading ->
                        InsightsCard {
                            Column(
                                Modifier.height(230.dp),
                                verticalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                repeat(3) {
                                    InsightsSkeleton(Modifier.fillMaxWidth().height(42.dp))
                                }
                            }
                        }
                    state.loadingFailed ->
                        InsightsCard {
                            InsightsEmpty("饮食记录读取失败", "检查网络后重试", "重新加载", model::load, error = true)
                        }
                    else -> {
                        if (editing && draft.foods.isNotEmpty())
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Image(
                                        painterResource(R.drawable.home_meal),
                                        null,
                                        Modifier.size(26.dp),
                                    )
                                    Text(
                                        "这餐整理好了\n份量不对，点一下就能改",
                                        fontSize = 13.sp,
                                        lineHeight = 21.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        if (draft.foods.isEmpty())
                            InsightsCard {
                                InsightsEmpty(
                                    "这餐还没有食物",
                                    "选择食物或自定义添加，未知热量可以留空",
                                    "选择食物",
                                    { picker = true },
                                )
                            }
                        else
                            InsightsCard {
                                draft.foods.forEachIndexed { itemIndex, food ->
                                    val local = catalog.firstOrNull { it.name == food.name }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        if (local != null)
                                            AsyncImage(
                                                local.image,
                                                null,
                                                Modifier.size(52.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop,
                                            )
                                        else
                                            Box(
                                                Modifier.size(52.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        RoundedCornerShape(12.dp),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Image(
                                                    painterResource(R.drawable.home_meal),
                                                    null,
                                                    Modifier.size(24.dp),
                                                )
                                            }
                                        Column(
                                            Modifier.weight(1f).clickable(
                                                enabled = editing && !state.saving
                                            ) {
                                                index = itemIndex
                                                custom = true
                                            },
                                            verticalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Text(
                                                food.name,
                                                fontSize = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                food.calories?.let { "$it kcal" } ?: "热量待补充",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                food.amount.orEmpty().ifBlank { "份量待补充" },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                        if (editing)
                                            Row {
                                                TextButton(
                                                    onClick = {
                                                        index = itemIndex
                                                        custom = true
                                                    },
                                                    enabled = !state.saving,
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.width(40.dp).height(48.dp),
                                                ) {
                                                    Text("修改", fontSize = 12.sp)
                                                }
                                                TextButton(
                                                    onClick = {
                                                        model.edit {
                                                            it.copy(
                                                                foods =
                                                                    it.foods.filterIndexed { i, _ ->
                                                                        i != itemIndex
                                                                    }
                                                            )
                                                        }
                                                    },
                                                    enabled = !state.saving,
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.width(40.dp).height(48.dp),
                                                ) {
                                                    Text(
                                                        "移除",
                                                        fontSize = 12.sp,
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .onSurfaceVariant,
                                                    )
                                                }
                                            }
                                    }
                                    if (itemIndex < draft.foods.lastIndex)
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                }
                            }
                        if (editing && draft.foods.isNotEmpty())
                            OutlinedButton(
                                onClick = { picker = true },
                                enabled = !state.saving && draft.foods.size < 50,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Text("继续添加食物")
                            }
                        InsightsCard {
                            Text("这餐约 $total kcal", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (unknown > 0) "$unknown 项食物热量待补充，未计入合计" else "合计来自已填写的食物热量",
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MealPhotoCard(state, model, editing) { readingPhoto = it }
                        if (editing)
                            OutlinedTextField(
                                draft.note,
                                { value -> model.edit { it.copy(note = value) } },
                                Modifier.fillMaxWidth(),
                                enabled = !state.saving,
                                label = { Text("备注（选填）") },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(16.dp),
                            )
                        else if (draft.note.isNotBlank())
                            InsightsCard {
                                Text(
                                    "备注",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(draft.note, fontSize = 14.sp)
                            }
                        Text(
                            "缺少营养数据的食物显示“待补充”，不按 0 计算。",
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.error?.let {
                            Text(
                                it,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
            }
        }
    }
    if (picker)
        FoodLibrarySheet(
            { picker = false },
            {
                picker = false
                index = null
                custom = true
            },
            draft.foods,
            "${time.format(DateTimeFormatter.ofPattern("M月d日"))} · ${mealLabels[draft.type] ?: "饮食"}",
        ) { food ->
            if (draft.foods.size < 50) model.edit { it.copy(foods = it.foods + food) }
            else model.error("每餐最多添加 50 项食物")
        }
    if (custom) {
        val food = index?.let { draft.foods.getOrNull(it) }
        val amount =
            food
                ?.amount
                ?.takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+)? g")) }
                ?.substringBefore(' ')
        if (food != null && amount != null && catalog.any { it.name == food.name })
            FoodLibrarySheet(
                { custom = false },
                {},
                mealLabel = "调整份量",
                portionFoodName = food.name,
                portionGrams = amount,
            ) { replacement ->
                model.edit {
                    it.copy(
                        foods =
                            it.foods.mapIndexed { i, entry ->
                                if (i == index) replacement else entry
                            }
                    )
                }
                custom = false
            }
        else
            FoodDialog(food, { custom = false }) { replacement ->
                model.edit {
                    it.copy(
                        foods =
                            if (index == null) it.foods + replacement
                            else
                                it.foods.mapIndexed { i, entry ->
                                    if (i == index) replacement else entry
                                }
                    )
                }
                custom = false
            }
    }
    if (calendar)
        RecordDateDialog(time.toLocalDate(), { calendar = false }) { date ->
            model.edit {
                it.copy(
                    time =
                        date
                            .atTime(time.toLocalTime())
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                )
            }
            calendar = false
        }
    if (clock)
        RecordTimeDialog(time.toLocalTime(), { clock = false }) { value ->
            model.edit {
                it.copy(
                    time =
                        time
                            .toLocalDate()
                            .atTime(value)
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                )
            }
            clock = false
        }
    if (meal)
        ModalBottomSheet(onDismissRequest = { meal = false }) {
            Column(
                Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("餐次与时间", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                InsightsSegments(mealLabels.values.toList(), mealLabels.keys.indexOf(draft.type)) {
                    index ->
                    model.edit { it.copy(type = mealLabels.keys.elementAt(index)) }
                }
                TextButton(
                    onClick = {
                        meal = false
                        clock = true
                    }
                ) {
                    Text("时间 ${time.format(DateTimeFormatter.ofPattern("HH:mm"))} ›")
                }
                Button(onClick = { meal = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("确认", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    if (leave)
        AlertDialog(
            onDismissRequest = { leave = false },
            title = { Text("放弃本次修改？") },
            text = { Text("未保存的输入会丢失。") },
            confirmButton = { TextButton(onClick = onBack) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { leave = false }) { Text("继续编辑") } },
        )
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "${time.format(DateTimeFormatter.ofPattern("M月d日"))} · ${mealLabels[draft.type]}\n删除后无法恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = false
                        model.delete()
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
}
