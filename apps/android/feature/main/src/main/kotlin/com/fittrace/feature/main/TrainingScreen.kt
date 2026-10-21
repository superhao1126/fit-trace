package com.fittrace.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*

@Composable
internal fun TrainingScreen(
    state: TrainingState,
    model: TrainingViewModel,
    onBack: () -> Unit,
    onQuick: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("tools") }
    var library by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val back = {
        if (!state.saving) {
            if (page == "session" && state.exercises.isNotEmpty()) leave = true
            else if (page != "tools") page = "tools" else onBack()
        }
    }
    BackHandler { back() }
    FitPage(
        when (page) {
            "session" -> "力量训练"
            "templates" -> "训练模板"
            "plan" -> "每周计划"
            else -> "运动与训练"
        },
        back,
    ) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { FitMessage(it, if (page != "session") model::load else null, true) }
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        when (page) {
            "tools" -> {
                FitCard(Modifier.clickable { onQuick() }) {
                    Text("快速记录运动", style = MaterialTheme.typography.titleMedium)
                    Text("跑步、骑行、游泳或其他运动")
                }
                FitCard(Modifier.clickable { page = "session" }) {
                    Text(
                        if (state.exercises.isEmpty()) "开始力量训练" else "继续当前训练",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("按动作记录次数、重量和完成组")
                }
                OutlinedButton(
                    onClick = { page = "templates" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("训练模板")
                }
                OutlinedButton(onClick = { page = "plan" }, modifier = Modifier.fillMaxWidth()) {
                    Text("每周计划")
                }
            }
            "session" -> {
                OutlinedTextField(
                    state.name,
                    model::name,
                    Modifier.fillMaxWidth(),
                    label = { Text("训练名称") },
                    singleLine = true,
                    enabled = !state.saving,
                )
                OutlinedTextField(
                    state.minutes,
                    model::minutes,
                    Modifier.fillMaxWidth(),
                    label = { Text("实际训练时长 · 分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !state.saving,
                )
                state.exercises.forEachIndexed { exerciseIndex, exercise ->
                    FitCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                exercise.name,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            TextButton(
                                onClick = { model.remove(exerciseIndex) },
                                enabled = !state.saving,
                            ) {
                                Text("移除")
                            }
                        }
                        Text(
                            "组数 · ${if(exercise.unit == "seconds") "秒" else "次数"} · 重量 kg",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        exercise.sets.forEachIndexed { setIndex, set ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${setIndex + 1}", Modifier.width(24.dp))
                                TextButton(
                                    onClick = { editing = exerciseIndex to setIndex },
                                    enabled = !state.saving,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        "${set.reps} ${if(exercise.unit == "seconds") "秒" else "次"} · ${set.weightKg.number()} kg"
                                    )
                                }
                                Checkbox(
                                    set.completed,
                                    {
                                        model.set(exerciseIndex, setIndex, set.copy(completed = it))
                                    },
                                    enabled = !state.saving,
                                )
                                if (exercise.sets.size > 1)
                                    TextButton(
                                        onClick = { model.removeSet(exerciseIndex, setIndex) },
                                        enabled = !state.saving,
                                    ) {
                                        Text("－")
                                    }
                            }
                        }
                        TextButton(
                            onClick = { model.addSet(exerciseIndex) },
                            enabled = !state.saving && exercise.sets.size < 20,
                        ) {
                            Text("＋ 添加一组")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { library = true },
                    enabled = !state.saving && state.exercises.size < 20,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("＋ 添加动作")
                }
                Text(
                    "只保存已完成的组；未完成组不会进入记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FitSaveButton(state.saving, { finish = true }, "完成并核对")
                TextButton(onClick = { model.save(true) }, enabled = !state.saving) {
                    Text("将当前清单保存为模板")
                }
            }
            "templates" -> {
                if (!state.loading && state.templates.isEmpty() && state.error == null)
                    FitMessage("还没有训练模板，先添加动作并保存为模板。")
                state.templates.forEach { template ->
                    FitCard {
                        Text(template.name, style = MaterialTheme.typography.titleMedium)
                        Text("${template.durationMinutes} 分钟 · ${template.exercises.size} 个动作")
                        Text(
                            template.exercises.joinToString("、") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = {
                                model.use(template)
                                page = "session"
                            },
                            enabled = !state.saving,
                        ) {
                            Text("使用模板")
                        }
                    }
                }
            }
            "plan" -> {
                Text("为每一天选择模板，也可以安排休息。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.plan
                    .sortedBy { it.weekday }
                    .forEach { day ->
                        var expanded by remember { mutableStateOf(false) }
                        FitCard {
                            Text("周${listOf("日", "一", "二", "三", "四", "五", "六")[day.weekday]}")
                            Box {
                                TextButton(
                                    onClick = { expanded = true },
                                    enabled = !state.loading && !state.saving,
                                ) {
                                    Text(
                                        state.templates.find { it.id == day.templateId }?.name
                                            ?: "休息"
                                    )
                                }
                                DropdownMenu(expanded, { expanded = false }) {
                                    DropdownMenuItem(
                                        { Text("休息") },
                                        {
                                            model.planDay(day.weekday, null)
                                            expanded = false
                                        },
                                    )
                                    state.templates.forEach { template ->
                                        DropdownMenuItem(
                                            { Text(template.name) },
                                            {
                                                model.planDay(day.weekday, template.id)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                FitSaveButton(state.saving, model::savePlan, "保存每周计划")
            }
        }
    }
    if (library)
        ExerciseSheet({ library = false }) {
            model.add(it)
            library = false
        }
    editing?.let { (ei, si) ->
        val exercise = state.exercises.getOrNull(ei)
        val set = exercise?.sets?.getOrNull(si)
        if (exercise != null && set != null)
            TrainingSetDialog(exercise.unit, set, { editing = null }) {
                model.set(ei, si, it)
                editing = null
            }
    }
    if (leave)
        AlertDialog(
            onDismissRequest = { leave = false },
            title = { Text("离开训练？") },
            text = { Text("当前清单保留在本次应用会话中；关闭应用后可能丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        leave = false
                        page = "tools"
                    }
                ) {
                    Text("返回工具")
                }
            },
            dismissButton = { TextButton(onClick = { leave = false }) { Text("继续训练") } },
        )
    if (finish)
        AlertDialog(
            onDismissRequest = { finish = false },
            title = { Text("核对训练记录") },
            text = {
                Text(
                    "共 ${state.exercises.sumOf { it.sets.count { set -> set.completed } }} 组已完成，训练时长 ${state.minutes.ifBlank { "未填写" }} 分钟。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        finish = false
                        model.save()
                    }
                ) {
                    Text("保存记录")
                }
            },
            dismissButton = { TextButton(onClick = { finish = false }) { Text("继续训练") } },
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseSheet(onDismiss: () -> Unit, onAdd: (CatalogExercise) -> Unit) {
    val context = LocalContext.current
    val exercises = remember { catalogExercises(context) }
    var category by remember { mutableStateOf("全部") }
    var search by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<CatalogExercise?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("动作库", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                search,
                { search = it },
                Modifier.fillMaxWidth(),
                label = { Text("搜索动作或器械") },
                singleLine = true,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf("全部") + exercises.map { it.bodyPart }.distinct()).forEach { part ->
                    FilterChip(category == part, { category = part }, { Text(part) })
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(
                    exercises.filter {
                        (category == "全部" || it.bodyPart == category) &&
                            (it.name.contains(search) || it.equipment.contains(search))
                    }
                ) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text(
                                "${exercise.bodyPart} · ${exercise.equipment} · ${if(exercise.unit == "seconds") "按秒" else "按次"}"
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { onAdd(exercise) }) { Text("添加") }
                        },
                        modifier = Modifier.clickable { detail = exercise },
                    )
                }
            }
        }
    }
    detail?.let { exercise ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(exercise.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    exercise.steps.forEachIndexed { i, step -> Text("${i+1}. $step") }
                    Text(exercise.tips)
                }
            },
            confirmButton = { TextButton(onClick = { onAdd(exercise) }) { Text("加入训练") } },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("返回") } },
        )
    }
}

@Composable
private fun TrainingSetDialog(
    unit: String,
    set: TrainingSet,
    onDismiss: () -> Unit,
    onSave: (TrainingSet) -> Unit,
) {
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var weight by remember { mutableStateOf(set.weightKg.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录这一组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    reps,
                    { reps = it },
                    label = { Text(if (unit == "seconds") "时长 · 秒" else "次数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    weight,
                    { weight = it },
                    label = { Text("重量 · kg（徒手填 0）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val r = reps.toIntOrNull()
                    val w = weight.toDoubleOrNull()
                    if (
                        r == null ||
                            r !in 1..3600 ||
                            w == null ||
                            !w.isFinite() ||
                            w !in 0.0..1000.0 ||
                            !weight.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))
                    )
                        error = "次数/秒为 1–3600，重量 0–1000 kg，最多两位小数"
                    else onSave(set.copy(reps = r, weightKg = w))
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
