package com.fittrace.core.data

import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.domain.SessionStore
import com.fittrace.core.model.*
import com.fittrace.core.network.*
import java.io.IOException
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DefaultAuthRepository(
    private val api: AuthApi,
    private val store: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) : AuthRepository {
    private val requests = AuthRequests()
    private val mutex = Mutex()
    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Loading)
    override val sessionStatus = status.asStateFlow()
    private var session: AuthSession? = null

    override suspend fun restoreSession(): AppResult<Unit> =
        mutex.withLock {
            val stored =
                try {
                    session ?: store.read()
                } catch (_: IOException) {
                    status.value = SessionStatus.RecoveryRequired(AppError.Storage)
                    return@withLock AppResult.Failure(AppError.Storage)
                }
            if (stored == null) {
                status.value = SessionStatus.SignedOut
                return@withLock AppResult.Success(Unit)
            }
            session = stored
            if (stored.accessTokenExpiresAt > now() + 30_000) {
                status.value = SessionStatus.SignedIn(stored.user)
                AppResult.Success(Unit)
            } else
                when (val result = refreshLocked(stored)) {
                    is AppResult.Success -> AppResult.Success(Unit)
                    is AppResult.Failure -> {
                        if (!result.error.isUnauthorized())
                            status.value = SessionStatus.RecoveryRequired(result.error)
                        result
                    }
                }
        }

    override suspend fun loginByEmail(
        email: String,
        code: String,
        acceptedTerms: Boolean,
    ): AppResult<Unit> {
        if (!acceptedTerms)
            return AppResult.Failure(AppError.Business("AGREEMENT_REQUIRED", "请先阅读并同意用户协议和隐私政策"))
        return mutex.withLock {
            commitResult(
                requests.call {
                    api.loginByEmail(EmailLoginRequest(email.normalized(), code, true))
                }
            )
        }
    }

    override suspend fun loginByPassword(
        email: String,
        password: String,
        captchaId: String?,
        captchaCode: String?,
    ): AppResult<Unit> =
        mutex.withLock {
            commitResult(
                requests.call {
                    api.loginByPassword(
                        PasswordLoginRequest(email.normalized(), password, captchaId, captchaCode)
                    )
                }
            )
        }

    override suspend fun captchaConfig(): AppResult<EmailCaptchaConfig> =
        requests
            .call { api.captchaConfig() }
            .map { EmailCaptchaConfig(it.enabled, it.region, it.prefix, it.appSceneId) }

    override suspend fun sendCode(
        email: String,
        purpose: EmailCodePurpose,
        captchaProof: String?,
    ): AppResult<EmailCodeReceipt> =
        requests
            .call {
                api.sendCode(
                    EmailCodeRequest(email.normalized(), purpose.name, captchaProof, "app")
                )
            }
            .map {
                require(it.retryAfterSeconds in 0..86400 && it.expiresInSeconds > 0)
                EmailCodeReceipt(it.retryAfterSeconds, it.expiresInSeconds)
            }

    override suspend fun loginCaptcha(): AppResult<LoginCaptcha> =
        requests
            .call { api.loginCaptcha() }
            .map { LoginCaptcha(it.id, it.image, it.expiresInSeconds) }

    override suspend fun verifyResetCode(email: String, code: String): AppResult<ResetGrant> =
        requests
            .call { api.verifyResetCode(VerifyResetRequest(email.normalized(), code)) }
            .map {
                require(it.expiresInSeconds in 1..86400)
                ResetGrant(it.resetToken, now() + it.expiresInSeconds * 1000L)
            }

    override suspend fun resetPassword(
        email: String,
        token: String,
        password: String,
    ): AppResult<Unit> =
        requests.unit {
            api.resetPassword(ResetPasswordRequest(email.normalized(), token, password))
        }

    override suspend fun currentUser(): AppResult<AuthUser> =
        mutex.withLock {
            var active = session ?: return@withLock AppResult.Failure(AppError.Http(401))
            if (active.accessTokenExpiresAt <= now() + 30_000) {
                when (val result = refreshLocked(active)) {
                    is AppResult.Failure -> return@withLock result
                    is AppResult.Success -> active = result.data
                }
            }
            var result = requests.call { api.currentUser("Bearer ${active.accessToken}") }
            if (result is AppResult.Failure && result.error.isUnauthorized()) {
                when (val refreshed = refreshLocked(active)) {
                    is AppResult.Failure -> return@withLock refreshed
                    is AppResult.Success -> active = refreshed.data
                }
                result = requests.call { api.currentUser("Bearer ${active.accessToken}") }
            }
            when (result) {
                is AppResult.Failure -> {
                    if (result.error.isUnauthorized()) {
                        val cleared = clearLocked()
                        if (cleared is AppResult.Failure) return@withLock cleared
                    }
                    result
                }
                is AppResult.Success -> {
                    val user = result.data.toModel()
                    when (val saved = saveLocked(active.copy(user = user))) {
                        is AppResult.Failure -> saved
                        is AppResult.Success -> AppResult.Success(user)
                    }
                }
            }
        }

    // 业务请求与会话刷新共用同一把锁，令牌只在 data/network 边界内使用。
    internal suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> =
        mutex.withLock {
            var active = session ?: return@withLock AppResult.Failure(AppError.Http(401))
            if (active.accessTokenExpiresAt <= now() + 30_000) {
                when (val refreshed = refreshLocked(active)) {
                    is AppResult.Failure -> return@withLock refreshed
                    is AppResult.Success -> active = refreshed.data
                }
            }
            var result = block("Bearer ${active.accessToken}")
            if (result is AppResult.Failure && result.error.isUnauthorized()) {
                when (val refreshed = refreshLocked(active)) {
                    is AppResult.Failure -> return@withLock refreshed
                    is AppResult.Success -> active = refreshed.data
                }
                result = block("Bearer ${active.accessToken}")
                if (result is AppResult.Failure && result.error.isUnauthorized()) {
                    val cleared = clearLocked()
                    if (cleared is AppResult.Failure) return@withLock cleared
                }
            }
            result
        }

    override suspend fun logout(): AppResult<Unit> {
        val old =
            mutex.withLock {
                val saved =
                    try {
                        session ?: store.read()
                    } catch (_: IOException) {
                        null
                    }
                when (val cleared = clearLocked()) {
                    is AppResult.Failure -> return cleared
                    is AppResult.Success -> saved
                }
            }
        return if (old == null) AppResult.Success(Unit)
        else requests.unit { api.logout(RefreshRequest(old.refreshToken)) }
    }

    private suspend fun commitResult(result: AppResult<AuthSessionDto>): AppResult<Unit> =
        when (result) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                try {
                    val dto = result.data
                    require(
                        dto.accessToken.isNotBlank() &&
                            dto.refreshToken.isNotBlank() &&
                            dto.user.id.isNotBlank() &&
                            dto.user.email.isNotBlank()
                    )
                    val expires = Instant.parse(dto.accessTokenExpiresAt).toEpochMilli()
                    require(expires > now())
                    saveLocked(
                        AuthSession(dto.accessToken, expires, dto.refreshToken, dto.user.toModel())
                    )
                } catch (_: java.time.DateTimeException) {
                    AppResult.Failure(AppError.InvalidResponse)
                } catch (_: IllegalArgumentException) {
                    AppResult.Failure(AppError.InvalidResponse)
                }
        }

    private suspend fun refreshLocked(old: AuthSession): AppResult<AuthSession> {
        val result = requests.call { api.refresh(RefreshRequest(old.refreshToken)) }
        return when (result) {
            is AppResult.Failure -> {
                if (result.error.isUnauthorized()) {
                    val cleared = clearLocked()
                    if (cleared is AppResult.Failure) return cleared
                }
                result
            }
            is AppResult.Success ->
                when (val saved = commitResult(result)) {
                    is AppResult.Failure -> saved
                    is AppResult.Success -> AppResult.Success(requireNotNull(session))
                }
        }
    }

    private suspend fun saveLocked(value: AuthSession): AppResult<Unit> =
        withContext(NonCancellable) {
            try {
                store.write(value)
                session = value
                status.value = SessionStatus.SignedIn(value.user)
                AppResult.Success(Unit)
            } catch (_: IOException) {
                AppResult.Failure(AppError.Storage)
            }
        }

    private suspend fun clearLocked(): AppResult<Unit> =
        withContext(NonCancellable) {
            try {
                store.clear()
                session = null
                status.value = SessionStatus.SignedOut
                AppResult.Success(Unit)
            } catch (_: IOException) {
                status.value = SessionStatus.RecoveryRequired(AppError.Storage)
                AppResult.Failure(AppError.Storage)
            }
        }
}

private fun String.normalized() = trim().lowercase(Locale.ROOT)

private fun AuthUserDto.toModel() = AuthUser(id, email, nickname, hasPassword)

private fun AppError.isUnauthorized(): Boolean =
    when (this) {
        is AppError.Http -> statusCode == 401
        is AppError.Business -> statusCode == 401 || code == "UNAUTHORIZED"
        else -> false
    }

internal inline fun <A, B> AppResult<A>.map(transform: (A) -> B): AppResult<B> =
    when (this) {
        is AppResult.Failure -> this
        is AppResult.Success ->
            try {
                AppResult.Success(transform(data))
            } catch (_: java.time.DateTimeException) {
                AppResult.Failure(AppError.InvalidResponse)
            } catch (_: IllegalArgumentException) {
                AppResult.Failure(AppError.InvalidResponse)
            }
    }
