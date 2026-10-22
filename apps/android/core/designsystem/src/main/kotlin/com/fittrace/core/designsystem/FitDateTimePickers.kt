package com.fittrace.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 年月日联动滚轮，只展示业务允许的日期；不打开软键盘。 */
@Composable
fun FitDatePickerDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate) -> Unit,
    title: String = "选择记录日期",
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    val lower = minDate ?: LocalDate.of(minOf(1900, date.year), 1, 1)
    val upper = maxDate ?: LocalDate.of(maxOf(2100, date.year), 12, 31)
    var selectedText by rememberSaveable { mutableStateOf(date.coerceIn(lower, upper).toString()) }
    val selected = LocalDate.parse(selectedText).coerceIn(lower, upper)
    val scrolling = remember { mutableStateMapOf<String, Boolean>() }
    val busy = scrolling.values.any { it }
    fun choose(
        year: Int = selected.year,
        month: Int = selected.monthValue,
        day: Int = selected.dayOfMonth,
    ) {
        selectedText =
            LocalDate.of(year, month, day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))
                .coerceIn(lower, upper)
                .toString()
    }
    val months =
        (if (selected.year == lower.year) lower.monthValue else 1)..(if (
                selected.year == upper.year
            )
                upper.monthValue
            else 12)
    val days =
        (if (YearMonth.from(selected) == YearMonth.from(lower)) lower.dayOfMonth else 1)..(if (
                YearMonth.from(selected) == YearMonth.from(upper)
            )
                upper.dayOfMonth
            else selected.lengthOfMonth())
    FitDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("上下滑动选择日期", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateTimeWheel(
                        (lower.year..upper.year).toList(),
                        selected.year,
                        "年份",
                        "年",
                        { choose(year = it) },
                        { scrolling["year"] = it },
                        Modifier.weight(1.3f),
                    )
                    DateTimeWheel(
                        months.toList(),
                        selected.monthValue,
                        "月份",
                        "月",
                        { choose(month = it) },
                        { scrolling["month"] = it },
                        Modifier.weight(1f),
                    )
                    DateTimeWheel(
                        days.toList(),
                        selected.dayOfMonth,
                        "日期",
                        "日",
                        { choose(day = it) },
                        { scrolling["day"] = it },
                        Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            FitDialogButton(onClick = { onSave(selected) }, enabled = !busy) { Text("确认") }
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
    var hour by rememberSaveable { mutableIntStateOf(time.hour) }
    var minute by rememberSaveable { mutableIntStateOf(time.minute) }
    val scrolling = remember { mutableStateMapOf<String, Boolean>() }
    val busy = scrolling.values.any { it }
    FitDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("上下滑动选择时间 · 24 小时制", style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DateTimeWheel(
                        (0..23).toList(),
                        hour,
                        "小时",
                        "时",
                        { hour = it },
                        { scrolling["hour"] = it },
                        Modifier.weight(1f),
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    DateTimeWheel(
                        (0..59).toList(),
                        minute,
                        "分钟",
                        "分",
                        { minute = it },
                        { scrolling["minute"] = it },
                        Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            FitDialogButton(onClick = { onSave(LocalTime.of(hour, minute)) }, enabled = !busy) {
                Text("确认")
            }
        },
        dismissButton = { FitDialogButton(onClick = onDismiss, primary = false) { Text("取消") } },
    )
}

@Composable
private fun DateTimeWheel(
    values: List<Int>,
    value: Int,
    label: String,
    unit: String,
    onValueChange: (Int) -> Unit,
    onScrolling: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(unit, style = MaterialTheme.typography.labelMedium)
        FitWheelPicker(values, value, label, unit, onValueChange, onScrolling)
    }
}
