package com.fittrace.core.data

import com.fittrace.core.domain.SessionStore
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class DefaultProfileRepositoryTest {
    private val now = 1_700_000_000_000L
    private val user =
        ProfileDto(
            "u",
            "hello@example.com",
            null,
            null,
            "UNSPECIFIED",
            "2026-10-01T00:00:00Z",
            FitnessGoalDto("LOSE_FAT", 71.0, 68.0, null, "2026-10-01T00:00:00Z"),
        )

    private open inner class Api : ProfileApi {
        override suspend fun profile(authorization: String) =
            Response.success(ApiEnvelope("OK", "", user))

        override suspend fun latestBody(
            authorization: String
        ): Response<ApiEnvelope<ProfileBodyDto>> = Response.success(ApiEnvelope("OK", "", null))

        override suspend fun updateProfile(authorization: String, input: ProfileUpdateDto) =
            Response.success(ApiEnvelope("OK", "", user.copy(nickname = input.nickname)))

        override suspend fun updateGoal(authorization: String, input: GoalUpdateDto) =
            Response.success(ApiEnvelope("OK", "", user))
    }

    private suspend fun repo(api: ProfileApi): DefaultProfileRepository {
        val auth =
            DefaultAuthRepository(
                AuthApiFixture(),
                object : SessionStore {
                    override suspend fun read() =
                        AuthSession(
                            "access",
                            now + 900_000,
                            "refresh",
                            AuthUser("u", "hello@example.com", null, false),
                        )

                    override suspend fun write(session: AuthSession) {}

                    override suspend fun clear() {}
                },
            ) {
                now
            }
        auth.restoreSession()
        return DefaultProfileRepository(api, auth)
    }

    @Test
    fun `profile maps real join date goal and nullable latest weight`() = runTest {
        var token = ""
        val repo =
            repo(
                object : Api() {
                    override suspend fun profile(
                        authorization: String
                    ): Response<ApiEnvelope<ProfileDto>> {
                        token = authorization
                        return super.profile(authorization)
                    }
                }
            )
        val result = repo.profile() as AppResult.Success
        assertEquals("Bearer access", token)
        assertEquals("hello", result.data.displayName)
        assertEquals(71.0, result.data.goal?.startWeight)
        assertEquals(AppResult.Success<ProfileBody?>(null), repo.latestBody())
    }

    @Test
    fun `business and malformed profile fail rather than create empty user`() = runTest {
        val failed =
            repo(
                    object : Api() {
                        override suspend fun profile(authorization: String) =
                            Response.success(ApiEnvelope("FAILED", "失败", user))
                    }
                )
                .profile()
        assertTrue(failed is AppResult.Failure)
        val invalid =
            repo(
                    object : Api() {
                        override suspend fun profile(authorization: String) =
                            Response.success(
                                ApiEnvelope("OK", "", user.copy(createdAt = "invalid"))
                            )
                    }
                )
                .profile()
        assertEquals(AppResult.Failure(AppError.InvalidResponse), invalid)
    }

    @Test
    fun `save profile maps returned nickname and goal input stays explicit`() = runTest {
        var captured: GoalUpdateDto? = null
        val repo =
            repo(
                object : Api() {
                    override suspend fun updateGoal(
                        authorization: String,
                        input: GoalUpdateDto,
                    ): Response<ApiEnvelope<ProfileDto>> {
                        captured = input
                        return super.updateGoal(authorization, input)
                    }
                }
            )
        assertEquals(
            "新昵称",
            (repo.updateProfile(ProfileUpdate("新昵称", UserGender.FEMALE)) as AppResult.Success)
                .data
                .nickname,
        )
        repo.updateGoal(GoalUpdate(GoalType.LOSE_FAT, 68.0, null))
        assertNull(captured?.currentWeight)
        assertEquals("LOSE_FAT", captured?.type)
    }
}
