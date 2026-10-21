package com.fittrace.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.ProfileRepository
import com.fittrace.core.model.*
import java.time.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val body: ProfileBody? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val bodyError: String? = null,
    val saving: Boolean = false,
    val formError: String? = null,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val avatarError: String? = null,
    val canRetryAvatar: Boolean = false,
    val gender: UserGender = UserGender.UNSPECIFIED,
    val goalType: GoalType = GoalType.LOSE_FAT,
    val targetWeight: String = "",
    val currentWeight: String = "",
    val targetDate: String? = null,
    val editingWeight: Boolean = false,
)

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state = mutable.asStateFlow()
    private var loadingJob: Job? = null
    private var pendingAvatar: Pair<ByteArray, String>? = null

    fun load() {
        if (mutable.value.saving) return
        loadingJob?.cancel()
        mutable.value = mutable.value.copy(loading = true, error = null, bodyError = null)
        loadingJob =
            viewModelScope.launch {
                when (val result = repository.profile()) {
                    is AppResult.Failure -> {
                        mutable.value =
                            mutable.value.copy(
                                loading = false,
                                error = result.error.profileMessage(),
                            )
                        return@launch
                    }
                    is AppResult.Success ->
                        mutable.value = mutable.value.copy(profile = result.data)
                }
                when (val result = repository.latestBody()) {
                    is AppResult.Success ->
                        mutable.value = mutable.value.copy(body = result.data, loading = false)
                    is AppResult.Failure ->
                        mutable.value =
                            mutable.value.copy(bodyError = "体重暂时无法读取，请重试", loading = false)
                }
            }
    }

    fun beginProfile() {
        val user = mutable.value.profile ?: return
        pendingAvatar = null
        mutable.value =
            mutable.value.copy(
                nickname = user.nickname.orEmpty(),
                avatarUrl = user.avatarUrl,
                avatarError = null,
                canRetryAvatar = false,
                gender = user.gender,
                formError = null,
            )
    }

    fun avatar(value: String?) {
        if (mutable.value.saving) return
        pendingAvatar = null
        mutable.value =
            mutable.value.copy(
                avatarUrl = value,
                avatarError = null,
                canRetryAvatar = false,
                formError = null,
            )
    }

    fun uploadAvatar(
        records: com.fittrace.core.domain.RecordsRepository,
        bytes: ByteArray,
        mime: String,
    ) {
        if (mutable.value.saving) return
        pendingAvatar = bytes to mime
        mutable.value =
            mutable.value.copy(
                saving = true,
                avatarError = null,
                canRetryAvatar = false,
                formError = null,
            )
        viewModelScope.launch {
            when (val result = records.upload(bytes, mime)) {
                is AppResult.Success -> {
                    pendingAvatar = null
                    mutable.value = mutable.value.copy(avatarUrl = result.data, saving = false)
                }
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(
                            saving = false,
                            avatarError = result.error.profileMessage(),
                            canRetryAvatar = true,
                        )
            }
        }
    }

    fun retryAvatar(records: com.fittrace.core.domain.RecordsRepository) {
        val pending = pendingAvatar ?: return
        uploadAvatar(records, pending.first, pending.second)
    }

    fun uploadError(message: String) {
        pendingAvatar = null
        mutable.value = mutable.value.copy(avatarError = message, canRetryAvatar = false)
    }

    fun nickname(value: String) {
        if (!mutable.value.saving)
            mutable.value = mutable.value.copy(nickname = value, formError = null)
    }

    fun gender(value: UserGender) {
        if (!mutable.value.saving)
            mutable.value = mutable.value.copy(gender = value, formError = null)
    }

    fun beginGoal() {
        val s = mutable.value
        mutable.value =
            s.copy(
                goalType = s.profile?.goal?.type ?: GoalType.LOSE_FAT,
                targetWeight = s.profile?.goal?.targetWeight?.weightText().orEmpty(),
                currentWeight = s.body?.weight?.weightText().orEmpty(),
                targetDate =
                    s.profile?.goal?.targetDate?.let {
                        OffsetDateTime.parse(it)
                            .atZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    },
                editingWeight = s.body == null,
                formError = null,
            )
    }

    fun goalType(value: GoalType) {
        if (!mutable.value.saving)
            mutable.value =
                mutable.value.copy(
                    goalType = value,
                    targetWeight =
                        if (value == GoalType.MAINTAIN && mutable.value.currentWeight.isNotBlank())
                            mutable.value.currentWeight
                        else mutable.value.targetWeight,
                    formError = null,
                )
    }

    fun targetWeight(value: String) {
        if (!mutable.value.saving)
            mutable.value = mutable.value.copy(targetWeight = value, formError = null)
    }

    fun currentWeight(value: String) {
        if (!mutable.value.saving)
            mutable.value = mutable.value.copy(currentWeight = value, formError = null)
    }

    fun targetDate(value: String?) {
        if (!mutable.value.saving)
            mutable.value = mutable.value.copy(targetDate = value, formError = null)
    }

    fun editWeight() {
        if (!mutable.value.saving) mutable.value = mutable.value.copy(editingWeight = true)
    }

    fun saveProfile(onSuccess: () -> Unit) {
        val s = mutable.value
        if (s.saving || s.loading || s.profile == null || s.avatarError != null) return
        val name = s.nickname.trim()
        if (name.isBlank() || name.length > 40) {
            mutable.value = s.copy(formError = "昵称长度必须为 1 到 40 个字符")
            return
        }
        save(
            {
                repository.updateProfile(
                    ProfileUpdate(name, s.gender, s.avatarUrl, s.avatarUrl != s.profile?.avatarUrl)
                )
            },
            onSuccess,
        )
    }

    fun saveGoal(onSuccess: () -> Unit) {
        val s = mutable.value
        if (s.saving || s.loading || s.profile == null) return
        val current = s.currentWeight.toDoubleOrNull()
        val target = s.targetWeight.toDoubleOrNull()
        val validNumber: (String, Double?) -> Boolean = { text, value ->
            value != null &&
                value.isFinite() &&
                value in 1.0..500.0 &&
                Regex("\\d+(\\.\\d{1,2})?").matches(text)
        }
        val date = s.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val error =
            when {
                !validNumber(s.currentWeight, current) -> "请输入 1–500 kg 的当前体重，最多两位小数"
                !validNumber(s.targetWeight, target) -> "请输入 1–500 kg 的目标体重，最多两位小数"
                s.goalType == GoalType.LOSE_FAT && target!! >= current!! -> "减脂目标应低于当前体重"
                s.goalType == GoalType.GAIN_MUSCLE && target!! <= current!! -> "增肌目标应高于当前体重"
                s.bodyError != null && !s.editingWeight -> "请先重新读取体重，或手动填写当前体重"
                s.targetDate != null && (date == null || date < today()) -> "目标日期不能早于今天"
                else -> null
            }
        if (error != null) {
            mutable.value = s.copy(formError = error)
            return
        }
        // 用户主动填写新体重时才提交 currentWeight；后端会同时新增身体记录。
        val isoDate =
            date
                ?.plusDays(1)
                ?.atStartOfDay(ZoneId.systemDefault())
                ?.minusNanos(1)
                ?.toInstant()
                ?.toString()
        save(
            {
                repository.updateGoal(
                    GoalUpdate(
                        s.goalType,
                        target!!,
                        isoDate,
                        if (s.editingWeight) current else null,
                    )
                )
            },
            onSuccess,
        )
    }

    private fun save(block: suspend () -> AppResult<UserProfile>, onSuccess: () -> Unit) {
        mutable.value = mutable.value.copy(saving = true, formError = null)
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(
                            saving = false,
                            formError = result.error.profileMessage(),
                        )
                is AppResult.Success -> {
                    mutable.value = mutable.value.copy(saving = false, profile = result.data)
                    onSuccess()
                    load()
                }
            }
        }
    }
}

internal fun Double.weightText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun AppError.profileMessage(): String =
    when (this) {
        AppError.Network -> "网络连接失败，请稍后重试"
        is AppError.Business ->
            if (statusCode == 401 || code == "UNAUTHORIZED") "登录已失效，请重新登录"
            else message.ifBlank { "请求失败，请稍后重试" }
        is AppError.Http -> if (statusCode == 401) "登录已失效，请重新登录" else "暂时无法完成请求，请稍后重试"
        else -> "数据暂不可用，请重试"
    }
