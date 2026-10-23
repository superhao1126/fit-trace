package com.fittrace.core.data

import com.fittrace.core.domain.*
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class DefaultBiometricRepositoryTest {
    private class Keys : DeviceKeyStore {
        var binding: DeviceBinding? = null
        var valid = true
        val deleted = mutableListOf<String>()

        override fun capability() = BiometricCapability.AVAILABLE

        override fun read() = binding

        override fun write(binding: DeviceBinding) {
            this.binding = binding
        }

        override fun create(alias: String) = "public-key"

        override fun usable(alias: String) = valid

        override fun delete(alias: String) {
            deleted += alias
        }

        override fun clear() {
            binding = null
        }
    }

    private class Store : SessionStore {
        var value: AuthSession? =
            AuthSession(
                "old-access",
                System.currentTimeMillis() + 900000,
                "old-refresh",
                AuthUser("owner", "owner@example.com", null, false),
            )

        override suspend fun read() = value

        override suspend fun write(session: AuthSession) {
            value = session
        }

        override suspend fun clear() {
            value = null
        }
    }

    private class Api : DeviceCredentialApi {
        var registered = false
        var failVerify = false
        var failRevoke = false
        var verifications = 0
        var request: DeviceRegistrationRequest? = null
        val credential = DeviceCredentialDto("credential", "android-prod", "Test")

        override suspend fun registrationOptions(
            token: String,
            input: DeviceRegistrationRequest,
        ): Response<ApiEnvelope<DeviceChallengeDto>> {
            request = input
            return ok(
                DeviceChallengeDto(
                    "register",
                    "exact-server-payload",
                    Instant.now().plusSeconds(120).toString(),
                    credential.id,
                )
            )
        }

        override suspend fun registrationVerify(
            token: String,
            input: DeviceVerifyRequest,
        ): Response<ApiEnvelope<DeviceCredentialDto>> {
            verifications++
            registered = true
            if (failVerify) throw java.io.IOException()
            return ok(credential)
        }

        override suspend fun loginOptions(input: DeviceLoginRequest) =
            ok(
                DeviceChallengeDto(
                    "login",
                    "login-server-payload",
                    Instant.now().plusSeconds(120).toString(),
                )
            )

        override suspend fun loginVerify(input: DeviceVerifyRequest) =
            ok(
                AuthSessionDto(
                    "fresh-access",
                    Instant.now().plusSeconds(900).toString(),
                    "fresh-refresh",
                    AuthUserDto("owner", "owner@example.com"),
                )
            )

        override suspend fun list(token: String) =
            ok(if (registered) listOf(credential) else emptyList())

        override suspend fun revoke(token: String, id: String): Response<ApiEnvelope<JsonElement>> {
            if (failRevoke) throw java.io.IOException()
            registered = false
            return ok(JsonNull)
        }

        private fun <T> ok(value: T) = Response.success(ApiEnvelope("OK", "成功", value))
    }

    private suspend fun fixture(): Triple<DefaultAuthRepository, Store, Keys> {
        val store = Store()
        val auth =
            DefaultAuthRepository(
                object : AuthApiFixture() {
                    override suspend fun logout(
                        input: RefreshRequest
                    ): Response<ApiEnvelope<JsonElement>> {
                        assertEquals("old-refresh", input.refreshToken)
                        return Response.success(ApiEnvelope("OK", "成功", JsonNull))
                    }
                },
                store,
            )
        auth.restoreSession()
        return Triple(auth, store, Keys())
    }

    private fun repo(api: Api, auth: DefaultAuthRepository, keys: Keys) =
        DefaultBiometricRepository(api, auth, keys, "android-prod", "Test")

    private val signer =
        object : BiometricSigner {
            override suspend fun sign(alias: String, payload: String): AppResult<String> {
                assertTrue(payload.endsWith("server-payload"))
                return AppResult.Success("signed")
            }
        }

    @Test
    fun `cancel never creates server binding and cleans unconfirmed key`() = runTest {
        val (auth, _, keys) = fixture()
        val api = Api()
        val repo = repo(api, auth, keys)
        val result =
            repo.enable(
                null,
                object : BiometricSigner {
                    override suspend fun sign(alias: String, payload: String) =
                        AppResult.Failure(AppError.Business("BIOMETRIC_CANCELLED", "取消"))
                },
            )
        assertTrue(result is AppResult.Failure)
        assertEquals(0, api.verifications)
        assertNull(keys.binding)
        assertEquals(1, keys.deleted.size)
    }

    @Test
    fun `unknown registration result recovers using persisted pending id`() = runTest {
        val (auth, _, keys) = fixture()
        val api = Api().apply { failVerify = true }
        val repo = repo(api, auth, keys)
        assertTrue(repo.enable("123456", signer) is AppResult.Failure)
        assertTrue(keys.binding!!.pending)
        assertEquals("123456", api.request?.emailCode)
        val status = repo.status(true) as AppResult.Success
        assertTrue(status.data.enabled)
        assertFalse(keys.binding!!.pending)
        assertTrue(keys.deleted.isEmpty())
    }

    @Test
    fun `logout retains independent key and signed login persists a new session`() = runTest {
        val (auth, store, keys) = fixture()
        val api = Api()
        val repo = repo(api, auth, keys)
        assertEquals(AppResult.Success(Unit), repo.enable(null, signer))
        val binding = keys.binding
        auth.logout()
        assertNull(store.value)
        assertEquals(binding, keys.binding)
        assertEquals(AppResult.Success(Unit), repo.login(signer))
        assertEquals("fresh-refresh", store.value?.refreshToken)
        assertTrue(auth.sessionStatus.value is SessionStatus.SignedIn)
    }

    @Test
    fun `failed remote revocation retains key until successful retry`() = runTest {
        val (auth, _, keys) = fixture()
        val api = Api()
        val repo = repo(api, auth, keys)
        repo.enable(null, signer)
        api.failRevoke = true
        assertTrue(repo.disable() is AppResult.Failure)
        assertNotNull(keys.binding)
        assertTrue(keys.deleted.isEmpty())
        api.failRevoke = false
        assertEquals(AppResult.Success(Unit), repo.disable())
        assertNull(keys.binding)
    }

    @Test
    fun `enrollment changes block login and require email recovery`() = runTest {
        val (auth, _, keys) = fixture()
        val api = Api()
        val repo = repo(api, auth, keys)
        repo.enable(null, signer)
        keys.valid = false
        assertTrue((repo.status(false) as AppResult.Success).data.invalidated)
        assertTrue(repo.login(signer) is AppResult.Failure)
        assertNotNull(keys.binding)
    }

    @Test
    fun `cancellation before verify cleans key and propagates coroutine cancellation`() = runTest {
        val (auth, _, keys) = fixture()
        val repo = repo(Api(), auth, keys)
        try {
            repo.enable(
                null,
                object : BiometricSigner {
                    override suspend fun sign(alias: String, payload: String): AppResult<String> {
                        throw CancellationException()
                    }
                },
            )
            fail()
        } catch (_: CancellationException) {}
        assertNull(keys.binding)
        assertEquals(1, keys.deleted.size)
    }

    @Test
    fun `different account binding cannot be silently overwritten`() = runTest {
        val (auth, _, keys) = fixture()
        val api = Api()
        val repo = repo(api, auth, keys)
        keys.binding = DeviceBinding("old", "other-user", "other@example.com", "other-alias")
        assertTrue(repo.status(true) is AppResult.Failure)
        assertTrue(repo.enable(null, signer) is AppResult.Failure)
        assertTrue(repo.disable() is AppResult.Failure)
        assertEquals("other-alias", keys.binding?.alias)
        assertEquals(AppResult.Success(Unit), repo.removeLocal())
        assertNull(keys.binding)
    }
}
