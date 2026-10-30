package com.fittrace.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.*
import com.fittrace.core.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BiometricUiState(
    val device: BiometricState? = null,
    val busy: Boolean = false,
    val notice: String? = null,
    val needsEmail: Boolean = false,
    val error: AppError? = null,
    val statusConfirmed: Boolean = true,
)

class BiometricViewModel(
    private val repository: BiometricRepository,
    private val settings: Boolean,
) : ViewModel() {
    private val mutable = MutableStateFlow(BiometricUiState(statusConfirmed = false))
    val state = mutable.asStateFlow()
    private var operation: Job? = null

    init {
        refresh()
    }

    private fun run(
        action: suspend () -> AppResult<Unit>,
        success: String? = null,
        refreshing: Boolean = false,
    ) {
        if (state.value.busy) return
        val previous = state.value
        val keepError =
            refreshing &&
                (previous.error as? AppError.Business)?.code in
                    setOf(
                        "DEVICE_CREDENTIAL_INVALID",
                        "BIOMETRIC_INVALIDATED",
                        "BIOMETRIC_KEY_UNAVAILABLE",
                    )
        mutable.value =
            previous.copy(
                busy = true,
                notice = if (keepError) previous.notice else null,
                error = if (keepError) previous.error else null,
                statusConfirmed = if (refreshing) false else previous.statusConfirmed,
            )
        operation =
            viewModelScope.launch {
                try {
                    val result = action()
                    if (result is AppResult.Failure) {
                        val error = result.error
                        mutable.value =
                            state.value.copy(
                                notice = error.message(),
                                error = error,
                                needsEmail =
                                    state.value.needsEmail ||
                                        (error is AppError.Business &&
                                            error.code == "EMAIL_VERIFICATION_REQUIRED"),
                            )
                    } else if (!refreshing) {
                        mutable.value =
                            state.value.copy(notice = success, error = null, needsEmail = false)
                    }
                    if (!refreshing) {
                        val refreshed = updateDevice()
                        if (result is AppResult.Success && refreshed is AppResult.Failure)
                            mutable.value =
                                state.value.copy(
                                    notice = refreshed.error.message(),
                                    error = refreshed.error,
                                )
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    mutable.value =
                        state.value.copy(
                            notice = "暂时无法读取本机登录状态，请重试",
                            error = AppError.Storage,
                            statusConfirmed = false,
                        )
                } finally {
                    mutable.value = state.value.copy(busy = false)
                }
            }
    }

    fun refresh() =
        run(
            action = {
                val local = repository.status(false)
                if (local is AppResult.Success)
                    mutable.value = state.value.copy(device = local.data)
                updateDevice()
            },
            refreshing = true,
        )

    private suspend fun updateDevice(): AppResult<Unit> =
        when (val result = repository.status(settings)) {
            is AppResult.Success -> {
                mutable.value = state.value.copy(device = result.data, statusConfirmed = true)
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> {
                mutable.value = state.value.copy(statusConfirmed = false)
                result
            }
        }

    fun dismissEmail() {
        cancel()
        mutable.value = state.value.copy(needsEmail = false, notice = null, error = null)
    }

    fun enable(code: String, signer: BiometricSigner) =
        run({ repository.enable(code, signer) }, "已开启生物识别登录")

    fun login(signer: BiometricSigner) = run({ repository.login(signer) })

    fun disable() = run({ repository.disable() }, "已关闭本机生物识别登录")

    fun remove() = run({ repository.removeLocal() }, "本机凭据已移除；服务端设备记录需登录原账号后管理")

    fun cancel() {
        operation?.cancel()
    }
}
