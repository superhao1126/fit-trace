package com.fittrace.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.model.*
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state = mutableState.asStateFlow()
    private val cooldowns = mutableMapOf<String, Long>()
    private var cooldownJob: Job? = null
    private var resetGrant: ResetGrant? = null
    private var pendingSend: PendingSend? = null
    private var challengeSequence = 0L

    private data class PendingSend(val email: String, val purpose: EmailCodePurpose)

    fun setEmail(value: String) {
        if (state.value.busy || state.value.mode == AuthMode.RESET_PASSWORD) return
        mutableState.update {
            it.copy(
                email = value.take(254),
                code = "",
                errors = it.errors - AuthField.EMAIL - AuthField.CODE,
                notice = null,
            )
        }
        updateCountdown()
    }

    fun setCode(value: String) {
        if (!state.value.busy)
            mutableState.update {
                it.copy(
                    code = value.filter(Char::isDigit).take(6),
                    errors = it.errors - AuthField.CODE,
                )
            }
    }

    fun setPassword(value: String) {
        if (!state.value.busy)
            mutableState.update {
                it.copy(password = value.take(73), errors = it.errors - AuthField.PASSWORD)
            }
    }

    fun setConfirmPassword(value: String) {
        if (!state.value.busy)
            mutableState.update {
                it.copy(
                    confirmPassword = value.take(73),
                    errors = it.errors - AuthField.CONFIRM_PASSWORD,
                )
            }
    }

    fun setCaptcha(value: String) {
        if (!state.value.busy)
            mutableState.update {
                it.copy(
                    captchaInput = value.filter { c -> c.isLetterOrDigit() }.take(4),
                    errors = it.errors - AuthField.CAPTCHA,
                )
            }
    }

    fun acceptTerms(value: Boolean) {
        if (!state.value.busy)
            mutableState.update {
                it.copy(acceptedTerms = value, errors = it.errors - AuthField.AGREEMENT)
            }
    }

    fun togglePassword() {
        mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun openPolicy(kind: PolicyKind?) {
        mutableState.update { it.copy(policy = kind) }
    }

    fun switchMode(mode: AuthMode) {
        if (state.value.busy || mode == AuthMode.RESET_PASSWORD) return
        resetGrant = null
        mutableState.update {
            it.copy(
                mode = mode,
                password = "",
                confirmPassword = "",
                code = "",
                captchaInput = "",
                passwordVisible = false,
                notice = null,
                errors = emptyMap(),
            )
        }
        updateCountdown()
    }

    fun back() {
        if (!state.value.busy)
            switchMode(
                if (state.value.mode == AuthMode.RESET_PASSWORD) AuthMode.RESET_EMAIL
                else AuthMode.PASSWORD
            )
    }

    fun requestCode() {
        val s = state.value
        if (s.busy || s.countdown > 0 || s.mode !in listOf(AuthMode.EMAIL, AuthMode.RESET_EMAIL))
            return
        val errors = validateEmail(s.email).toMutableMap()
        // 在加载第三方安全验证 SDK 前取得登录页上的协议同意。
        if (s.mode == AuthMode.EMAIL && !s.acceptedTerms)
            errors[AuthField.AGREEMENT] = "请先阅读并同意用户协议和隐私政策"
        if (errors.isNotEmpty()) {
            mutableState.update { it.copy(errors = errors) }
            return
        }
        pendingSend = PendingSend(s.email.normalized(), purpose())
        mutableState.update {
            it.copy(busy = true, sendingCode = true, notice = null, errors = emptyMap())
        }
        viewModelScope.launch {
            when (val config = repository.captchaConfig()) {
                is AppResult.Failure -> {
                    pendingSend = null
                    report(config.error)
                    finishBusy()
                }
                is AppResult.Success -> {
                    if (!config.data.enabled) sendPreparedCode(null)
                    else if (
                        config.data.appSceneId.isNullOrBlank() ||
                            config.data.prefix.isBlank() ||
                            config.data.region !in listOf("cn", "sgp")
                    ) {
                        pendingSend = null
                        report(AppError.Business("EMAIL_CAPTCHA_UNAVAILABLE", "安全验证暂不可用，请稍后重试"))
                        finishBusy()
                    } else
                        mutableState.update {
                            it.copy(challenge = CaptchaChallenge(++challengeSequence, config.data))
                        }
                }
            }
        }
    }

    fun captchaCompleted(id: Long, proof: String?) {
        if (state.value.challenge?.id != id) return
        mutableState.update { it.copy(challenge = null) }
        if (proof.isNullOrBlank() || proof.length > 65536) {
            pendingSend = null
            finishBusy()
            return
        }
        viewModelScope.launch { sendPreparedCode(proof) }
    }

    fun captchaFailed(id: Long, message: String) {
        if (state.value.challenge?.id != id) return
        pendingSend = null
        mutableState.update { it.copy(challenge = null, notice = message) }
        finishBusy()
    }

    private suspend fun sendPreparedCode(proof: String?) {
        val request = pendingSend ?: return
        pendingSend = null
        when (val result = repository.sendCode(request.email, request.purpose, proof)) {
            is AppResult.Failure -> report(result.error)
            is AppResult.Success -> {
                cooldowns[key(request.email, request.purpose)] =
                    now() + result.data.retryAfterSeconds * 1000L
                mutableState.update {
                    it.copy(
                        notice =
                            if (request.purpose == EmailCodePurpose.LOGIN) "验证码已发送，请查收邮箱"
                            else "若邮箱可用，验证码将发送至你的邮箱"
                    )
                }
                updateCountdown()
            }
        }
        finishBusy()
    }

    fun submit() {
        val s = state.value
        if (s.busy) return
        val errors = validateEmail(s.email).toMutableMap()
        if (
            s.mode in listOf(AuthMode.EMAIL, AuthMode.RESET_EMAIL) &&
                !s.code.matches(Regex("[0-9]{6}"))
        )
            errors[AuthField.CODE] = "请输入 6 位邮箱验证码"
        if (
            s.mode in listOf(AuthMode.PASSWORD, AuthMode.RESET_PASSWORD) &&
                s.password.length !in 8..72
        )
            errors[AuthField.PASSWORD] = "密码需要 8–72 位"
        if (s.mode == AuthMode.RESET_PASSWORD && s.password != s.confirmPassword)
            errors[AuthField.CONFIRM_PASSWORD] = "两次输入的密码不一致"
        if (s.mode in listOf(AuthMode.EMAIL, AuthMode.PASSWORD) && !s.acceptedTerms)
            errors[AuthField.AGREEMENT] = "请先阅读并同意用户协议和隐私政策"
        if (
            s.mode == AuthMode.PASSWORD &&
                s.captchaRequired &&
                (s.loginCaptcha == null || !s.captchaInput.matches(Regex("[a-zA-Z0-9]{4}")))
        )
            errors[AuthField.CAPTCHA] = "请输入图片中的 4 位字符"
        if (errors.isNotEmpty()) {
            mutableState.update { it.copy(errors = errors) }
            return
        }
        mutableState.update { it.copy(busy = true, notice = null, errors = emptyMap()) }
        viewModelScope.launch {
            val result =
                when (s.mode) {
                    AuthMode.EMAIL ->
                        repository.loginByEmail(s.email.normalized(), s.code, s.acceptedTerms)
                    AuthMode.PASSWORD ->
                        repository.loginByPassword(
                            s.email.normalized(),
                            s.password,
                            if (s.captchaRequired) s.loginCaptcha?.id else null,
                            if (s.captchaRequired) s.captchaInput else null,
                        )
                    AuthMode.RESET_EMAIL ->
                        when (
                            val grant = repository.verifyResetCode(s.email.normalized(), s.code)
                        ) {
                            is AppResult.Failure -> grant
                            is AppResult.Success -> {
                                resetGrant = grant.data
                                mutableState.update {
                                    it.copy(
                                        mode = AuthMode.RESET_PASSWORD,
                                        password = "",
                                        confirmPassword = "",
                                        code = "",
                                    )
                                }
                                AppResult.Success(Unit)
                            }
                        }
                    AuthMode.RESET_PASSWORD -> {
                        val grant = resetGrant
                        if (grant == null || grant.expiresAt <= now()) {
                            resetGrant = null
                            mutableState.update {
                                it.copy(
                                    mode = AuthMode.RESET_EMAIL,
                                    password = "",
                                    confirmPassword = "",
                                    code = "",
                                )
                            }
                            AppResult.Failure(
                                AppError.Business("RESET_VERIFICATION_INVALID", "邮箱验证已过期，请重新验证")
                            )
                        } else
                            repository.resetPassword(s.email.normalized(), grant.token, s.password)
                    }
                }
            if (result is AppResult.Failure) {
                report(result.error)
                if (s.mode == AuthMode.PASSWORD && state.value.captchaRequired) loadLoginCaptcha()
            } else if (s.mode == AuthMode.RESET_PASSWORD) {
                resetGrant = null
                mutableState.update {
                    it.copy(
                        mode = AuthMode.PASSWORD,
                        password = "",
                        confirmPassword = "",
                        passwordVisible = false,
                        notice = "密码已更新，请使用新密码登录",
                    )
                }
            } else if (s.mode in listOf(AuthMode.EMAIL, AuthMode.PASSWORD)) {
                mutableState.update { it.copy(password = "", code = "", captchaInput = "") }
            }
            finishBusy()
        }
    }

    fun refreshLoginCaptcha() {
        if (state.value.captchaLoading || state.value.busy) return
        viewModelScope.launch { loadLoginCaptcha() }
    }

    private suspend fun loadLoginCaptcha() {
        mutableState.update {
            it.copy(captchaLoading = true, loginCaptcha = null, captchaInput = "")
        }
        when (val result = repository.loginCaptcha()) {
            is AppResult.Success -> mutableState.update { it.copy(loginCaptcha = result.data) }
            is AppResult.Failure -> mutableState.update { it.copy(notice = "图形验证加载失败，请点击重试") }
        }
        mutableState.update { it.copy(captchaLoading = false) }
    }

    private fun report(error: AppError) {
        val business = error as? AppError.Business
        val message = error.message()
        var errors = state.value.errors
        when (business?.code) {
            "EMAIL_CODE_INVALID",
            "EMAIL_CODE_EXPIRED" -> errors = errors + (AuthField.CODE to message)
            "INVALID_CREDENTIALS" -> errors = errors + (AuthField.PASSWORD to message)
            "AGREEMENT_REQUIRED" -> errors = errors + (AuthField.AGREEMENT to message)
            "LOGIN_CAPTCHA_REQUIRED",
            "CAPTCHA_INVALID" -> {
                errors = errors + (AuthField.CAPTCHA to message)
                mutableState.update { it.copy(captchaRequired = true) }
            }
            "RESET_VERIFICATION_INVALID" -> {
                resetGrant = null
                mutableState.update {
                    it.copy(
                        mode = AuthMode.RESET_EMAIL,
                        password = "",
                        confirmPassword = "",
                        code = "",
                    )
                }
                errors = errors + (AuthField.CODE to message)
            }
        }
        val retryAfter = business?.retryAfterSeconds
        if (retryAfter != null && state.value.sendingCode) {
            cooldowns[key(state.value.email.normalized(), purpose())] = now() + retryAfter * 1000L
            updateCountdown()
        }
        mutableState.update { it.copy(errors = errors, notice = message) }
    }

    private fun updateCountdown() {
        cooldownJob?.cancel()
        val until = cooldowns[key(state.value.email.normalized(), purpose())] ?: 0
        fun update() {
            mutableState.update {
                it.copy(countdown = (((until - now()).coerceAtLeast(0) + 999) / 1000).toInt())
            }
        }
        update()
        if (state.value.countdown > 0)
            cooldownJob =
                viewModelScope.launch {
                    while (state.value.countdown > 0) {
                        delay(1000)
                        update()
                    }
                }
    }

    private fun purpose() =
        if (state.value.mode in listOf(AuthMode.RESET_EMAIL, AuthMode.RESET_PASSWORD))
            EmailCodePurpose.RESET_PASSWORD
        else EmailCodePurpose.LOGIN

    private fun key(email: String, purpose: EmailCodePurpose) = "${purpose.name}:$email"

    private fun finishBusy() {
        mutableState.update { it.copy(busy = false, sendingCode = false) }
    }
}

private fun String.normalized() = trim().lowercase(Locale.ROOT)

private fun validateEmail(email: String): Map<AuthField, String> =
    when {
        email.isBlank() -> mapOf(AuthField.EMAIL to "请输入邮箱地址")
        !email.trim().matches(Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")) ->
            mapOf(AuthField.EMAIL to "请输入正确的邮箱地址")
        else -> emptyMap()
    }

fun AppError.message(): String =
    when (this) {
        AppError.Network -> "网络连接异常，请检查网络后重试"
        AppError.Storage -> "无法保存登录状态，请检查可用空间后重试"
        AppError.InvalidResponse -> "服务返回异常，请稍后重试"
        is AppError.Business -> message.ifBlank { "操作失败，请稍后重试" }
        is AppError.Http ->
            when (statusCode) {
                401 -> "登录已失效，请重新登录"
                429 -> "操作过于频繁，请稍后重试"
                else -> "服务暂不可用，请稍后重试"
            }
    }
