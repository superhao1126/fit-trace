package com.fittrace.core.data

import com.fittrace.core.domain.ProfileRepository
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import java.time.OffsetDateTime

class DefaultProfileRepository(
    private val api: ProfileApi,
    private val auth: DefaultAuthRepository,
) : ProfileRepository {
    private val requests = AuthRequests()

    override suspend fun profile(): AppResult<UserProfile> =
        auth.authorized { token -> requests.call { api.profile(token) }.map { it.model() } }

    override suspend fun latestBody(): AppResult<ProfileBody?> =
        auth.authorized { token ->
            requests
                .nullableCall { api.latestBody(token) }
                .map { dto ->
                    dto?.let {
                        require(it.weight.isFinite() && it.weight > 0)
                        OffsetDateTime.parse(it.recordedAt)
                        ProfileBody(it.weight, it.recordedAt)
                    }
                }
        }

    override suspend fun updateProfile(input: ProfileUpdate): AppResult<UserProfile> =
        auth.authorized { token ->
            requests
                .call {
                    if (input.avatarChanged)
                        api.updateProfileWithAvatar(
                            token,
                            ProfileWithAvatarUpdateDto(
                                input.nickname,
                                input.gender.name,
                                input.avatarUrl,
                            ),
                        )
                    else
                        api.updateProfile(
                            token,
                            ProfileUpdateDto(input.nickname, input.gender.name),
                        )
                }
                .map { it.model() }
        }

    override suspend fun updateGoal(input: GoalUpdate): AppResult<UserProfile> =
        auth.authorized { token ->
            requests
                .call {
                    api.updateGoal(
                        token,
                        GoalUpdateDto(
                            input.type.name,
                            input.targetWeight,
                            input.targetDate,
                            input.currentWeight,
                        ),
                    )
                }
                .map { it.model() }
        }
}

private fun ProfileDto.model(): UserProfile {
    require(id.isNotBlank() && email.isNotBlank())
    OffsetDateTime.parse(createdAt)
    return UserProfile(
        id,
        email,
        nickname,
        avatarUrl,
        UserGender.valueOf(gender),
        createdAt,
        goal?.let {
            require(
                it.startWeight.isFinite() &&
                    it.targetWeight.isFinite() &&
                    it.startWeight > 0 &&
                    it.targetWeight > 0
            )
            OffsetDateTime.parse(it.startedAt)
            it.targetDate?.let(OffsetDateTime::parse)
            FitnessGoal(
                GoalType.valueOf(it.type),
                it.startWeight,
                it.targetWeight,
                it.targetDate,
                it.startedAt,
            )
        },
    )
}
