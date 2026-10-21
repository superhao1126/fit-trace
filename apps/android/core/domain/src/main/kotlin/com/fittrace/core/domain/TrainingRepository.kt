package com.fittrace.core.domain

import com.fittrace.core.model.*

interface TrainingRepository {
    suspend fun templates(): AppResult<List<TrainingTemplate>>

    suspend fun saveTemplate(
        name: String,
        minutes: Int,
        exercises: List<TrainingExercise>,
        id: String? = null,
    ): AppResult<TrainingTemplate>

    suspend fun plan(): AppResult<TrainingPlan>

    suspend fun savePlan(days: List<TrainingPlanDay>): AppResult<List<TrainingPlanDay>>
}
