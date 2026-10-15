package com.fittrace.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val parent =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    private lateinit var auth: Repository
    private lateinit var model: AppViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        auth = Repository()
        model = AppViewModel(auth)
        ViewModelProvider(parent, factory { model })[AppViewModel::class.java]
    }

    @After
    fun cleanup() {
        parent.viewModelStore.clear()
        Dispatchers.resetMain()
    }

    private fun probe() =
        ViewModelProvider(model.authFlowOwner, factory { Probe() })[Probe::class.java]

    private fun factory(create: () -> ViewModel) =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
        }

    private class Probe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun `authentication owner retains its view models while signed out`() =
        runTest(dispatcher) {
            val previous = probe()
            model.restore()
            runCurrent()
            assertSame(previous, probe())
            assertFalse(previous.cleared)
        }

    @Test
    fun `successful authentication clears sensitive auth flow before next account login`() =
        runTest(dispatcher) {
            val previous = probe()
            auth.sessionStatus.value =
                SessionStatus.SignedIn(AuthUser("id", "user@example.com", null, null))
            runCurrent()
            assertTrue(previous.cleared)
            auth.sessionStatus.value = SessionStatus.SignedOut
            runCurrent()
            assertNotSame(previous, probe())
        }

    @Test
    fun `parallel foreground restore triggers a single request`() =
        runTest(dispatcher) {
            auth.waitForRestore = CompletableDeferred()
            repeat(6) { model.restore() }
            runCurrent()
            assertEquals(1, auth.restoreCalls)
            assertTrue(model.busy.value)
            auth.waitForRestore?.complete(Unit)
            runCurrent()
            assertFalse(model.busy.value)
        }

    @Test
    fun `restored signed in session verifies current account`() =
        runTest(dispatcher) {
            auth.sessionStatus.value =
                SessionStatus.SignedIn(AuthUser("id", "user@example.com", null, null))
            model.restore()
            runCurrent()
            assertEquals(1, auth.meCalls)
        }

    @Test
    fun `application view model disposal clears child authentication view models`() =
        runTest(dispatcher) {
            val previous = probe()
            parent.viewModelStore.clear()
            assertTrue(previous.cleared)
        }

    private class Repository : AuthRepository {
        override val sessionStatus = MutableStateFlow<SessionStatus>(SessionStatus.SignedOut)
        var restoreCalls = 0
        var meCalls = 0
        var waitForRestore: CompletableDeferred<Unit>? = null

        override suspend fun restoreSession(): AppResult<Unit> {
            restoreCalls++
            waitForRestore?.await()
            return AppResult.Success(Unit)
        }

        override suspend fun currentUser(): AppResult<AuthUser> {
            meCalls++
            return AppResult.Success(AuthUser("id", "user@example.com", null, null))
        }

        override suspend fun logout() = AppResult.Success(Unit)

        override suspend fun loginByEmail(
            email: String,
            code: String,
            acceptedTerms: Boolean,
        ): AppResult<Unit> = error("unused")

        override suspend fun loginByPassword(
            email: String,
            password: String,
            captchaId: String?,
            captchaCode: String?,
        ): AppResult<Unit> = error("unused")

        override suspend fun captchaConfig(): AppResult<EmailCaptchaConfig> = error("unused")

        override suspend fun sendCode(
            email: String,
            purpose: EmailCodePurpose,
            captchaProof: String?,
        ): AppResult<EmailCodeReceipt> = error("unused")

        override suspend fun loginCaptcha(): AppResult<LoginCaptcha> = error("unused")

        override suspend fun verifyResetCode(email: String, code: String): AppResult<ResetGrant> =
            error("unused")

        override suspend fun resetPassword(
            email: String,
            token: String,
            password: String,
        ): AppResult<Unit> = error("unused")
    }
}
