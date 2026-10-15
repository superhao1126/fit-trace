package com.fittrace.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String,
    val nickname: String? = null,
    val hasPassword: Boolean? = null,
)

@Serializable
data class AuthSessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val user: AuthUserDto,
)

@Serializable
data class EmailLoginRequest(val email: String, val emailCode: String, val acceptedTerms: Boolean)

@Serializable
data class PasswordLoginRequest(
    val email: String,
    val password: String,
    val captchaId: String? = null,
    val captchaCode: String? = null,
)

@Serializable
data class EmailCodeRequest(
    val email: String,
    val purpose: String,
    val captchaVerifyParam: String? = null,
    val captchaPlatform: String,
)

@Serializable data class RefreshRequest(val refreshToken: String)

@Serializable data class VerifyResetRequest(val email: String, val emailCode: String)

@Serializable
data class ResetPasswordRequest(val email: String, val resetToken: String, val password: String)

@Serializable data class EmailCodeDto(val retryAfterSeconds: Int, val expiresInSeconds: Int)

@Serializable
data class EmailCaptchaConfigDto(
    val enabled: Boolean,
    val region: String = "cn",
    val prefix: String = "",
    val appSceneId: String? = null,
)

@Serializable
data class LoginCaptchaDto(val id: String, val image: String, val expiresInSeconds: Int)

@Serializable data class ResetGrantDto(val resetToken: String, val expiresInSeconds: Int)

@Serializable data class ApiErrorDto(val code: String = "", val message: String = "")

interface AuthApi {
    @POST("auth/login/email")
    suspend fun loginByEmail(@Body input: EmailLoginRequest): Response<ApiEnvelope<AuthSessionDto>>

    @POST("auth/login")
    suspend fun loginByPassword(
        @Body input: PasswordLoginRequest
    ): Response<ApiEnvelope<AuthSessionDto>>

    @GET("auth/email-captcha/config")
    suspend fun captchaConfig(): Response<ApiEnvelope<EmailCaptchaConfigDto>>

    @POST("auth/email-code")
    suspend fun sendCode(@Body input: EmailCodeRequest): Response<ApiEnvelope<EmailCodeDto>>

    @GET("auth/captcha") suspend fun loginCaptcha(): Response<ApiEnvelope<LoginCaptchaDto>>

    @POST("auth/refresh")
    suspend fun refresh(@Body input: RefreshRequest): Response<ApiEnvelope<AuthSessionDto>>

    @POST("auth/logout")
    suspend fun logout(@Body input: RefreshRequest): Response<ApiEnvelope<JsonElement>>

    @GET("auth/me")
    suspend fun currentUser(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<AuthUserDto>>

    @POST("auth/reset-password/verify-code")
    suspend fun verifyResetCode(
        @Body input: VerifyResetRequest
    ): Response<ApiEnvelope<ResetGrantDto>>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body input: ResetPasswordRequest): Response<ApiEnvelope<JsonElement>>
}
