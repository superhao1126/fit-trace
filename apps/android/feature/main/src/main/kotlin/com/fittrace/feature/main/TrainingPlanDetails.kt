package com.fittrace.feature.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.FitCard
import com.fittrace.core.model.TrainingTemplate

/** 计划预览只读取模板；开始训练由独立按钮明确触发。 */
@Composable
internal fun TrainingPlanDetails(template: TrainingTemplate) {
    val context = LocalContext.current
    val catalog = remember(context) { catalogExercises(context).associateBy { it.id } }
    var tutorial by remember(template.id) { mutableStateOf<CatalogExercise?>(null) }
    Text(template.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(
        "预计 ${template.durationMinutes} 分钟 · ${template.exercises.size} 个动作 · ${template.exercises.sumOf { it.sets.size }} 组",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "开始训练后可按实际情况调整重量和次数。",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    template.exercises.forEachIndexed { index, exercise ->
        val info = catalog[exercise.exerciseId]
        FitCard(
            Modifier.clickable(enabled = info != null) { tutorial = info }
                .semantics { contentDescription = "查看${exercise.name}教程" }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info != null) ExercisePhoto(info, Modifier.size(88.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}. ${exercise.name}", fontWeight = FontWeight.Bold)
                    Text(
                        "${exercise.sets.size} 组 · ${if (exercise.restSeconds == 0) "不设组间休息" else "组间休息 ${exercise.restSeconds} 秒"}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    val unit = if (exercise.unit == "seconds") "秒" else "次"
                    val first = exercise.sets.firstOrNull()
                    if (
                        first != null &&
                            exercise.sets.all {
                                it.reps == first.reps && it.weightKg == first.weightKg
                            }
                    ) {
                        Text(
                            "每组 ${first.reps} $unit · ${first.weightKg.number()} kg",
                            fontSize = 13.sp,
                        )
                    } else
                        exercise.sets.forEachIndexed { setIndex, set ->
                            Text(
                                "第 ${setIndex + 1} 组：${set.reps} $unit · ${set.weightKg.number()} kg",
                                fontSize = 13.sp,
                            )
                        }
                    if (info != null)
                        Text(
                            "查看教程 ›",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                        )
                }
            }
        }
    }
    tutorial?.let { ExerciseTutorial(it, onDismiss = { tutorial = null }) }
}
