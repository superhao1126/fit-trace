package com.fittrace.core.model

data class BodyEntry(
    val id: String,
    val weight: Double,
    val bodyFat: Double?,
    val waist: Double?,
    val chest: Double?,
    val hip: Double?,
    val recordedAt: String,
    val note: String?,
)

data class BodyInput(
    val weight: Double,
    val bodyFat: Double?,
    val waist: Double?,
    val chest: Double?,
    val hip: Double?,
    val recordedAt: String,
    val note: String?,
)

data class FoodEntry(
    val name: String,
    val amount: String?,
    val calories: Int?,
    val aiGenerated: Boolean = false,
)

data class MealFavorite(val id: String, val name: String, val foods: List<FoodEntry>)

data class FoodRecognition(val provider: String, val foods: List<FoodEntry>)

data class MealEntry(
    val id: String,
    val type: String,
    val recordedAt: String,
    val foods: List<FoodEntry>,
    val totalCalories: Double?,
    val note: String?,
    val imageUrl: String?,
)

data class MealInput(
    val type: String,
    val recordedAt: String,
    val foods: List<FoodEntry>,
    val note: String?,
    val imageUrl: String? = null,
)

data class WorkoutEntry(
    val id: String,
    val type: String,
    val name: String,
    val startedAt: String,
    val durationMinutes: Int,
    val calories: Int?,
    val note: String?,
    val exercises: List<TrainingExercise>? = null,
)

data class WorkoutInput(
    val type: String,
    val name: String,
    val startedAt: String,
    val durationMinutes: Int,
    val calories: Int?,
    val note: String?,
    val exercises: List<TrainingExercise>? = null,
)

data class PhotoEntry(
    val id: String,
    val type: String,
    val imageUrl: String,
    val recordedAt: String,
    val note: String?,
)

data class PhotoInput(
    val type: String,
    val imageUrl: String,
    val recordedAt: String,
    val note: String?,
)

data class TrendPoint(val recordedAt: String, val value: Double)

data class TrendSeries(val points: List<TrendPoint>)

data class BodyTrends(
    val days: Int,
    val from: String,
    val to: String,
    val weight: TrendSeries,
    val bodyFat: TrendSeries,
    val waist: TrendSeries,
)

data class RecordPage<T>(val entries: List<T>, val total: Int)

data class TrainingSet(val reps: Int, val weightKg: Double, val completed: Boolean)

data class TrainingExercise(
    val exerciseId: String,
    val name: String,
    val unit: String,
    val restSeconds: Int,
    val sets: List<TrainingSet>,
)

data class TrainingTemplate(
    val id: String,
    val name: String,
    val durationMinutes: Int,
    val exercises: List<TrainingExercise>,
)

data class TrainingPlanDay(val weekday: Int, val templateId: String?)

data class TrainingPlan(val configured: Boolean, val days: List<TrainingPlanDay>)

/** 同期饮食与运动概览；未知热量保留 null。 */
data class PeriodInsights(
    val days: Int,
    val averageCalories: Double?,
    val mealCount: Int,
    val mealDays: Int,
    val workoutCount: Int,
    val workoutMinutes: Int,
)
