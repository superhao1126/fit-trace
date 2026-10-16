package com.fittrace.core.data

import com.fittrace.core.domain.SessionStore
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class DefaultDashboardRepositoryTest {
    private val now = 1_700_000_000_000L

    private class Store(var value: AuthSession?) : SessionStore {
        override suspend fun read() = value

        override suspend fun write(session: AuthSession) {
            value = session
        }

        override suspend fun clear() {
            value = null
        }
    }

    private fun dto(date: String = "2026-10-16") =
        DashboardDto(
            date,
            DashboardTodayDto(
                null,
                null,
                1,
                0,
                0,
                0,
                listOf(
                    DashboardMealDto("m", "BREAKFAST", "2026-10-16T00:30:00Z", listOf("鸡蛋"), null)
                ),
                emptyList(),
                emptyList(),
            ),
            DashboardTrendDto("2026-10-10", date, null, 0, 1, 0, 0),
        )

    @Test
    fun `authorized overview preserves unknown calories maps goal and forwards selected day`() =
        runTest {
            val store =
                Store(
                    AuthSession(
                        "access",
                        now + 900_000,
                        "refresh",
                        AuthUser("u", "u@example.com", null, false),
                    )
                )
            val auth = DefaultAuthRepository(AuthApiFixture(), store) { now }
            auth.restoreSession()
            var token = ""
            var day = ""
            var offset = 0
            val api =
                object : DashboardApi {
                    override suspend fun overview(
                        authorization: String,
                        date: String,
                        timezoneOffset: Int,
                    ): Response<ApiEnvelope<DashboardDto>> {
                        token = authorization
                        day = date
                        offset = timezoneOffset
                        return Response.success(ApiEnvelope("OK", "", dto()))
                    }

                    override suspend fun profile(authorization: String) =
                        Response.success(
                            ApiEnvelope(
                                "OK",
                                "",
                                DashboardProfileDto(DashboardGoalDto("LOSE_FAT", 68.0)),
                            )
                        )
                }
            val result =
                DefaultDashboardRepository(api, auth).overview("2026-10-16", 480)
                    as AppResult.Success
            assertEquals("Bearer access", token)
            assertEquals("2026-10-16", day)
            assertEquals(480, offset)
            assertNull(result.data.calories)
            assertEquals("热量未填写", result.data.records.single().value)
            assertEquals(68.0, result.data.goal?.targetWeight)
        }

    @Test
    fun `unauthorized dashboard refreshes once and clears repeated invalid session`() = runTest {
        val store =
            Store(
                AuthSession(
                    "access",
                    now + 900_000,
                    "refresh",
                    AuthUser("u", "u@example.com", null, false),
                )
            )
        var refreshes = 0
        val auth =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun refresh(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<AuthSessionDto>> {
                        refreshes++
                        return Response.success(
                            ApiEnvelope(
                                "OK",
                                "",
                                AuthSessionDto(
                                    "new",
                                    Instant.ofEpochMilli(now + 900_000).toString(),
                                    "r2",
                                    AuthUserDto("u", "u@example.com"),
                                ),
                            )
                        )
                    }
                },
                store,
            ) {
                now
            }
        auth.restoreSession()
        var calls = 0
        val result =
            DefaultDashboardRepository(
                    object : DashboardApi {
                        override suspend fun overview(
                            authorization: String,
                            date: String,
                            timezoneOffset: Int,
                        ): Response<ApiEnvelope<DashboardDto>> {
                            calls++
                            return Response.success(ApiEnvelope("UNAUTHORIZED", "expired", null))
                        }

                        override suspend fun profile(
                            authorization: String
                        ): Response<ApiEnvelope<DashboardProfileDto>> =
                            error("must not load profile")
                    },
                    auth,
                )
                .overview("2026-10-16", 480)
        assertTrue(result is AppResult.Failure)
        assertEquals(2, calls)
        assertEquals(1, refreshes)
        assertEquals(SessionStatus.SignedOut, auth.sessionStatus.value)
        assertNull(store.value)
    }

    @Test
    fun `business errors invalid date and network failure never become empty success`() = runTest {
        val store =
            Store(
                AuthSession(
                    "access",
                    now + 900_000,
                    "refresh",
                    AuthUser("u", "u@example.com", null, false),
                )
            )
        val auth = DefaultAuthRepository(AuthApiFixture(), store) { now }
        auth.restoreSession()
        var mode = 0
        val api =
            object : DashboardApi {
                override suspend fun overview(
                    authorization: String,
                    date: String,
                    timezoneOffset: Int,
                ): Response<ApiEnvelope<DashboardDto>> =
                    when (mode) {
                        0 -> Response.success(ApiEnvelope("FAILED", "failure", dto()))
                        1 -> Response.success(ApiEnvelope("OK", "", dto("2026-10-15")))
                        else -> throw IOException()
                    }

                override suspend fun profile(authorization: String) =
                    Response.success(ApiEnvelope("OK", "", DashboardProfileDto()))
            }
        val repo = DefaultDashboardRepository(api, auth)
        assertTrue(repo.overview("2026-10-16", 480) is AppResult.Failure)
        mode = 1
        assertEquals(AppResult.Failure(AppError.InvalidResponse), repo.overview("2026-10-16", 480))
        mode = 2
        assertEquals(AppResult.Failure(AppError.Network), repo.overview("2026-10-16", 480))
    }
}
