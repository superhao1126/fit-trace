package com.fittrace.core.model

data class AuthUser(
    val id: String,
    val email: String,
    val nickname: String?,
    val hasPassword: Boolean?,
)

data class AuthSession(
    val accessToken: String,
    val accessTokenExpiresAt: Long,
    val refreshToken: String,
    val user: AuthUser,
) {
    override fun toString(): String = "AuthSession(<redacted>)"
}

sealed interface SessionStatus {
    data object Loading : SessionStatus

    data object SignedOut : SessionStatus

    data class SignedIn(val user: AuthUser) : SessionStatus

    data class RecoveryRequired(val error: AppError) : SessionStatus
}

enum class EmailCodePurpose {
    LOGIN,
    RESET_PASSWORD,
}

data class EmailCodeReceipt(val retryAfterSeconds: Int, val expiresInSeconds: Int)

data class EmailCaptchaConfig(
    val enabled: Boolean,
    val region: String = "cn",
    val prefix: String = "",
    val appSceneId: String? = null,
)

data class LoginCaptcha(val id: String, val image: String, val expiresInSeconds: Int)

data class ResetGrant(val token: String, val expiresAt: Long)
