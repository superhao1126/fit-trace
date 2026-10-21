package com.fittrace.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.model.*
import java.time.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordItem(
    val id: String,
    val kind: RecordKind,
    val title: String,
    val summary: String,
    val value: String,
    val time: String,
    val image: String? = null,
)

data class RecordsState(
    val items: List<RecordItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val page: Int = 0,
    val total: Int = 0,
)

class RecordsViewModel(private val repository: RecordsRepository) : ViewModel() {
    private val mutable = MutableStateFlow(RecordsState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var kind: RecordKind? = null
    private var date: LocalDate? = null
    private val pages = mutableMapOf<RecordKind, Int>()
    private val totals = mutableMapOf<RecordKind, Int>()

    fun select(kind: RecordKind?, date: LocalDate?, force: Boolean = false) {
        if (!force && this.kind == kind && this.date == date && mutable.value.page > 0) return
        val sameContext = this.kind == kind && this.date == date
        this.kind = kind
        this.date = date
        request?.cancel()
        pages.clear()
        totals.clear()
        mutable.value =
            if (sameContext) mutable.value.copy(loading = true, error = null) else RecordsState()
        load()
    }

    fun refresh() = select(kind, date, true)

    fun load() {
        if (mutable.value.loading && request?.isActive == true) return
        mutable.value = mutable.value.copy(loading = true, error = null)
        val replacing = pages.isEmpty()
        val selectedKind = kind
        val selectedDate = date
        request =
            viewModelScope.launch {
                val zone = ZoneId.systemDefault()
                val from = selectedDate?.atStartOfDay(zone)?.toOffsetDateTime()?.toString()
                val to =
                    selectedDate?.plusDays(1)?.atStartOfDay(zone)?.toOffsetDateTime()?.toString()
                val pending = mutableListOf<RecordItem>()
                val nextPages = pages.toMutableMap()
                val nextTotals = totals.toMutableMap()
                var failure: AppError? = null
                (selectedKind?.let { listOf(it) } ?: RecordKind.entries).forEach { category ->
                    val page = (pages[category] ?: 0) + 1
                    if (page > 1 && (page - 1) * 20 >= (totals[category] ?: 0)) return@forEach
                    val result =
                        when (category) {
                            RecordKind.BODY -> repository.bodies(page, from, to).items { it.item() }
                            RecordKind.MEAL -> repository.meals(page, from, to).items { it.item() }
                            RecordKind.WORKOUT ->
                                repository.workouts(page, from, to).items { it.item() }
                            RecordKind.PHOTO ->
                                repository.photos(page, from, to).items { it.item() }
                        }
                    when (result) {
                        is AppResult.Success -> {
                            pending += result.data.entries
                            nextPages[category] = page
                            nextTotals[category] = result.data.total
                        }
                        is AppResult.Failure -> failure = result.error
                    }
                }
                // 一页中的所有类型成功后再提交，重试不会丢页或重复追加。
                if (failure != null)
                    mutable.value =
                        mutable.value.copy(loading = false, error = failure!!.recordMessage())
                else {
                    pages.putAll(nextPages)
                    totals.putAll(nextTotals)
                    val items =
                        (if (replacing) pending else mutable.value.items + pending)
                            .distinctBy { it.kind to it.id }
                            .sortedByDescending { OffsetDateTime.parse(it.time).toInstant() }
                    mutable.value =
                        RecordsState(
                            items,
                            false,
                            page = (pages.values.maxOrNull() ?: 1),
                            total = totals.values.sum(),
                        )
                }
            }
    }
}

private fun <T> AppResult<RecordPage<T>>.items(
    convert: (T) -> RecordItem
): AppResult<RecordPage<RecordItem>> =
    when (this) {
        is AppResult.Failure -> this
        is AppResult.Success -> AppResult.Success(RecordPage(data.entries.map(convert), data.total))
    }

internal fun BodyEntry.item() =
    RecordItem(
        id,
        RecordKind.BODY,
        "身体数据",
        listOfNotNull(bodyFat?.let { "体脂 $it%" }, waist?.let { "腰围 $it cm" }, note)
            .joinToString(" · "),
        "${weight.number()} kg",
        recordedAt,
    )

internal fun MealEntry.item() =
    RecordItem(
        id,
        RecordKind.MEAL,
        mealLabels[type] ?: "饮食",
        foods.joinToString("、") { it.name },
        totalCalories?.let { "${it.number()} kcal" } ?: "热量待补充",
        recordedAt,
        imageUrl,
    )

internal fun WorkoutEntry.item() =
    RecordItem(
        id,
        RecordKind.WORKOUT,
        name,
        workoutLabels[type] ?: "运动",
        "$durationMinutes 分钟",
        startedAt,
    )

internal fun PhotoEntry.item() =
    RecordItem(
        id,
        RecordKind.PHOTO,
        photoLabels[type] ?: "身材照片",
        note.orEmpty(),
        "",
        recordedAt,
        imageUrl,
    )

internal val mealLabels =
    linkedMapOf("BREAKFAST" to "早餐", "LUNCH" to "午餐", "DINNER" to "晚餐", "SNACK" to "加餐")
internal val workoutLabels =
    linkedMapOf(
        "RUNNING" to "跑步",
        "CYCLING" to "骑行",
        "SWIMMING" to "游泳",
        "CARDIO" to "有氧运动",
        "STRENGTH" to "力量训练",
        "OTHER" to "其他运动",
    )
internal val photoLabels =
    linkedMapOf("FRONT" to "正面", "SIDE" to "侧面", "BACK" to "背面", "OTHER" to "其他")

internal fun Double?.number(): String =
    this?.let {
        if (it % 1 == 0.0) it.toLong().toString()
        else String.format(java.util.Locale.ROOT, "%.1f", it)
    } ?: "—"

internal fun AppError.recordMessage(): String =
    when (this) {
        AppError.Network -> "网络连接失败，请检查网络后重试"
        is AppError.Business ->
            if (statusCode == 401 || code == "UNAUTHORIZED") "登录已失效，请重新登录"
            else message.ifBlank { "操作失败，请重试" }
        is AppError.Http -> if (statusCode == 401) "登录已失效，请重新登录" else "服务暂不可用，请重试"
        else -> "数据暂不可用，请重试"
    }
