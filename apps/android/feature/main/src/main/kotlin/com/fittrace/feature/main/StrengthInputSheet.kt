package com.fittrace.feature.main

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StrengthInputSheet(
    exercise: CatalogExercise,
    dismiss: () -> Unit,
    accept: (Int, Double, Int) -> Unit,
) {
    var field by rememberSaveable { mutableIntStateOf(0) }
    var weight by rememberSaveable { mutableStateOf("0") }
    var reps by rememberSaveable { mutableStateOf(if (exercise.unit == "seconds") "20" else "10") }
    var groups by rememberSaveable { mutableStateOf("3") }
    var fresh by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    fun input(key: String) {
        val old = listOf(weight, reps, groups)[field]
        val next =
            when (key) {
                "⌫" -> old.dropLast(1)
                "." ->
                    if (field != 0 || old.contains('.')) old
                    else if (fresh || old.isBlank()) "0." else "$old."
                else -> if (fresh || old == "0") key else (old + key).take(if (field == 0) 7 else 4)
            }
        when (field) {
            0 -> weight = next
            1 -> reps = next
            else -> groups = next
        }
        fresh = false
        error = null
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painterResource(R.drawable.strength_dumbbells),
                    null,
                    Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column {
                    Text(exercise.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "按实际完成情况填写，加入后仍可逐组修改",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                        "重量 kg" to weight,
                        (if (exercise.unit == "seconds") "时长 秒" else "次数") to reps,
                        "组数" to groups,
                    )
                    .forEachIndexed { i, (label, value) ->
                        Column(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (field == i) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    field = i
                                    fresh = true
                                }
                                .semantics {
                                    contentDescription = "输入$label"
                                    selected = field == i
                                }
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                value.ifBlank { "—" },
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color =
                                    if (field == i) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
            }
            listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(".", "0", "⌫"),
                )
                .forEach { keys ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        keys.forEach { key ->
                            FilledTonalButton(
                                onClick = { input(key) },
                                enabled = key != "." || field == 0,
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(key, fontSize = 18.sp)
                            }
                        }
                    }
                }
            error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val w = weight.toDoubleOrNull()
                    val r = reps.toIntOrNull()
                    val g = groups.toIntOrNull()
                    if (
                        w == null ||
                            !w.isFinite() ||
                            w !in 0.0..1000.0 ||
                            !weight.matches(Regex("[0-9]+(\\.[0-9]{1,2})?")) ||
                            r == null ||
                            r !in 1..3600 ||
                            g == null ||
                            g !in 1..20
                    )
                        error = "重量 0–1000 kg（最多两位小数），次数/秒 1–3600，组数 1–20"
                    else accept(r, w, g)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("加入动作清单")
            }
            Text(
                "加入时不自动标记完成，只保存之后勾选的实际完成组。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
