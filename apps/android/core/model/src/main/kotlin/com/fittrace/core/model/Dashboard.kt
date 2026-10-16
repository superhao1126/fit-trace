package com.fittrace.core.model

data class Dashboard(
    val date: String,
    val weight: Double?,
    val calories: Double?,
    val workoutMinutes: Int,
    val workoutCount: Int,
    val mealCount: Int,
    val photoCount: Int,
    val records: List<DiaryRecord>,
    val trend: DashboardTrend,
    val goal: DashboardGoal? = null,
)

data class DiaryRecord(
    val id: String,
    val kind: RecordKind,
    val title: String,
    val subtitle: String,
    val value: String,
    val recordedAt: String,
)

enum class RecordKind {
    BODY,
    MEAL,
    WORKOUT,
    PHOTO,
}

data class DashboardTrend(
    val from: String,
    val to: String,
    val weightChange: Double?,
    val bodyRecordedDays: Int,
    val mealRecordedDays: Int,
    val workoutCount: Int,
    val workoutMinutes: Int,
)

data class DashboardGoal(val type: String, val targetWeight: Double)
