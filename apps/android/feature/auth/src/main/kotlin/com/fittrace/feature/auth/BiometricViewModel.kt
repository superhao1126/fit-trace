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
)

class BiometricViewModel(
    private val repository: BiometricRepository,
    private val settings: Boolean,
) : ViewModel() {
    private val mutable = MutableStateFlow(BiometricUiState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null

    init {
        refresh()
    }

    private fun run(action: suspend () -> AppResult<Unit>, success: String? = null) {
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true, notice = null)
        operation =
            viewModelScope.launch {
                try {
                    val result = action()
                    if (result is AppResult.Failure) {
                        val error = result.error
                        mutable.value =
                            state.value.copy(
                                notice = error.message(),
                                needsEmail =
                                    state.value.needsEmail ||
                                        (error is AppError.Business &&
                                            error.code == "EMAIL_VERIFICATION_REQUIRED"),
                            )
                    } else mutable.value = state.value.copy(notice = success, needsEmail = false)
                    val refreshed = repository.status(settings)
                    if (refreshed is AppResult.Success)
                        mutable.value = state.value.copy(device = refreshed.data)
                    else if (result is AppResult.Success && refreshed is AppResult.Failure)
                        mutable.value = state.value.copy(notice = refreshed.error.message())
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    mutable.value = state.value.copy(notice = "暂时无法读取本机凭据，请重试")
                } finally {
                    mutable.value = state.value.copy(busy = false)
                }
            }
    }

    fun refresh() =
        run({
            val local = repository.status(false)
            if (local is AppResult.Success)
                mutable.value =
                    state.value.copy(
                        device =
                            local.data.copy(enabled = if (settings) false else local.data.enabled)
                    )
            when (val result = repository.status(settings)) {
                is AppResult.Success -> {
                    mutable.value = state.value.copy(device = result.data)
                    AppResult.Success(Unit)
                }
                is AppResult.Failure -> result
            }
        })

    fun enable(code: String, signer: BiometricSigner) =
        run({ repository.enable(code, signer) }, "已开启生物识别登录")

    fun login(signer: BiometricSigner) = run({ repository.login(signer) })

    fun disable() = run({ repository.disable() }, "已关闭本机生物识别登录")

    fun remove() = run({ repository.removeLocal() }, "本机凭据已移除；服务端设备记录需登录原账号后管理")

    fun cancel() {
        operation?.cancel()
    }
}
