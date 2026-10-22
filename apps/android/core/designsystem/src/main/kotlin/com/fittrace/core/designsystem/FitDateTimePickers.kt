package com.fittrace.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 日历与手动录入共用同一份日期校验，业务只指定允许范围。 */
@Composable
fun FitDatePickerDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate) -> Unit,
    title: String = "选择记录日期",
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    var input by rememberSaveable { mutableStateOf(date.toString()) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(date).toString()) }
    var manual by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.parse(monthText)
    val selected = runCatching { LocalDate.parse(input) }.getOrNull()
    fun allowed(value: LocalDate) =
        (minDate == null || value >= minDate) && (maxDate == null || value <= maxDate)
    val valid = selected != null && allowed(selected)
    FitDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${month.year}年${month.monthValue}月",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = {
                            if (manual && selected != null)
                                monthText = YearMonth.from(selected).toString()
                            manual = !manual
                        }
                    ) {
                        Text(if (manual) "日历选择" else "输入日期")
                    }
                }
                if (manual) {
                    FitTextField(
                        input,
                        { input = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("日期 · YYYY-MM-DD") },
                        singleLine = true,
                        isError = !valid,
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = { monthText = month.minusMonths(1).toString() },
                            enabled = minDate == null || month.atDay(1) > minDate,
                            modifier = Modifier.semantics { contentDescription = "上个月" },
                        ) {
                            Text("‹")
                        }
                        Text(
                            input,
                            Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { monthText = month.plusMonths(1).toString() },
                            enabled = maxDate == null || month.atEndOfMonth() < maxDate,
                            modifier = Modifier.semantics { contentDescription = "下个月" },
                        ) {
                            Text("›")
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                            Text(
                                it,
                                Modifier.weight(1f).wrapContentWidth(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    val offset = month.atDay(1).dayOfWeek.value - 1
                    val rows = (offset + month.lengthOfMonth() + 6) / 7
                    repeat(rows) { week ->
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { weekday ->
                                val day = week * 7 + weekday - offset + 1
                                if (day !in 1..month.lengthOfMonth())
                                    Spacer(Modifier.weight(1f).height(44.dp))
                                else {
                                    val value = month.atDay(day)
                                    val active = value == selected
                                    Surface(
                                        Modifier.weight(1f)
                                            .height(44.dp)
                                            .padding(2.dp)
                                            .selectable(
                                                selected = active,
                                                enabled = allowed(value),
                                                role = Role.RadioButton,
                                                onClick = { input = value.toString() },
                                            )
                                            .semantics { contentDescription = value.toString() },
                                        shape =
                                            androidx.compose.foundation.shape.RoundedCornerShape(
                                                12.dp
                                            ),
                                        color =
                                            if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surface,
                                        contentColor =
                                            if (active) MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = if (allowed(value)) 1f else .35f
                                                ),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                day.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!valid)
                    FitInlineMessage(
                        "请选择有效日期${if (minDate != null) "，不早于 $minDate" else ""}${if (maxDate != null) "，不晚于 $maxDate" else ""}"
                    )
            }
        },
        confirmButton = {
            FitDialogButton(
                onClick = { selected?.takeIf(::allowed)?.let(onSave) },
                enabled = valid,
            ) {
                Text("确认")
            }
        },
        dismissButton = { FitDialogButton(onClick = onDismiss, primary = false) { Text("取消") } },
    )
}

@Composable
fun FitTimePickerDialog(
    time: LocalTime,
    onDismiss: () -> Unit,
    onSave: (LocalTime) -> Unit,
    title: String = "记录时间",
) {
    var hour by rememberSaveable { mutableStateOf(time.hour.toString().padStart(2, '0')) }
    var minute by rememberSaveable { mutableStateOf(time.minute.toString().padStart(2, '0')) }
    val h = hour.toIntOrNull()?.takeIf { it in 0..23 }
    val m = minute.toIntOrNull()?.takeIf { it in 0..59 }
    FitDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("24 小时制", style = MaterialTheme.typography.bodySmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FitTextField(
                        hour,
                        { hour = it.filter(Char::isDigit).take(2) },
                        Modifier.weight(1f),
                        label = { Text("时") },
                        singleLine = true,
                        isError = h == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(":")
                    FitTextField(
                        minute,
                        { minute = it.filter(Char::isDigit).take(2) },
                        Modifier.weight(1f),
                        label = { Text("分") },
                        singleLine = true,
                        isError = m == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                if (h == null || m == null) FitInlineMessage("请输入 00–23 时、00–59 分")
            }
        },
        confirmButton = {
            FitDialogButton(
                onClick = { if (h != null && m != null) onSave(LocalTime.of(h, m)) },
                enabled = h != null && m != null,
            ) {
                Text("确认")
            }
        },
        dismissButton = { FitDialogButton(onClick = onDismiss, primary = false) { Text("取消") } },
    )
}
