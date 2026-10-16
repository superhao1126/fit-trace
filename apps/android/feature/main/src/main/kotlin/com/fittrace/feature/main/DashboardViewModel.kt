package com.fittrace.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.DashboardRepository
import com.fittrace.core.model.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val date: LocalDate = LocalDate.now(),
    val data: Dashboard? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class DashboardViewModel(
    private val repository: DashboardRepository,
    private val timezone: () -> ZoneId = ZoneId::systemDefault,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutable = MutableStateFlow(DashboardUiState(date = today()))
    val state = mutable.asStateFlow()
    private var request: Job? = null

    init {
        refresh()
    }

    fun selectDate(date: LocalDate) {
        if (date > today() || date == mutable.value.date) return
        request?.cancel()
        mutable.value = DashboardUiState(date = date)
        refresh()
    }

    fun refresh() {
        request?.cancel()
        val date = mutable.value.date
        mutable.value = mutable.value.copy(loading = true, error = null)
        request =
            viewModelScope.launch {
                // API 约定 UTC 以东为正；Java ZoneOffset 已使用该符号，不能再取反。
                val offset = date.atStartOfDay(timezone()).offset.totalSeconds / 60
                when (val result = repository.overview(date.toString(), offset)) {
                    is AppResult.Success ->
                        mutable.value = DashboardUiState(date, result.data, loading = false)
                    is AppResult.Failure ->
                        mutable.value =
                            mutable.value.copy(
                                loading = false,
                                error =
                                    when (val error = result.error) {
                                        AppError.Network -> "网络连接失败，请稍后重试"
                                        is AppError.Http ->
                                            if (error.statusCode == 401) "登录已失效，请重新登录"
                                            else "暂时无法加载记录，请重试"
                                        is AppError.Business ->
                                            if (
                                                error.statusCode == 401 ||
                                                    error.code == "UNAUTHORIZED"
                                            )
                                                "登录已失效，请重新登录"
                                            else "暂时无法加载记录，请重试"
                                        else -> "记录数据暂不可用，请重试"
                                    },
                            )
                }
            }
    }
}
