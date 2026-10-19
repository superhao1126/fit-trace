package com.fittrace.core.model

data class UserProfile(
    val id: String,
    val email: String,
    val nickname: String?,
    val avatarUrl: String?,
    val gender: UserGender,
    val createdAt: String,
    val goal: FitnessGoal?,
) {
    val displayName: String
        get() = nickname?.takeIf(String::isNotBlank) ?: email.substringBefore('@')
}

enum class UserGender {
    UNSPECIFIED,
    MALE,
    FEMALE,
}

enum class GoalType {
    LOSE_FAT,
    GAIN_MUSCLE,
    MAINTAIN,
}

data class FitnessGoal(
    val type: GoalType,
    val startWeight: Double,
    val targetWeight: Double,
    val targetDate: String?,
    val startedAt: String,
)

data class ProfileBody(val weight: Double, val recordedAt: String)

data class ProfileUpdate(val nickname: String, val gender: UserGender)

data class GoalUpdate(
    val type: GoalType,
    val targetWeight: Double,
    val targetDate: String?,
    val currentWeight: Double? = null,
)

fun FitnessGoal.progress(current: Double?): Int? {
    if (current == null || type == GoalType.MAINTAIN || startWeight == targetWeight) return null
    return kotlin.math
        .round((current - startWeight) / (targetWeight - startWeight) * 100)
        .toInt()
        .coerceIn(0, 100)
}
