package com.fittrace.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.*
import java.time.*
import java.time.format.DateTimeFormatter

private data class Sport(
    val type: String,
    val name: String,
    val category: String,
    val image: Int? = null,
)

private val sports =
    listOf(
        Sport("RUNNING", "跑步（快速）", "跑步健走", R.drawable.sport_running_fast),
        Sport("RUNNING", "跑步（慢速）", "跑步健走", R.drawable.sport_running_slow),
        Sport("CARDIO", "走路", "跑步健走", R.drawable.sport_walking),
        Sport("CARDIO", "爬楼梯", "跑步健走", R.drawable.sport_stairs),
        Sport("OTHER", "登山", "跑步健走", R.drawable.sport_hiking),
        Sport("CYCLING", "骑行", "户外骑行", R.drawable.sport_cycling),
        Sport("SWIMMING", "游泳", "水上运动"),
        Sport("CARDIO", "有氧运动", "有氧运动"),
        Sport("STRENGTH", "力量训练", "肌力训练"),
    )

@Composable
internal fun WorkoutRecordScreen(
    state: EditorState,
    model: RecordEditorViewModel,
    onBack: () -> Unit,
) {
    val d = state.draft
    var editing by rememberSaveable { mutableStateOf(d.id == null) }
    var selecting by rememberSaveable { mutableStateOf(d.id == null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("跑步健走") }
    var pending by remember { mutableStateOf<WorkoutSelection?>(null) }
    var index by remember { mutableStateOf<Int?>(null) }
    var date by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val time = OffsetDateTime.parse(d.time).atZoneSameInstant(ZoneId.systemDefault())
    val back = {
        if (!state.saving) {
            if (!selecting && d.id == null) selecting = true
            else if (state.dirty) leave = true else onBack()
        }
    }
    BackHandler { back() }
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
                    if (d.id == null) {
                        Text(
                            "已选 ${state.workouts.size} 项 · ${state.workouts.sumOf { it.minutes.toIntOrNull() ?: 0 }} 分钟",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "消耗未知时保留为空",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FitSaveButton(
                        state.saving,
                        {
                            when {
                                !editing -> editing = true
                                d.id != null -> model.save()
                                selecting -> selecting = false
                                else -> model.saveWorkouts()
                            }
                        },
                        if (!editing) "编辑运动"
                        else if (d.id != null) "保存修改"
                        else if (selecting) "核对并保存" else if (state.error != null) "重新保存" else "完成记录",
                    )
                }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = back,
                    modifier = Modifier.width(32.dp).semantics { contentDescription = "返回" },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("‹", fontSize = 28.sp)
                }
                Text(
                    if (d.id != null) "运动记录详情"
                    else if (selecting) "选择运动" else "已选运动 · ${state.workouts.size} 项",
                    Modifier.weight(1f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (selecting)
                    TextButton(
                        onClick = {
                            index = null
                            pending = WorkoutSelection("OTHER", "")
                        },
                        enabled = !state.saving,
                    ) {
                        Text("自定义", fontSize = 12.sp)
                    }
            }
            Text(
                if (selecting) "先选运动，再填写实际时长" else "核对实际完成的运动，确认后保存",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { date = true }, enabled = editing && !state.saving) {
                    Text(time.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                }
                TextButton(onClick = { clock = true }, enabled = editing && !state.saving) {
                    Text(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.loadingFailed -> FitMessage("运动记录读取失败", model::load, true)
                else -> {
                    state.error?.let { FitMessage(it, error = true) }
                    when {
                        d.id != null -> {
                            FitCard {
                                SportRow(
                                    d.name,
                                    "${d.duration} 分钟 · ${d.calories.ifBlank { "—" }} kcal",
                                    sports.find { it.name == d.name }?.image,
                                )
                                if (editing) {
                                    OutlinedButton(
                                        onClick = {
                                            pending =
                                                WorkoutSelection(
                                                    d.type,
                                                    d.name,
                                                    d.duration,
                                                    d.calories,
                                                    d.note,
                                                )
                                        },
                                        enabled = !state.saving,
                                    ) {
                                        Text("修改运动")
                                    }
                                }
                                Text(
                                    "消耗 · ${d.calories.ifBlank { "—" }} kcal",
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                if (d.note.isNotBlank()) Text(d.note)
                                d.exercises?.forEach { exercise ->
                                    Text(exercise.name, fontWeight = FontWeight.Bold)
                                    exercise.sets.forEachIndexed { i, set ->
                                        Text(
                                            "第 ${i + 1} 组 · ${set.reps} ${if (exercise.unit == "seconds") "秒" else "次"} · ${set.weightKg.number()} kg",
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { deleting = true }, enabled = !state.saving) {
                                Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        selecting -> {
                            FitTextField(
                                query,
                                { query = it },
                                Modifier.fillMaxWidth(),
                                placeholder = { Text("搜索运动名称") },
                                singleLine = true,
                                enabled = !state.saving,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(
                                    Modifier.width(80.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(14.dp),
                                        )
                                ) {
                                    sports
                                        .map { it.category }
                                        .distinct()
                                        .forEach { label ->
                                            Text(
                                                label,
                                                Modifier.fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable(enabled = !state.saving) {
                                                        category = label
                                                    }
                                                    .background(
                                                        if (category == label)
                                                            MaterialTheme.colorScheme.surface
                                                        else
                                                            MaterialTheme.colorScheme
                                                                .primaryContainer
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 16.dp)
                                                    .semantics { selected = category == label },
                                                fontSize = 12.sp,
                                                color =
                                                    if (category == label)
                                                        MaterialTheme.colorScheme.secondary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    val filtered =
                                        sports.filter {
                                            if (query.isBlank()) it.category == category
                                            else it.name.contains(query.trim(), true)
                                        }
                                    if (filtered.isEmpty())
                                        Text("没有找到相关运动，可使用自定义记录", fontSize = 13.sp)
                                    filtered.forEach { sport ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(enabled = !state.saving) {
                                                    index = null
                                                    pending =
                                                        WorkoutSelection(sport.type, sport.name)
                                                }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                SportRow(sport.name, "消耗待填写", sport.image)
                                            }
                                            Text(
                                                "＋",
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontSize = 20.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            if (state.workouts.isEmpty()) FitMessage("还没有添加运动，返回选择后再保存")
                            state.workouts.forEachIndexed { i, item ->
                                FitCard {
                                    SportRow(
                                        item.name,
                                        "${item.minutes} 分钟 · ${item.calories.ifBlank { "—" }} kcal",
                                        sports.find { it.name == item.name }?.image,
                                    )
                                    Row {
                                        TextButton(
                                            onClick = {
                                                index = i
                                                pending = item
                                            },
                                            enabled = !state.saving,
                                        ) {
                                            Text("修改")
                                        }
                                        TextButton(
                                            onClick = { model.removeWorkout(i) },
                                            enabled = !state.saving,
                                        ) {
                                            Text("移除")
                                        }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { selecting = true },
                                enabled = !state.saving,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("继续添加运动")
                            }
                            Text(
                                "合计 ${state.workouts.sumOf { it.minutes.toIntOrNull() ?: 0 }} 分钟",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
    pending?.let { item ->
        WorkoutInputSheet(
            item,
            { pending = null },
            { next ->
                if (d.id != null)
                    model.edit {
                        it.copy(
                            type = next.type,
                            name = next.name,
                            duration = next.minutes,
                            calories = next.calories,
                            note = next.note,
                        )
                    }
                else model.selectWorkout(next, index)
                pending = null
            },
        )
    }
    if (date)
        RecordDateDialog(time.toLocalDate(), { date = false }) { value ->
            model.edit {
                it.copy(
                    time =
                        value
                            .atTime(time.toLocalTime())
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                )
            }
            date = false
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
    if (leave)
        FitDialog(
            onDismissRequest = { leave = false },
            title = { Text("放弃本次修改？") },
            text = { Text("未保存的运动会丢失。") },
            confirmButton = { FitDialogButton(onClick = onBack) { Text("放弃") } },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { leave = false }) { Text("继续记录") }
            },
        )
    if (deleting)
        FitDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除运动记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                FitDialogButton(
                    destructive = true,
                    onClick = {
                        deleting = false
                        model.delete()
                    },
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { deleting = false }) { Text("取消") }
            },
        )
}

@Composable
private fun SportRow(name: String, subtitle: String, image: Int?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (image != null)
            Image(
                painterResource(image),
                null,
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutInputSheet(
    item: WorkoutSelection,
    dismiss: () -> Unit,
    accept: (WorkoutSelection) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(item.minutes) }
    var name by rememberSaveable { mutableStateOf(item.name) }
    var calories by rememberSaveable { mutableStateOf(item.calories) }
    var note by rememberSaveable { mutableStateOf(item.note) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("填写运动时长", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            FitTextField(
                name,
                { name = it },
                Modifier.fillMaxWidth(),
                label = { Text("运动名称") },
                singleLine = true,
            )
            Text(
                value.ifBlank { "—" },
                Modifier.align(Alignment.CenterHorizontally),
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Text("分钟", Modifier.align(Alignment.CenterHorizontally), fontSize = 12.sp)
            listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("清空", "0", "⌫"),
                )
                .forEach { keys ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        keys.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    value =
                                        when (key) {
                                            "清空" -> ""
                                            "⌫" -> value.dropLast(1)
                                            else -> (value + key).take(4)
                                        }
                                    error = null
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(key)
                            }
                        }
                    }
                }
            FitTextField(
                calories,
                { calories = it },
                Modifier.fillMaxWidth(),
                label = { Text("消耗 · kcal（选填）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Text(
                "未知消耗留空，不自动估算",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FitTextField(
                note,
                { note = it },
                Modifier.fillMaxWidth(),
                label = { Text("备注（选填）") },
                maxLines = 3,
            )
            error?.let { FitInlineMessage(it) }
            Button(
                onClick = {
                    val next =
                        item.copy(name = name, minutes = value, calories = calories, note = note)
                    val invalid =
                        validateRecord(
                            RecordDraft(
                                com.fittrace.core.model.RecordKind.WORKOUT,
                                time = OffsetDateTime.now().toString(),
                                name = name,
                                duration = value,
                                calories = calories,
                                note = note,
                            )
                        )
                    if (invalid != null) error = invalid else accept(next)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("加入清单")
            }
            Text(
                "数值确认后加入清单，完成记录时再保存。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
