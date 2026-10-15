package com.fittrace.core.domain

import com.fittrace.core.model.*
import kotlinx.coroutines.flow.StateFlow

interface SessionStore {
    suspend fun read(): AuthSession?

    suspend fun write(session: AuthSession)

    suspend fun clear()
}

interface AuthRepository {
    val sessionStatus: StateFlow<SessionStatus>

    suspend fun restoreSession(): AppResult<Unit>

    suspend fun loginByEmail(email: String, code: String, acceptedTerms: Boolean): AppResult<Unit>

    suspend fun loginByPassword(
        email: String,
        password: String,
        captchaId: String?,
        captchaCode: String?,
    ): AppResult<Unit>

    suspend fun captchaConfig(): AppResult<EmailCaptchaConfig>

    suspend fun sendCode(
        email: String,
        purpose: EmailCodePurpose,
        captchaProof: String?,
    ): AppResult<EmailCodeReceipt>

    suspend fun loginCaptcha(): AppResult<LoginCaptcha>

    suspend fun verifyResetCode(email: String, code: String): AppResult<ResetGrant>

    suspend fun resetPassword(email: String, token: String, password: String): AppResult<Unit>

    suspend fun currentUser(): AppResult<AuthUser>

    suspend fun logout(): AppResult<Unit>
}
