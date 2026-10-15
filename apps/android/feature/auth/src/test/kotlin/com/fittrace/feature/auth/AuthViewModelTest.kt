package com.fittrace.feature.auth

import androidx.lifecycle.viewModelScope
import com.fittrace.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private lateinit var fake: AuthRepositoryFake
    private lateinit var model: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        fake = AuthRepositoryFake()
        model = AuthViewModel(fake) { scheduler.currentTime }
    }

    @After
    fun cleanup() {
        model.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun prepareEmail() {
        model.setEmail("user@example.com")
        model.acceptTerms(true)
    }

    @Test
    fun `default entry is email and consent is unchecked`() {
        assertEquals(AuthMode.EMAIL, model.state.value.mode)
        assertFalse(model.state.value.acceptedTerms)
    }

    @Test
    fun `invalid fields and missing consent never call login`() =
        runTest(dispatcher) {
            model.submit()
            runCurrent()
            assertEquals(0, fake.emailLoginCalls)
            assertTrue(model.state.value.errors.containsKey(AuthField.EMAIL))
            assertTrue(model.state.value.errors.containsKey(AuthField.CODE))
            assertTrue(model.state.value.errors.containsKey(AuthField.AGREEMENT))
        }

    @Test
    fun `SDK is not loaded before login consent`() =
        runTest(dispatcher) {
            model.setEmail("user@example.com")
            model.requestCode()
            runCurrent()
            assertFalse(model.state.value.busy)
            assertNull(model.state.value.challenge)
            assertEquals(0, fake.sendCalls)
        }

    @Test
    fun `cancelling provider verification does not send email or start cooldown`() =
        runTest(dispatcher) {
            fake.config = EmailCaptchaConfig(true, "cn", "prefix", "app")
            prepareEmail()
            model.requestCode()
            runCurrent()
            val challenge = requireNotNull(model.state.value.challenge)
            assertTrue(model.state.value.busy)
            assertEquals(0, fake.sendCalls)
            model.captchaCompleted(challenge.id, null)
            runCurrent()
            assertEquals(0, fake.sendCalls)
            assertEquals(0, model.state.value.countdown)
            assertFalse(model.state.value.busy)
        }

    @Test
    fun `proof is consumed once and only success starts cooldown`() =
        runTest(dispatcher) {
            fake.config = EmailCaptchaConfig(true, "cn", "prefix", "app")
            prepareEmail()
            model.requestCode()
            model.requestCode()
            runCurrent()
            val challenge = requireNotNull(model.state.value.challenge)
            model.captchaCompleted(challenge.id, " opaque ")
            model.captchaCompleted(challenge.id, "duplicate")
            runCurrent()
            assertEquals(1, fake.sendCalls)
            assertEquals(" opaque ", fake.lastProof)
            assertEquals(60, model.state.value.countdown)
            assertFalse(model.state.value.busy)
        }

    @Test
    fun `enabled configuration without app scene fails closed`() =
        runTest(dispatcher) {
            fake.config = EmailCaptchaConfig(true, "cn", "prefix", null)
            prepareEmail()
            model.requestCode()
            runCurrent()
            assertEquals(0, fake.sendCalls)
            assertFalse(model.state.value.busy)
            assertNotNull(model.state.value.notice)
        }

    @Test
    fun `countdowns are scoped by email and purpose and use elapsed wall time`() =
        runTest(dispatcher) {
            prepareEmail()
            model.requestCode()
            runCurrent()
            assertEquals(60, model.state.value.countdown)
            model.setEmail("another@example.com")
            assertEquals(0, model.state.value.countdown)
            model.setEmail("user@example.com")
            assertEquals(60, model.state.value.countdown)
            model.switchMode(AuthMode.RESET_EMAIL)
            assertEquals(0, model.state.value.countdown)
            model.switchMode(AuthMode.EMAIL)
            advanceTimeBy(12_000)
            runCurrent()
            assertEquals(48, model.state.value.countdown)
        }

    @Test
    fun `server retry after is honored without pretending mail was sent`() =
        runTest(dispatcher) {
            fake.sendResult =
                AppResult.Failure(AppError.Business("AUTH_RATE_LIMITED", "稍后重试", 429, 25))
            prepareEmail()
            model.requestCode()
            runCurrent()
            assertEquals(25, model.state.value.countdown)
            assertEquals("稍后重试", model.state.value.notice)
        }

    @Test
    fun `changing mode clears secrets but retains email and explicit consent`() {
        prepareEmail()
        model.setCode("123456")
        model.setPassword("secret-password")
        assertFalse(model.state.value.toString().contains("secret-password"))
        model.switchMode(AuthMode.PASSWORD)
        assertEquals("", model.state.value.code)
        assertEquals("", model.state.value.password)
        assertEquals("user@example.com", model.state.value.email)
        assertTrue(model.state.value.acceptedTerms)
    }

    @Test
    fun `password risk control loads required captcha and blocks empty captcha submission`() =
        runTest(dispatcher) {
            fake.passwordResult =
                AppResult.Failure(AppError.Business("LOGIN_CAPTCHA_REQUIRED", "请完成图形验证", 403))
            prepareEmail()
            model.switchMode(AuthMode.PASSWORD)
            model.setPassword("password123")
            model.submit()
            runCurrent()
            assertTrue(model.state.value.captchaRequired)
            assertNotNull(model.state.value.loginCaptcha)
            assertEquals(1, fake.captchaCalls)
            model.submit()
            runCurrent()
            assertEquals(1, fake.passwordLoginCalls)
            assertTrue(model.state.value.errors.containsKey(AuthField.CAPTCHA))
        }

    @Test
    fun `reset confirmation mismatch is rejected without request`() =
        runTest(dispatcher) {
            prepareEmail()
            model.switchMode(AuthMode.RESET_EMAIL)
            model.setCode("123456")
            model.submit()
            runCurrent()
            assertEquals(AuthMode.RESET_PASSWORD, model.state.value.mode)
            model.setPassword("password123")
            model.setConfirmPassword("different123")
            model.submit()
            runCurrent()
            assertEquals(0, fake.resetCalls)
            assertTrue(model.state.value.errors.containsKey(AuthField.CONFIRM_PASSWORD))
        }

    @Test
    fun `expired reset authorization returns to email verification and clears passwords`() =
        runTest(dispatcher) {
            prepareEmail()
            model.switchMode(AuthMode.RESET_EMAIL)
            model.setCode("123456")
            model.submit()
            runCurrent()
            model.setPassword("password123")
            model.setConfirmPassword("password123")
            advanceTimeBy(31_000)
            model.submit()
            runCurrent()
            assertEquals(0, fake.resetCalls)
            assertEquals(AuthMode.RESET_EMAIL, model.state.value.mode)
            assertEquals("", model.state.value.password)
        }

    @Test
    fun `reset success returns to password login and clears credentials`() =
        runTest(dispatcher) {
            prepareEmail()
            model.switchMode(AuthMode.RESET_EMAIL)
            model.setCode("123456")
            model.submit()
            runCurrent()
            model.setPassword("password123")
            model.setConfirmPassword("password123")
            model.submit()
            runCurrent()
            assertEquals(1, fake.resetCalls)
            assertEquals(AuthMode.PASSWORD, model.state.value.mode)
            assertEquals("", model.state.value.password)
            assertEquals("", model.state.value.confirmPassword)
        }
}
