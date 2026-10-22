package com.fittrace.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.*
import com.fittrace.core.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyRecordScreen(
    state: EditorState,
    model: RecordEditorViewModel,
    onBack: () -> Unit,
) {
    val draft = state.draft
    var editing by rememberSaveable { mutableStateOf(draft.id == null) }
    var weightInput by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val back = {
        if (!state.saving) {
            if (state.dirty) leave = true else onBack()
        }
    }
    BackHandler { back() }
    val time = OffsetDateTime.parse(draft.time).atZoneSameInstant(ZoneId.systemDefault())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!state.loadingFailed && !state.loading)
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .imePadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = { if (editing) model.save() else editing = true },
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                    ) {
                        if (state.saving)
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        else
                            Text(
                                if (!editing) "编辑记录"
                                else if (state.error != null && validateRecord(draft) == null)
                                    "重新保存"
                                else "保存记录",
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
                    .height(156.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (editing) "记录身体数据" else "身体记录详情",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = back,
                        modifier =
                            Modifier.align(Alignment.CenterStart).width(32.dp).semantics {
                                contentDescription = "返回"
                            },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { calendar = true },
                        enabled = editing && !state.saving,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            time.format(DateTimeFormatter.ofPattern("M月d日")),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        " · ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { clock = true },
                        enabled = editing && !state.saving,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            time.format(DateTimeFormatter.ofPattern("HH:mm")) +
                                if (editing) " ⌄" else "",
                            fontSize = 12.sp,
                        )
                    }
                }
                when {
                    state.loading ->
                        InsightsCard {
                            Column(
                                Modifier.height(240.dp).semantics {
                                    contentDescription = "正在加载身体数据"
                                },
                                verticalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                InsightsSkeleton(Modifier.size(110.dp, 44.dp))
                                repeat(3) {
                                    InsightsSkeleton(Modifier.fillMaxWidth().height(20.dp))
                                }
                            }
                        }
                    state.loadingFailed ->
                        InsightsCard {
                            InsightsEmpty("身体数据读取失败", "检查网络后重试", "重新加载", model::load, error = true)
                        }
                    else -> {
                        if (editing) {
                            Text(
                                "当前体重",
                                Modifier.padding(top = 10.dp),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            InsightsCard {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        draft.weight.ifBlank { "—" },
                                        Modifier.clickable(enabled = !state.saving) {
                                                weightInput = true
                                            }
                                            .semantics { contentDescription = "输入体重" },
                                        fontSize = 44.sp,
                                        lineHeight = 54.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    Text(
                                        "kg · 点击数字输入",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    WeightRuler(draft.weight, !state.saving) { value ->
                                        model.edit { it.copy(weight = value) }
                                    }
                                    val previous = state.previousBody
                                    val days =
                                        previous?.let {
                                            ChronoUnit.DAYS.between(
                                                OffsetDateTime.parse(it.recordedAt)
                                                    .atZoneSameInstant(ZoneId.systemDefault())
                                                    .toLocalDate(),
                                                time.toLocalDate(),
                                            )
                                        }
                                    Text(
                                        if (previous != null)
                                            "上次 ${previous.weight.number()} kg · ${if (days == 0L) "同一天" else "${days} 天前"}"
                                        else "滑动标尺微调体重",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            BodyOptionRow(
                                "体脂率 · 选填",
                                draft.bodyFat.ifBlank { "选填" } +
                                    if (draft.bodyFat.isNotBlank()) "% ›" else " ›",
                                !state.saving,
                            ) {
                                more = true
                            }
                            BodyOptionRow(
                                "围度与备注",
                                if (
                                    listOf(draft.waist, draft.chest, draft.hip, draft.note).any {
                                        it.isNotBlank()
                                    }
                                )
                                    "已补充 ›"
                                else "展开 ›",
                                !state.saving,
                            ) {
                                more = true
                            }
                            Text(
                                "体重必填；其余指标留空表示未记录。",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            InsightsCard {
                                BodyDetailRow("体重", "${draft.weight} kg")
                                BodyDetailRow(
                                    "体脂率",
                                    draft.bodyFat.takeIf { it.isNotBlank() }?.let { "$it %" } ?: "—",
                                )
                                BodyDetailRow(
                                    "腰围",
                                    draft.waist.takeIf { it.isNotBlank() }?.let { "$it cm" } ?: "—",
                                )
                                BodyDetailRow(
                                    "胸围",
                                    draft.chest.takeIf { it.isNotBlank() }?.let { "$it cm" } ?: "—",
                                )
                                BodyDetailRow(
                                    "臀围",
                                    draft.hip.takeIf { it.isNotBlank() }?.let { "$it cm" } ?: "—",
                                )
                            }
                            InsightsCard {
                                Text(
                                    "备注",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(draft.note.ifBlank { "—" }, fontSize = 15.sp)
                            }
                        }
                        state.error?.let { FitInlineMessage(it) }
                    }
                }
            }
        }
    }
    if (weightInput)
        WeightInputSheet(draft.weight, { weightInput = false }) { value ->
            model.edit { it.copy(weight = value) }
            weightInput = false
        }
    if (more)
        ModalBottomSheet(onDismissRequest = { more = false }) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("补充身体数据", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                BodySupplementInput("体脂率 · %（选填）", draft.bodyFat) { value ->
                    model.edit { it.copy(bodyFat = value) }
                }
                BodySupplementInput("腰围 · cm（选填）", draft.waist) { value ->
                    model.edit { it.copy(waist = value) }
                }
                BodySupplementInput("胸围 · cm（选填）", draft.chest) { value ->
                    model.edit { it.copy(chest = value) }
                }
                BodySupplementInput("臀围 · cm（选填）", draft.hip) { value ->
                    model.edit { it.copy(hip = value) }
                }
                FitTextField(
                    draft.note,
                    { value -> model.edit { it.copy(note = value) } },
                    Modifier.fillMaxWidth(),
                    label = { Text("备注（选填）") },
                    minLines = 2,
                    maxLines = 4,
                )
                Button(onClick = { more = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("确认补充", color = Color(0xFF092B17))
                }
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
    if (leave)
        FitDialog(
            onDismissRequest = { leave = false },
            title = { Text("放弃本次修改？") },
            text = { Text("未保存的输入会丢失。") },
            confirmButton = {
                FitDialogButton(destructive = true, onClick = onBack) { Text("放弃修改") }
            },
            dismissButton = {
                FitDialogButton(primary = false, onClick = { leave = false }) { Text("继续编辑") }
            },
        )
    if (deleting)
        FitDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "${time.format(DateTimeFormatter.ofPattern("M月d日"))} · ${draft.weight} kg\n删除后无法恢复。"
                )
            },
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
private fun BodyOptionRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp).heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun BodyDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BodySupplementInput(label: String, value: String, onValue: (String) -> Unit) {
    FitTextField(
        value,
        onValue,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun WeightRuler(value: String, enabled: Boolean, onWeight: (String) -> Unit) {
    val latest by rememberUpdatedState(value)
    val update by rememberUpdatedState(onWeight)
    val line = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    var dragging by remember { mutableStateOf(false) }
    var visualWeight by remember { mutableDoubleStateOf(value.toDoubleOrNull() ?: 70.0) }
    LaunchedEffect(value, dragging) { if (!dragging) visualWeight = value.toDoubleOrNull() ?: 70.0 }
    Canvas(
        Modifier.fillMaxWidth()
            .height(82.dp)
            .padding(top = 10.dp, bottom = 4.dp)
            .semantics {
                contentDescription = "体重标尺"
                stateDescription =
                    value.takeIf { it.isNotBlank() }?.let { "当前体重 $it kg" } ?: "滑动选择体重"
                if (enabled)
                    customActions =
                        listOf(
                            CustomAccessibilityAction("增加 0.1 kg") {
                                update(rulerWeight(latest, .1))
                                true
                            },
                            CustomAccessibilityAction("减少 0.1 kg") {
                                update(rulerWeight(latest, -.1))
                                true
                            },
                        )
            }
            .pointerInput(enabled) {
                if (enabled)
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            visualWeight = latest.toDoubleOrNull() ?: 70.0
                        },
                        onDragEnd = {
                            visualWeight = round(visualWeight * 10) / 10
                            update(String.format(Locale.ROOT, "%.1f", visualWeight))
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            visualWeight =
                                (visualWeight - amount / 8.dp.toPx() * .1).coerceIn(1.0, 500.0)
                            update(String.format(Locale.ROOT, "%.1f", visualWeight))
                        },
                    )
            }
    ) {
        val spacing = 8.dp.toPx()
        val selectedTick = visualWeight * 10
        val half = size.width / spacing / 2
        val first = floor(selectedTick - half).toInt().coerceAtLeast(10)
        val last = ceil(selectedTick + half).toInt().coerceAtMost(5000)
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor.toArgb()
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
            }
        for (tick in first..last) {
            val x = (size.width / 2 + (tick - selectedTick) * spacing).toFloat()
            val height =
                if (tick % 10 == 0) 28.dp.toPx()
                else if (tick % 5 == 0) 20.dp.toPx() else 12.dp.toPx()
            drawLine(line, Offset(x, 0f), Offset(x, height), 1.dp.toPx())
            if (tick % 10 == 0)
                drawContext.canvas.nativeCanvas.drawText("${tick / 10}", x, 48.dp.toPx(), paint)
        }
        drawLine(
            primary,
            Offset(size.width / 2, 0f),
            Offset(size.width / 2, 32.dp.toPx()),
            2.dp.toPx(),
        )
    }
}

internal fun rulerWeight(value: String, change: Double) =
    String.format(
        Locale.ROOT,
        "%.1f",
        ((value.toDoubleOrNull() ?: 70.0) + change).coerceIn(1.0, 500.0),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightInputSheet(value: String, onDismiss: () -> Unit, onValue: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf(value) }
    var fresh by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("输入体重 · kg", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                input.ifBlank { "—" },
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (error) FitInlineMessage("请填写 1～500 kg 的体重，最多两位小数")
            listOf(
                    listOf("1", "2", "3", "⌫"),
                    listOf("4", "5", "6", ""),
                    listOf("7", "8", "9", ""),
                    listOf(".", "0", "", ""),
                )
                .forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { key ->
                            if (key.isEmpty()) Spacer(Modifier.weight(1f).height(48.dp))
                            else
                                TextButton(
                                    onClick = {
                                        error = false
                                        if (key == "⌫") {
                                            input = input.dropLast(1)
                                            fresh = false
                                        } else {
                                            val base = if (fresh) "" else input
                                            val next =
                                                if (key == "." && base.isEmpty()) "0."
                                                else base + key
                                            if (
                                                next.length <= 6 &&
                                                    next.count { it == '.' } <= 1 &&
                                                    (!next.contains('.') ||
                                                        next.substringAfter('.').length <= 2)
                                            )
                                                input = next
                                            fresh = false
                                        }
                                    },
                                    modifier =
                                        Modifier.weight(1f)
                                            .height(48.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(12.dp),
                                            ),
                                ) {
                                    Text(
                                        key,
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                        }
                    }
                }
            Button(
                onClick = {
                    val number = input.toDoubleOrNull()
                    if (number != null && number.isFinite() && number in 1.0..500.0)
                        onValue(number.numberForInput())
                    else error = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("完成输入", color = Color(0xFF092B17))
            }
        }
    }
}

private fun Double.numberForInput() =
    java.math.BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
