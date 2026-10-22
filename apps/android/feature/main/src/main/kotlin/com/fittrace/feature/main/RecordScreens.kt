package com.fittrace.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun RecordEditorScreen(
    state: EditorState,
    model: RecordEditorViewModel,
    onBack: () -> Unit,
) {
    when (state.draft.kind) {
        RecordKind.BODY -> BodyRecordScreen(state, model, onBack)
        RecordKind.MEAL -> MealRecordScreen(state, model, onBack)
        RecordKind.WORKOUT -> WorkoutRecordScreen(state, model, onBack)
        RecordKind.PHOTO -> PhotoRecordScreen(state, model, onBack)
    }
}

@Composable
internal fun FoodDialog(existing: FoodEntry?, onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.amount.orEmpty()) }
    var calories by remember { mutableStateOf(existing?.calories?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    FitDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加食物" else "修改食物") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FitTextField(name, { name = it }, label = { Text("食物名称") }, singleLine = true)
                FitTextField(
                    amount,
                    { amount = it },
                    label = { Text("份量（如 1 碗 / 100 g）") },
                    singleLine = true,
                )
                NumberInput("热量 · kcal（选填）", calories, true, true) { calories = it }
                error?.let { FitInlineMessage(it) }
            }
        },
        confirmButton = {
            FitDialogButton(
                onClick = {
                    if (name.trim().length !in 1..100 || amount.length > 50)
                        error = "名称需 1–100 字，份量不超过 50 字"
                    else if (calories.isNotBlank() && (calories.toIntOrNull() ?: -1) !in 0..100000)
                        error = "热量应为 0–100000 的整数，未知请留空"
                    else
                        onSave(
                            FoodEntry(
                                name.trim(),
                                amount.ifBlank { null },
                                calories.toIntOrNull(),
                                existing?.aiGenerated ?: false,
                            )
                        )
                }
            ) {
                Text("加入清单")
            }
        },
        dismissButton = { FitDialogButton(primary = false, onClick = onDismiss) { Text("取消") } },
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
    FitTextField(
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
internal fun RecordDateDialog(date: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) =
    FitDatePickerDialog(date, onDismiss, onSave, maxDate = LocalDate.now())

@Composable
internal fun RecordTimeDialog(time: LocalTime, onDismiss: () -> Unit, onSave: (LocalTime) -> Unit) =
    FitTimePickerDialog(time, onDismiss, onSave)

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
