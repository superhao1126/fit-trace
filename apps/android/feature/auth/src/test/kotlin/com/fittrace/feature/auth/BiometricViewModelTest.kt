package com.fittrace.feature.auth

import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.BiometricRepository
import com.fittrace.core.domain.BiometricSigner
import com.fittrace.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiometricViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fake = FakeBiometricRepository()
    private val signer =
        object : BiometricSigner {
            override suspend fun sign(alias: String, payload: String): AppResult<String> =
                AppResult.Success("signature")
        }
    private lateinit var model: BiometricViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        if (::model.isInitialized) model.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `failed server check never turns a local binding into a confirmed off switch`() =
        runTest(dispatcher) {
            fake.serverResult = AppResult.Failure(AppError.Network)
            model = BiometricViewModel(fake, true)
            runCurrent()
            assertTrue(model.state.value.device!!.enabled)
            assertFalse(model.state.value.statusConfirmed)
            assertEquals(AppError.Network, model.state.value.error)
            fake.serverResult = null
            model.refresh()
            runCurrent()
            assertTrue(model.state.value.statusConfirmed)
            assertTrue(model.state.value.device!!.enabled)
            assertNull(model.state.value.error)
        }

    @Test
    fun `failed disable preserves enabled state and explicit error`() =
        runTest(dispatcher) {
            model = BiometricViewModel(fake, true)
            runCurrent()
            fake.disableResult = AppResult.Failure(AppError.Network)
            model.disable()
            runCurrent()
            assertTrue(model.state.value.statusConfirmed)
            assertTrue(model.state.value.device!!.enabled)
            assertEquals(AppError.Network, model.state.value.error)
        }

    @Test
    fun `email verification is conditional and cancelling it does not enable login`() =
        runTest(dispatcher) {
            fake.device = BiometricState(BiometricCapability.AVAILABLE)
            fake.enableResult =
                AppResult.Failure(AppError.Business("EMAIL_VERIFICATION_REQUIRED", "请先验证邮箱"))
            model = BiometricViewModel(fake, true)
            runCurrent()
            model.enable("", signer)
            runCurrent()
            assertTrue(model.state.value.needsEmail)
            model.dismissEmail()
            runCurrent()
            assertFalse(model.state.value.needsEmail)
            assertFalse(model.state.value.device!!.enabled)
            assertNull(model.state.value.error)
            assertEquals(1, fake.enableCalls)
        }

    @Test
    fun `credential rejected by server stays invalid across local refresh`() =
        runTest(dispatcher) {
            model = BiometricViewModel(fake, false)
            runCurrent()
            fake.loginResult =
                AppResult.Failure(AppError.Business("DEVICE_CREDENTIAL_INVALID", "凭据已失效"))
            model.login(signer)
            runCurrent()
            model.refresh()
            runCurrent()
            assertEquals(
                "DEVICE_CREDENTIAL_INVALID",
                (model.state.value.error as AppError.Business).code,
            )
            assertTrue(model.state.value.device!!.enabled)
        }

    @Test
    fun `repeated activation while waiting for system authentication runs only once`() =
        runTest(dispatcher) {
            val result = CompletableDeferred<AppResult<Unit>>()
            fake.activation = { result.await() }
            model = BiometricViewModel(fake, true)
            runCurrent()
            model.enable("123456", signer)
            model.enable("123456", signer)
            runCurrent()
            assertTrue(model.state.value.busy)
            assertEquals(1, fake.enableCalls)
            result.complete(AppResult.Failure(AppError.Business("BIOMETRIC_CANCELLED", "已取消验证")))
            runCurrent()
            assertFalse(model.state.value.busy)
            assertEquals("BIOMETRIC_CANCELLED", (model.state.value.error as AppError.Business).code)
        }

    private class FakeBiometricRepository : BiometricRepository {
        var device =
            BiometricState(
                BiometricCapability.AVAILABLE,
                DeviceBinding("id", "owner", "owner@example.com", "alias"),
                enabled = true,
            )
        var serverResult: AppResult<BiometricState>? = null
        var enableResult: AppResult<Unit> = AppResult.Success(Unit)
        var disableResult: AppResult<Unit> = AppResult.Success(Unit)
        var loginResult: AppResult<Unit> = AppResult.Success(Unit)
        var activation: (suspend () -> AppResult<Unit>)? = null
        var enableCalls = 0

        override suspend fun status(checkServer: Boolean): AppResult<BiometricState> =
            if (checkServer) serverResult ?: AppResult.Success(device)
            else AppResult.Success(device)

        override suspend fun enable(emailCode: String?, signer: BiometricSigner): AppResult<Unit> {
            enableCalls++
            return activation?.invoke() ?: enableResult
        }

        override suspend fun login(signer: BiometricSigner) = loginResult

        override suspend fun disable() = disableResult

        override suspend fun removeLocal(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
