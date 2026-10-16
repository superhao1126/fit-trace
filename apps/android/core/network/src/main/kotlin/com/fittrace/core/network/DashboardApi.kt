package com.fittrace.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DashboardApi {
    @GET("auth/me")
    suspend fun profile(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<DashboardProfileDto>>

    @GET("dashboard")
    suspend fun overview(
        @Header("Authorization") authorization: String,
        @Query("date") date: String,
        @Query("tzOffset") timezoneOffset: Int,
    ): Response<ApiEnvelope<DashboardDto>>
}

@Serializable
data class DashboardDto(
    val date: String,
    val today: DashboardTodayDto,
    val recentTrend: DashboardTrendDto,
)

@Serializable
data class DashboardTodayDto(
    val bodyRecord: DashboardBodyDto?,
    val calories: Double?,
    val mealCount: Int,
    val workoutCount: Int,
    val workoutMinutes: Int,
    val photoCount: Int,
    val mealPreview: List<DashboardMealDto>,
    val workoutPreview: List<DashboardWorkoutDto>,
    val photoPreview: List<DashboardPhotoDto>,
)

@Serializable
data class DashboardBodyDto(val id: String, val weight: Double, val recordedAt: String)

@Serializable
data class DashboardMealDto(
    val id: String,
    val type: String,
    val recordedAt: String,
    val foodNames: List<String>,
    val totalCalories: Double?,
)

@Serializable
data class DashboardWorkoutDto(
    val id: String,
    val name: String?,
    val type: String,
    val startedAt: String,
    val durationMinutes: Int,
)

@Serializable
data class DashboardPhotoDto(val id: String, val type: String, val recordedAt: String)

@Serializable
data class DashboardTrendDto(
    val from: String,
    val to: String,
    val weightChange: Double?,
    val bodyRecordedDays: Int,
    val mealRecordedDays: Int,
    val workoutCount: Int,
    val workoutMinutes: Int,
)

@Serializable data class DashboardProfileDto(val goal: DashboardGoalDto? = null)

@Serializable data class DashboardGoalDto(val type: String, val targetWeight: Double)
