package com.fittrace.core.domain

import com.fittrace.core.model.*

interface ProfileRepository {
    suspend fun profile(): AppResult<UserProfile>

    suspend fun latestBody(): AppResult<ProfileBody?>

    suspend fun updateProfile(input: ProfileUpdate): AppResult<UserProfile>

    suspend fun updateGoal(input: GoalUpdate): AppResult<UserProfile>
}
