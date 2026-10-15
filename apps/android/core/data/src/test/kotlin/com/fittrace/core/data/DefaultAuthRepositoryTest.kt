package com.fittrace.core.data

import com.fittrace.core.domain.SessionStore
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class DefaultAuthRepositoryTest {
    private val now = 1_700_000_000_000L
    private val user = AuthUser("u1", "user@example.com", null, false)

    private fun session(expired: Boolean = false) =
        AuthSession("old-access", now + if (expired) -1 else 60_000, "old-refresh", user)

    private fun dto() =
        AuthSessionDto(
            "new-access",
            Instant.ofEpochMilli(now + 900_000).toString(),
            "new-refresh",
            AuthUserDto(user.id, user.email),
        )

    private class Store(var value: AuthSession? = null) : SessionStore {
        var failWrite = false
        var failClear = false

        override suspend fun read() = value

        override suspend fun write(session: AuthSession) {
            if (failWrite) throw IOException()
            value = session
        }

        override suspend fun clear() {
            if (failClear) throw IOException()
            value = null
        }
    }

    @Test
    fun `consent is mandatory before email login`() = runTest {
        val repo = DefaultAuthRepository(AuthApiFixture(), Store()) { now }
        assertEquals(
            AppResult.Failure(AppError.Business("AGREEMENT_REQUIRED", "请先阅读并同意用户协议和隐私政策")),
            repo.loginByEmail("user@example.com", "123456", false),
        )
    }

    @Test
    fun `login normalizes email and persists before publishing signed in`() = runTest {
        val store = Store()
        var request: EmailLoginRequest? = null
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun loginByEmail(
                        input: EmailLoginRequest
                    ): Response<ApiEnvelope<AuthSessionDto>> {
                        request = input
                        return Response.success(ApiEnvelope("OK", "成功", dto()))
                    }
                },
                store,
            ) {
                now
            }
        repo.restoreSession()
        assertEquals(
            AppResult.Success(Unit),
            repo.loginByEmail(" User@Example.COM ", "123456", true),
        )
        assertEquals("user@example.com", request?.email)
        assertEquals("new-refresh", store.value?.refreshToken)
        assertTrue(repo.sessionStatus.value is SessionStatus.SignedIn)
        assertFalse(store.value.toString().contains("new-refresh"))
    }

    @Test
    fun `storage failure does not publish authenticated state`() = runTest {
        val store = Store().apply { failWrite = true }
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun loginByEmail(input: EmailLoginRequest) =
                        Response.success(ApiEnvelope("OK", "成功", dto()))
                },
                store,
            ) {
                now
            }
        repo.restoreSession()
        assertEquals(
            AppResult.Failure(AppError.Storage),
            repo.loginByEmail(user.email, "123456", true),
        )
        assertEquals(SessionStatus.SignedOut, repo.sessionStatus.value)
        assertNull(store.value)
    }

    @Test
    fun `expired session refresh is single flight across concurrent restore calls`() = runTest {
        var calls = 0
        val store = Store(session(true))
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun refresh(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<AuthSessionDto>> {
                        calls++
                        assertEquals("old-refresh", input.refreshToken)
                        return Response.success(ApiEnvelope("OK", "成功", dto()))
                    }
                },
                store,
            ) {
                now
            }
        List(12) { async { repo.restoreSession() } }.awaitAll()
        assertEquals(1, calls)
        assertEquals("new-refresh", store.value?.refreshToken)
        assertTrue(repo.sessionStatus.value is SessionStatus.SignedIn)
    }

    @Test
    fun `transient refresh failure preserves saved session for retry`() = runTest {
        val old = session(true)
        val store = Store(old)
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun refresh(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<AuthSessionDto>> = throw IOException("offline")
                },
                store,
            ) {
                now
            }
        assertEquals(AppResult.Failure(AppError.Network), repo.restoreSession())
        assertSame(old, store.value)
        assertEquals(SessionStatus.RecoveryRequired(AppError.Network), repo.sessionStatus.value)
    }

    @Test
    fun `revoked refresh token clears local credentials`() = runTest {
        val store = Store(session(true))
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun refresh(input: RefreshRequest) =
                        Response.success(ApiEnvelope<AuthSessionDto>("UNAUTHORIZED", "登录失效"))
                },
                store,
            ) {
                now
            }
        assertTrue(repo.restoreSession() is AppResult.Failure)
        assertNull(store.value)
        assertEquals(SessionStatus.SignedOut, repo.sessionStatus.value)
    }

    @Test
    fun `protected request refreshes once on unauthorized and retries with new access token`() =
        runTest {
            val headers = mutableListOf<String>()
            val repo =
                DefaultAuthRepository(
                    object : AuthApiFixture() {
                        override suspend fun currentUser(
                            authorization: String
                        ): Response<ApiEnvelope<AuthUserDto>> {
                            headers += authorization
                            return if (headers.size == 1)
                                Response.success(ApiEnvelope("UNAUTHORIZED", "过期"))
                            else
                                Response.success(
                                    ApiEnvelope("OK", "成功", AuthUserDto(user.id, user.email, "昵称"))
                                )
                        }

                        override suspend fun refresh(input: RefreshRequest) =
                            Response.success(ApiEnvelope("OK", "成功", dto()))
                    },
                    Store(session()),
                ) {
                    now
                }
            repo.restoreSession()
            val result = repo.currentUser() as AppResult.Success
            assertEquals("昵称", result.data.nickname)
            assertEquals(listOf("Bearer old-access", "Bearer new-access"), headers)
        }

    @Test
    fun `logout clears locally even if server cannot be reached`() = runTest {
        val store = Store(session())
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun logout(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<JsonElement>> = throw IOException()
                },
                store,
            ) {
                now
            }
        repo.restoreSession()
        assertEquals(AppResult.Failure(AppError.Network), repo.logout())
        assertNull(store.value)
        assertEquals(SessionStatus.SignedOut, repo.sessionStatus.value)
    }

    @Test
    fun `failed local logout is visible and does not claim credentials cleared`() = runTest {
        val store = Store(session()).apply { failClear = true }
        val repo = DefaultAuthRepository(AuthApiFixture(), store) { now }
        repo.restoreSession()
        assertEquals(AppResult.Failure(AppError.Storage), repo.logout())
        assertNotNull(store.value)
        assertEquals(SessionStatus.RecoveryRequired(AppError.Storage), repo.sessionStatus.value)
    }

    @Test
    fun `invalid expiry does not persist partial credentials`() = runTest {
        val store = Store()
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun loginByEmail(input: EmailLoginRequest) =
                        Response.success(
                            ApiEnvelope("OK", "成功", dto().copy(accessTokenExpiresAt = "not-a-date"))
                        )
                },
                store,
            ) {
                now
            }
        assertEquals(
            AppResult.Failure(AppError.InvalidResponse),
            repo.loginByEmail(user.email, "123456", true),
        )
        assertNull(store.value)
    }

    @Test
    fun `cancellation propagates without deleting session`() = runTest {
        val old = session(true)
        val store = Store(old)
        val cancellation = CancellationException("cancel")
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun refresh(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<AuthSessionDto>> = throw cancellation
                },
                store,
            ) {
                now
            }
        try {
            repo.restoreSession()
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
        assertSame(old, store.value)
    }

    @Test
    fun `logout waits for in flight rotation and revokes the rotated token`() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var revoked: String? = null
        val store = Store(session(true))
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun refresh(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<AuthSessionDto>> {
                        started.complete(Unit)
                        finish.await()
                        return Response.success(ApiEnvelope("OK", "成功", dto()))
                    }

                    override suspend fun logout(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<JsonElement>> {
                        revoked = input.refreshToken
                        return Response.success(ApiEnvelope("OK", "成功"))
                    }
                },
                store,
            ) {
                now
            }
        val restoring = async { repo.restoreSession() }
        started.await()
        val exiting = async { repo.logout() }
        finish.complete(Unit)
        restoring.await()
        exiting.await()
        assertEquals("new-refresh", revoked)
        assertNull(store.value)
        assertEquals(SessionStatus.SignedOut, repo.sessionStatus.value)
    }

    @Test
    fun `email code carries unchanged app proof and purpose`() = runTest {
        var request: EmailCodeRequest? = null
        val repo =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun sendCode(
                        input: EmailCodeRequest
                    ): Response<ApiEnvelope<EmailCodeDto>> {
                        request = input
                        return Response.success(ApiEnvelope("OK", "成功", EmailCodeDto(60, 600)))
                    }
                },
                Store(),
            ) {
                now
            }
        assertEquals(
            AppResult.Success(EmailCodeReceipt(60, 600)),
            repo.sendCode(" User@Example.com ", EmailCodePurpose.LOGIN, " opaque-proof "),
        )
        assertEquals("app", request?.captchaPlatform)
        assertEquals("LOGIN", request?.purpose)
        assertEquals(" opaque-proof ", request?.captchaVerifyParam)
        assertEquals("user@example.com", request?.email)
    }

    @Test
    fun `cancellation after disk commit cannot leave memory on old rotated credentials`() =
        runTest {
            val writing = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            var persisted: AuthSession? = null
            val store =
                object : SessionStore {
                    override suspend fun read(): AuthSession? = null

                    override suspend fun write(session: AuthSession) {
                        persisted = session
                        writing.complete(Unit)
                        finish.await()
                    }

                    override suspend fun clear() {
                        persisted = null
                    }
                }
            val repo =
                DefaultAuthRepository(
                    object : AuthApiFixture() {
                        override suspend fun loginByEmail(input: EmailLoginRequest) =
                            Response.success(ApiEnvelope("OK", "成功", dto()))
                    },
                    store,
                ) {
                    now
                }
            val login = async { repo.loginByEmail(user.email, "123456", true) }
            writing.await()
            login.cancel()
            finish.complete(Unit)
            login.join()
            assertEquals("new-refresh", persisted?.refreshToken)
            assertTrue(repo.sessionStatus.value is SessionStatus.SignedIn)
        }
}
