package com.fittrace.core.data

import com.fittrace.core.network.*
import kotlinx.serialization.json.JsonElement
import retrofit2.Response

internal open class AuthApiFixture : AuthApi {
    override suspend fun loginByEmail(
        input: EmailLoginRequest
    ): Response<ApiEnvelope<AuthSessionDto>> = error("Unexpected email login")

    override suspend fun loginByPassword(
        input: PasswordLoginRequest
    ): Response<ApiEnvelope<AuthSessionDto>> = error("Unexpected password login")

    override suspend fun captchaConfig(): Response<ApiEnvelope<EmailCaptchaConfigDto>> =
        error("Unexpected captcha config")

    override suspend fun sendCode(input: EmailCodeRequest): Response<ApiEnvelope<EmailCodeDto>> =
        error("Unexpected send")

    override suspend fun loginCaptcha(): Response<ApiEnvelope<LoginCaptchaDto>> =
        error("Unexpected image captcha")

    override suspend fun refresh(input: RefreshRequest): Response<ApiEnvelope<AuthSessionDto>> =
        error("Unexpected refresh")

    override suspend fun logout(input: RefreshRequest): Response<ApiEnvelope<JsonElement>> =
        error("Unexpected logout")

    override suspend fun currentUser(authorization: String): Response<ApiEnvelope<AuthUserDto>> =
        error("Unexpected me")

    override suspend fun verifyResetCode(
        input: VerifyResetRequest
    ): Response<ApiEnvelope<ResetGrantDto>> = error("Unexpected verify reset")

    override suspend fun resetPassword(
        input: ResetPasswordRequest
    ): Response<ApiEnvelope<JsonElement>> = error("Unexpected reset")
}
