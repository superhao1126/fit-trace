package com.fittrace.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

interface ProfileApi {
    @GET("auth/me")
    suspend fun profile(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<ProfileDto>>

    @GET("body-records/latest")
    suspend fun latestBody(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<ProfileBodyDto>>

    @PATCH("users/me/profile")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body input: ProfileUpdateDto,
    ): Response<ApiEnvelope<ProfileDto>>

    @PATCH("users/me/profile")
    suspend fun updateProfileWithAvatar(
        @Header("Authorization") authorization: String,
        @Body input: ProfileWithAvatarUpdateDto,
    ): Response<ApiEnvelope<ProfileDto>>

    @PATCH("users/me/goal")
    suspend fun updateGoal(
        @Header("Authorization") authorization: String,
        @Body input: GoalUpdateDto,
    ): Response<ApiEnvelope<ProfileDto>>
}

@Serializable
data class ProfileDto(
    val id: String,
    val email: String,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val gender: String = "UNSPECIFIED",
    val createdAt: String,
    val goal: FitnessGoalDto? = null,
)

@Serializable
data class FitnessGoalDto(
    val type: String,
    val startWeight: Double,
    val targetWeight: Double,
    val targetDate: String? = null,
    val startedAt: String,
)

@Serializable data class ProfileBodyDto(val weight: Double, val recordedAt: String)

@Serializable data class ProfileUpdateDto(val nickname: String, val gender: String)

@Serializable
data class ProfileWithAvatarUpdateDto(
    val nickname: String,
    val gender: String,
    val avatarUrl: String?,
)

@Serializable
data class GoalUpdateDto(
    val type: String,
    val targetWeight: Double,
    val targetDate: String? = null,
    val currentWeight: Double? = null,
)
