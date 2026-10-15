package com.fittrace.feature.auth

import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class AuthRepositoryFake : AuthRepository {
    override val sessionStatus = MutableStateFlow<SessionStatus>(SessionStatus.SignedOut)
    var config = EmailCaptchaConfig(false)
    var sendCalls = 0
    var emailLoginCalls = 0
    var passwordLoginCalls = 0
    var resetCalls = 0
    var captchaCalls = 0
    var lastProof: String? = null
    var sendResult: AppResult<EmailCodeReceipt> = AppResult.Success(EmailCodeReceipt(60, 600))
    var passwordResult: AppResult<Unit> = AppResult.Success(Unit)
    var grant = ResetGrant("grant", 30_000)

    override suspend fun restoreSession() = AppResult.Success(Unit)

    override suspend fun loginByEmail(
        email: String,
        code: String,
        acceptedTerms: Boolean,
    ): AppResult<Unit> {
        emailLoginCalls++
        return AppResult.Success(Unit)
    }

    override suspend fun loginByPassword(
        email: String,
        password: String,
        captchaId: String?,
        captchaCode: String?,
    ): AppResult<Unit> {
        passwordLoginCalls++
        return passwordResult
    }

    override suspend fun captchaConfig() = AppResult.Success(config)

    override suspend fun sendCode(
        email: String,
        purpose: EmailCodePurpose,
        captchaProof: String?,
    ): AppResult<EmailCodeReceipt> {
        sendCalls++
        lastProof = captchaProof
        return sendResult
    }

    override suspend fun loginCaptcha(): AppResult<LoginCaptcha> {
        captchaCalls++
        return AppResult.Success(LoginCaptcha("id", "image", 120))
    }

    override suspend fun verifyResetCode(email: String, code: String) = AppResult.Success(grant)

    override suspend fun resetPassword(
        email: String,
        token: String,
        password: String,
    ): AppResult<Unit> {
        resetCalls++
        return AppResult.Success(Unit)
    }

    override suspend fun currentUser(): AppResult<AuthUser> =
        AppResult.Success(AuthUser("id", "user@example.com", null, null))

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}
