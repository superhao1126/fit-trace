package com.fittrace.feature.auth

import com.fittrace.core.model.EmailCaptchaConfig
import com.fittrace.core.model.LoginCaptcha

enum class AuthMode {
    EMAIL,
    PASSWORD,
    RESET_EMAIL,
    RESET_PASSWORD,
}

enum class AuthField {
    EMAIL,
    CODE,
    PASSWORD,
    CONFIRM_PASSWORD,
    AGREEMENT,
    CAPTCHA,
}

data class CaptchaChallenge(val id: Long, val config: EmailCaptchaConfig)

data class AuthUiState(
    val mode: AuthMode = AuthMode.EMAIL,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val acceptedTerms: Boolean = false,
    val passwordVisible: Boolean = false,
    val errors: Map<AuthField, String> = emptyMap(),
    val busy: Boolean = false,
    val sendingCode: Boolean = false,
    val countdown: Int = 0,
    val notice: String? = null,
    val challenge: CaptchaChallenge? = null,
    val loginCaptcha: LoginCaptcha? = null,
    val captchaInput: String = "",
    val captchaRequired: Boolean = false,
    val captchaLoading: Boolean = false,
    val policy: PolicyKind? = null,
) {
    // UI 状态含密码和验证码，只驻留内存，避免调试日志意外打印凭据。
    override fun toString(): String = "AuthUiState(mode=$mode, busy=$busy, <redacted>)"
}

enum class PolicyKind {
    AGREEMENT,
    PRIVACY,
}
