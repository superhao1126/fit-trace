package com.fittrace.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*
import kotlinx.coroutines.delay

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
    var libraryFromTools by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<TrainingTemplate?>(null) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val back = {
        if (!state.saving) {
            if (page == "template-editor") {
                model.cancelTemplate()
                page = "templates"
            } else if (page == "session" && state.exercises.isNotEmpty()) leave = true
            else if (page != "tools") page = "tools" else onBack()
        }
    }
    LaunchedEffect(state.editingTemplate, page) {
        if (page == "template-editor" && !state.editingTemplate) page = "templates"
    }
    BackHandler { back() }
    TrainingPage(
        when (page) {
            "session" -> "力量训练"
            "template-editor" -> "编辑训练模板"
            "templates" -> "我的模板"
            "plan" -> "每周计划"
            else -> "运动与训练"
        },
        back,
        if (page == "session" || page == "template-editor")
            ({
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "已选 ${state.exercises.size} 个动作 · ${state.exercises.sumOf { it.sets.size }} 组",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    FitSaveButton(
                        state.saving,
                        { if (page == "template-editor") model.save(true) else finish = true },
                        if (page == "template-editor") "保存模板" else "完成并核对",
                    )
                }
            })
        else null,
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
                    onClick = {
                        libraryFromTools = true
                        library = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("动作库")
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
            "session",
            "template-editor" -> {
                if (state.restored && !state.editingTemplate)
                    Text("已恢复本机草稿", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                OutlinedTextField(
                    state.name,
                    model::name,
                    Modifier.fillMaxWidth(),
                    label = { Text(if (state.editingTemplate) "模板名称" else "训练名称") },
                    singleLine = true,
                    enabled = !state.saving,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    state.minutes,
                    model::minutes,
                    Modifier.fillMaxWidth(),
                    label = { Text(if (state.editingTemplate) "预计时长 · 分钟" else "实际训练时长 · 分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !state.saving,
                    shape = RoundedCornerShape(14.dp),
                )
                if (state.exercises.isEmpty()) FitMessage("先选择动作，再填写实际完成的组")
                state.exercises.forEachIndexed { exerciseIndex, exercise ->
                    FitCard {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${exercise.sets.size} 组 · 已完成 ${exercise.sets.count { it.completed }} 组",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        exercise.sets.forEachIndexed { i, set ->
                            Text(
                                "${i + 1}. ${set.weightKg.number()} kg × ${set.reps} ${if (exercise.unit == "seconds") "秒" else "次"}${if (set.completed) " · 已完成" else ""}",
                                fontSize = 13.sp,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = { editing = exerciseIndex },
                                enabled = !state.saving,
                            ) {
                                Text("修改组数")
                            }
                            TextButton(
                                onClick = { model.remove(exerciseIndex) },
                                enabled = !state.saving,
                            ) {
                                Text("移除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!state.editingTemplate)
                            TextButton(
                                onClick = { model.startRest(exerciseIndex) },
                                enabled = !state.saving,
                            ) {
                                Text("休息 ${exercise.restSeconds} 秒")
                            }
                    }
                }
                if (!state.editingTemplate && state.restUntil != null)
                    RestTimer(state.restUntil, model::extendRest, model::stopRest)
                OutlinedButton(
                    onClick = { library = true },
                    enabled = !state.saving && state.exercises.size < 20,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("＋ 添加动作")
                }
                Text(
                    if (state.editingTemplate) "模板保留全部计划组，使用模板时重新勾选完成。" else "只保存已完成的组；未完成组不会进入记录。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.editingTemplate)
                    TextButton(onClick = { model.save(true) }, enabled = !state.saving) {
                        Text("将当前清单保存为模板")
                    }
            }
            "templates" -> {
                OutlinedButton(
                    onClick = {
                        model.beginTemplate()
                        page = "template-editor"
                    },
                    enabled = !state.saving && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("创建我的模板")
                }
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
                                if (state.exercises.isNotEmpty()) replacing = template
                                else {
                                    model.use(template)
                                    page = "session"
                                }
                            },
                            enabled = !state.saving,
                        ) {
                            Text("使用模板")
                        }
                        TextButton(
                            onClick = {
                                model.beginTemplate(template)
                                page = "template-editor"
                            },
                            enabled = !state.saving,
                        ) {
                            Text("编辑模板")
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
        ExerciseSheet(state.exercises.map { it.exerciseId }.toSet(), { library = false }) {
            if (libraryFromTools) {
                model.beginTemplate()
                page = "template-editor"
                libraryFromTools = false
            }
            model.add(it)
        }
    editing?.let { ei ->
        state.exercises.getOrNull(ei)?.let { exercise ->
            StrengthSetsSheet(
                exercise,
                !state.editingTemplate,
                state.previousLoading,
                { editing = null },
                { i, set -> model.set(ei, i, set) },
                { model.addSet(ei) },
                { model.removeSet(ei, it) },
                { model.bringPrevious(ei) },
                { model.restSeconds(ei, it) },
                {
                    if (exercise.sets.any { it.completed } && !state.editingTemplate)
                        model.startRest(ei)
                    editing = null
                },
            )
        }
    }
    replacing?.let { template ->
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text("替换当前训练清单？") },
            text = { Text("使用模板会替换当前草稿中的动作和组。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.use(template)
                        page = "session"
                        replacing = null
                    }
                ) {
                    Text("使用模板")
                }
            },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text("取消") } },
        )
    }
    if (leave)
        AlertDialog(
            onDismissRequest = { leave = false },
            title = { Text("离开训练？") },
            text = {
                Column {
                    Text("已保留本机草稿，下次可继续本日期的训练。")
                    TextButton(
                        onClick = {
                            model.discard()
                            leave = false
                            page = "tools"
                        }
                    ) {
                        Text("放弃本次草稿", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
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
private fun ExerciseSheet(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (CatalogExercise) -> Unit,
) {
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
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.width(76.dp)
                        .verticalScroll(rememberScrollState())
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(14.dp),
                        )
                ) {
                    (listOf("全部") + exercises.map { it.bodyPart }.distinct()).forEach { part ->
                        Text(
                            part,
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { category = part }
                                .background(
                                    if (category == part) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .padding(horizontal = 10.dp, vertical = 16.dp),
                            fontSize = 12.sp,
                            color =
                                if (category == part) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val filtered =
                    exercises.filter {
                        (category == "全部" || it.bodyPart == category) &&
                            (it.name.contains(search) || it.equipment.contains(search))
                    }
                LazyColumn(Modifier.weight(1f)) {
                    if (filtered.isEmpty()) item { Text("没有找到相关动作，请换个关键词", fontSize = 12.sp) }
                    items(filtered) { exercise ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { detail = exercise }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Image(
                                painterResource(R.drawable.strength_dumbbells),
                                null,
                                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    exercise.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${exercise.bodyPart} · ${exercise.equipment}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { onAdd(exercise) },
                                enabled = exercise.id !in selected,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.width(32.dp),
                            ) {
                                Text(if (exercise.id in selected) "✓" else "＋")
                            }
                        }
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("完成选择 · ${selected.size} 个动作")
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

@Composable
private fun TrainingPage(
    title: String,
    back: () -> Unit,
    footer: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (footer != null) Surface { footer() } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = back,
                    modifier = Modifier.width(32.dp).semantics { contentDescription = "返回" },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("‹", fontSize = 28.sp)
                }
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun RestTimer(until: Long, extend: () -> Unit, stop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (now < until) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val seconds = ((until - now + 999) / 1000).coerceAtLeast(0)
    FitCard {
        Text(
            if (seconds == 0L) "休息结束"
            else "组间休息 · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Row {
            TextButton(onClick = extend) { Text("＋30 秒") }
            TextButton(onClick = stop) { Text("结束休息") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrengthSetsSheet(
    exercise: TrainingExercise,
    session: Boolean,
    previousLoading: Boolean,
    dismiss: () -> Unit,
    change: (Int, TrainingSet) -> Unit,
    add: () -> Unit,
    remove: (Int) -> Unit,
    previous: () -> Unit,
    rest: (Int) -> Unit,
    confirm: () -> Unit,
) {
    var input by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painterResource(R.drawable.strength_dumbbells),
                    null,
                    Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
                Text(exercise.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                if (exercise.unit == "seconds") "按秒记录实际时长" else "按单只重量记录",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("组", Modifier.width(24.dp), fontSize = 12.sp)
                Text(
                    "重量 kg / ${if (exercise.unit == "seconds") "秒" else "次数"}",
                    Modifier.weight(1f),
                    fontSize = 12.sp,
                )
                if (session) Text("完成", fontSize = 12.sp)
            }
            exercise.sets.forEachIndexed { i, set ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(24.dp))
                    FilledTonalButton(
                        onClick = { input = i },
                        Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "${set.weightKg.number()} kg × ${set.reps} ${if (exercise.unit == "seconds") "秒" else "次"}"
                        )
                    }
                    if (session)
                        Checkbox(
                            set.completed,
                            { change(i, set.copy(completed = it)) },
                            modifier = Modifier.semantics { contentDescription = "完成第 ${i + 1} 组" },
                        )
                    if (exercise.sets.size > 1)
                        TextButton(
                            onClick = { remove(i) },
                            modifier = Modifier.width(40.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("－")
                        }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = add, enabled = exercise.sets.size < 20) { Text("＋ 添加一组") }
                if (session)
                    TextButton(onClick = previous, enabled = !previousLoading) {
                        Text(if (previousLoading) "正在读取…" else "带入上次")
                    }
            }
            if (session) {
                Text("组间休息", fontSize = 13.sp)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0, 30, 60, 90, 120).forEach { seconds ->
                        FilterChip(
                            exercise.restSeconds == seconds,
                            { rest(seconds) },
                            { Text(if (seconds == 0) "不休息" else "${seconds}秒") },
                        )
                    }
                }
            }
            Text(
                if (session) "未勾选的组不会写入正式记录。" else "模板中的组不会自动标记完成。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = confirm, Modifier.fillMaxWidth().height(48.dp)) { Text("确认修改") }
        }
    }
    input?.let { i ->
        exercise.sets.getOrNull(i)?.let { set ->
            TrainingSetDialog(exercise.unit, set, { input = null }) {
                change(i, it)
                input = null
            }
        }
    }
}
