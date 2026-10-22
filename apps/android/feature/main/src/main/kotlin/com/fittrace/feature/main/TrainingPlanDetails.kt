package com.fittrace.feature.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fittrace.core.designsystem.FitCard
import com.fittrace.core.model.TrainingTemplate

/** 计划预览只读取模板；开始训练由独立按钮明确触发。 */
@Composable
internal fun TrainingPlanDetails(template: TrainingTemplate) {
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
        FitCard {
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
                    exercise.sets.all { it.reps == first.reps && it.weightKg == first.weightKg }
            ) {
                Text("每组 ${first.reps} $unit · ${first.weightKg.number()} kg", fontSize = 13.sp)
            } else
                exercise.sets.forEachIndexed { setIndex, set ->
                    Text(
                        "第 ${setIndex + 1} 组：${set.reps} $unit · ${set.weightKg.number()} kg",
                        fontSize = 13.sp,
                    )
                }
        }
    }
}
