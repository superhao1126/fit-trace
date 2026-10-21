package com.fittrace.feature.main

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RecordEditorScreen(
    state: EditorState,
    model: RecordEditorViewModel,
    onBack: () -> Unit,
) {
    if (state.draft.kind == RecordKind.BODY) {
        BodyRecordScreen(state, model, onBack)
        return
    }
    if (state.draft.kind == RecordKind.MEAL) {
        MealRecordScreen(state, model, onBack)
        return
    }
    val d = state.draft
    var editing by rememberSaveable { mutableStateOf(d.id == null) }
    var leave by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    fun upload(uri: Uri) {
        reading = true
        scope.launch {
            val result =
                withContext(Dispatchers.IO) { runCatching { readSelectedImage(context, uri) } }
            reading = false
            result.fold(
                { model.upload(it.first, it.second) },
                { model.error(it.message ?: "无法读取照片") },
            )
        }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedUri = it.toString()
                model.edit { draft -> draft.copy(imageUrl = null) }
                upload(it)
            }
        }
    val back = {
        if (!state.saving && !reading) {
            if (state.dirty) leave = true else onBack()
        }
    }
    BackHandler { back() }
    FitPage(
        if (editing) "记录${d.kind.label()}" else "${d.kind.label()}详情",
        back,
        action = {
            if (d.id != null && !state.loading && !state.saving)
                TextButton(
                    onClick = {
                        if (d.kind == RecordKind.PHOTO) delete = true else editing = !editing
                    }
                ) {
                    Text(if (d.kind == RecordKind.PHOTO) "删除" else if (editing) "查看" else "编辑")
                }
        },
    ) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            FitMessage(it, if (d.id != null && state.loadingFailed) model::load else null, true)
        }
        if (!state.loading && !state.loadingFailed) {
            val dateTime = OffsetDateTime.parse(d.time).atZoneSameInstant(ZoneId.systemDefault())
            Row {
                TextButton(onClick = { calendar = true }, enabled = editing && !state.saving) {
                    Text(dateTime.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                }
                TextButton(onClick = { clock = true }, enabled = editing && !state.saving) {
                    Text(dateTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }
            when (d.kind) {
                RecordKind.BODY -> Unit
                RecordKind.MEAL -> Unit
                RecordKind.WORKOUT -> {
                    ChoiceRow(workoutLabels, d.type, editing && !state.saving) { value ->
                        model.edit { it.copy(type = value, name = workoutLabels[value].orEmpty()) }
                    }
                    FitCard {
                        OutlinedTextField(
                            d.name,
                            { value -> model.edit { it.copy(name = value) } },
                            Modifier.fillMaxWidth(),
                            label = { Text("运动名称") },
                            readOnly = !editing,
                            enabled = !state.saving,
                            singleLine = true,
                        )
                        NumberInput("时长 · 分钟", d.duration, editing && !state.saving, true) { value
                            ->
                            model.edit { it.copy(duration = value) }
                        }
                        NumberInput("消耗 · kcal（选填）", d.calories, editing && !state.saving, true) {
                            value ->
                            model.edit { it.copy(calories = value) }
                        }
                    }
                    Text(
                        "消耗未知时留空，不自动估算",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                RecordKind.PHOTO -> {
                    ChoiceRow(photoLabels, d.type, editing && !state.saving) { value ->
                        model.edit { it.copy(type = value) }
                    }
                    FitCard {
                        val preview = selectedUri ?: d.imageUrl
                        if (preview != null)
                            AsyncImage(preview, "所选身材照片", Modifier.fillMaxWidth().height(280.dp))
                        else Text("选择一张照片，记录身体的变化")
                        if (editing) {
                            OutlinedButton(
                                onClick = { picker.launch("image/*") },
                                enabled = !state.saving && !reading,
                            ) {
                                Text(if (preview == null) "从相册选择" else "重新选择")
                            }
                            if (
                                selectedUri != null &&
                                    d.imageUrl == null &&
                                    !state.saving &&
                                    !reading
                            )
                                TextButton(onClick = { upload(selectedUri!!.toUri()) }) {
                                    Text("重试上传")
                                }
                            Text(
                                if (state.saving || reading) "正在上传，请稍候…"
                                else if (d.imageUrl != null) "已上传，保存后加入照片归档"
                                else "支持 JPG / PNG / WebP，最大 6 MB",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (editing || d.note.isNotBlank())
                OutlinedTextField(
                    d.note,
                    { value -> model.edit { it.copy(note = value) } },
                    Modifier.fillMaxWidth(),
                    label = { Text("备注（选填）") },
                    readOnly = !editing,
                    enabled = !state.saving,
                    minLines = 2,
                    maxLines = 5,
                )
            if (editing)
                FitSaveButton(
                    state.saving || reading,
                    model::save,
                    if (d.kind == RecordKind.MEAL) "核对并保存" else "保存记录",
                )
            if (d.id != null)
                TextButton(onClick = { delete = true }, enabled = !state.saving && !reading) {
                    Text("删除这条记录", color = MaterialTheme.colorScheme.error)
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
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("删除这条记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        delete = false
                        model.delete()
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } },
        )
    if (calendar)
        RecordDateDialog(OffsetDateTime.parse(d.time).toLocalDate(), { calendar = false }) { date ->
            model.edit {
                it.copy(
                    time =
                        date
                            .atTime(OffsetDateTime.parse(it.time).toLocalTime())
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                )
            }
            calendar = false
        }
    if (clock)
        RecordTimeDialog(OffsetDateTime.parse(d.time).toLocalTime(), { clock = false }) { time ->
            model.edit {
                it.copy(
                    time =
                        OffsetDateTime.parse(it.time)
                            .toLocalDate()
                            .atTime(time)
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                            .toString()
                )
            }
            clock = false
        }
}

@Composable
internal fun FoodDialog(existing: FoodEntry?, onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.amount.orEmpty()) }
    var calories by remember { mutableStateOf(existing?.calories?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加食物" else "修改食物") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("食物名称") }, singleLine = true)
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("份量（如 1 碗 / 100 g）") },
                    singleLine = true,
                )
                NumberInput("热量 · kcal（选填）", calories, true, true) { calories = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().length !in 1..100 || amount.length > 50)
                        error = "名称需 1–100 字，份量不超过 50 字"
                    else if (calories.isNotBlank() && (calories.toIntOrNull() ?: -1) !in 0..100000)
                        error = "热量应为 0–100000 的整数，未知请留空"
                    else
                        onSave(
                            FoodEntry(name.trim(), amount.ifBlank { null }, calories.toIntOrNull())
                        )
                }
            ) {
                Text("加入清单")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberInput(
    label: String,
    value: String,
    enabled: Boolean,
    integer: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        readOnly = !enabled,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal
            ),
    )
}

@Composable
private fun ChoiceRow(
    options: Map<String, String>,
    selected: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (key, label) ->
            FilterChip(selected == key, { onChange(key) }, { Text(label) }, enabled = enabled)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordDateDialog(date: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    val state =
        rememberDatePickerState(
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <=
                            LocalDate.now()
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onSave(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }
            ) {
                Text("确认")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state, title = { Text("选择记录日期", Modifier.padding(20.dp)) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordTimeDialog(time: LocalTime, onDismiss: () -> Unit, onSave: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(time.hour, time.minute, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录时间") },
        text = { TimeInput(state) },
        confirmButton = {
            TextButton(onClick = { onSave(LocalTime.of(state.hour, state.minute)) }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun RecordKind.label() =
    when (this) {
        RecordKind.BODY -> "身体"
        RecordKind.MEAL -> "饮食"
        RecordKind.WORKOUT -> "运动"
        RecordKind.PHOTO -> "照片"
    }

internal fun String.localTime(): String =
    runCatching {
            OffsetDateTime.parse(this)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
        .getOrDefault(this)
