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

data class RecordDraft(
    val kind: RecordKind,
    val id: String? = null,
    val time: String,
    val weight: String = "",
    val bodyFat: String = "",
    val waist: String = "",
    val chest: String = "",
    val hip: String = "",
    val type: String = "",
    val name: String = "",
    val duration: String = "",
    val calories: String = "",
    val note: String = "",
    val foods: List<FoodEntry> = emptyList(),
    val imageUrl: String? = null,
    val exercises: List<TrainingExercise>? = null,
)

data class WorkoutSelection(
    val type: String,
    val name: String,
    val minutes: String = "",
    val calories: String = "",
    val note: String = "",
)

data class EditorState(
    val draft: RecordDraft,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val loadingFailed: Boolean = false,
    val previousBody: BodyEntry? = null,
    val workouts: List<WorkoutSelection> = emptyList(),
    val uploading: Boolean = false,
)

class RecordEditorViewModel(
    private val repository: RecordsRepository,
    kind: RecordKind,
    date: LocalDate,
    private val recordId: String?,
) : ViewModel() {
    private val mutable =
        MutableStateFlow(
            EditorState(
                RecordDraft(
                    kind,
                    recordId,
                    date
                        .atTime(LocalTime.now())
                        .atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()
                        .toString(),
                    type =
                        when (kind) {
                            RecordKind.MEAL -> "BREAKFAST"
                            RecordKind.WORKOUT -> "RUNNING"
                            RecordKind.PHOTO -> "FRONT"
                            else -> ""
                        },
                )
            )
        )
    val state = mutable.asStateFlow()

    private var referenceRequest: Job? = null

    init {
        if (kind == RecordKind.BODY && recordId == null) loadBodyReference()
        if (recordId != null) load()
    }

    fun edit(change: (RecordDraft) -> RecordDraft) {
        if (!mutable.value.saving && !mutable.value.loading) {
            val old = mutable.value.draft
            val next = change(old)
            mutable.value = mutable.value.copy(draft = next, dirty = true, error = null)
            if (next.kind == RecordKind.BODY && old.time != next.time) loadBodyReference()
        }
    }

    private fun loadBodyReference() {
        referenceRequest?.cancel()
        val draft = mutable.value.draft
        mutable.value = mutable.value.copy(previousBody = null)
        referenceRequest =
            viewModelScope.launch {
                val result = repository.bodies(1, null, draft.time)
                if (result is AppResult.Success) {
                    val previous =
                        result.data.entries
                            .filter {
                                it.id != draft.id &&
                                    OffsetDateTime.parse(it.recordedAt).toInstant() <
                                        OffsetDateTime.parse(draft.time).toInstant()
                            }
                            .maxByOrNull { OffsetDateTime.parse(it.recordedAt).toInstant() }
                    mutable.value = mutable.value.copy(previousBody = previous)
                }
            }
    }

    fun load() {
        val id = recordId ?: return
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result =
                when (mutable.value.draft.kind) {
                    RecordKind.BODY ->
                        repository.body(id).draft { entry ->
                            mutable.value.draft.copy(
                                weight = entry.weight.toString(),
                                bodyFat = entry.bodyFat?.toString().orEmpty(),
                                waist = entry.waist?.toString().orEmpty(),
                                chest = entry.chest?.toString().orEmpty(),
                                hip = entry.hip?.toString().orEmpty(),
                                time = entry.recordedAt,
                                note = entry.note.orEmpty(),
                            )
                        }
                    RecordKind.MEAL ->
                        repository.meal(id).draft { entry ->
                            mutable.value.draft.copy(
                                type = entry.type,
                                foods = entry.foods,
                                time = entry.recordedAt,
                                note = entry.note.orEmpty(),
                                imageUrl = entry.imageUrl,
                            )
                        }
                    RecordKind.WORKOUT ->
                        repository.workout(id).draft { entry ->
                            mutable.value.draft.copy(
                                type = entry.type,
                                name = entry.name,
                                duration = entry.durationMinutes.toString(),
                                calories = entry.calories?.toString().orEmpty(),
                                time = entry.startedAt,
                                exercises = entry.exercises,
                                note = entry.note.orEmpty(),
                            )
                        }
                    RecordKind.PHOTO ->
                        repository.photo(id).draft { entry ->
                            mutable.value.draft.copy(
                                type = entry.type,
                                imageUrl = entry.imageUrl,
                                time = entry.recordedAt,
                                note = entry.note.orEmpty(),
                            )
                        }
                }
            mutable.value =
                when (result) {
                    is AppResult.Success ->
                        mutable.value.copy(
                            draft = result.data,
                            loading = false,
                            loadingFailed = false,
                        )
                    is AppResult.Failure ->
                        mutable.value.copy(
                            loading = false,
                            error = result.error.recordMessage(),
                            loadingFailed = true,
                        )
                }
        }
    }

    fun upload(bytes: ByteArray, mimeType: String) {
        if (mutable.value.saving) return
        mutable.value =
            mutable.value.copy(
                saving = true,
                error = null,
                draft = mutable.value.draft.copy(imageUrl = null),
                uploading = true,
            )
        viewModelScope.launch {
            when (val result = repository.upload(bytes, mimeType)) {
                is AppResult.Success ->
                    mutable.value =
                        mutable.value.copy(
                            draft = mutable.value.draft.copy(imageUrl = result.data),
                            saving = false,
                            dirty = true,
                            uploading = false,
                        )
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(
                            saving = false,
                            uploading = false,
                            error = result.error.recordMessage(),
                        )
            }
        }
    }

    fun error(message: String) {
        mutable.value = mutable.value.copy(error = message)
    }

    fun save() {
        val state = mutable.value
        if (state.saving || state.loading) return
        val d = state.draft
        validateRecord(d)?.let {
            error(it)
            return
        }
        mutable.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            val result: AppResult<*> =
                when (d.kind) {
                    RecordKind.BODY ->
                        repository.saveBody(
                            BodyInput(
                                d.weight.toDouble(),
                                d.bodyFat.toDoubleOrNull(),
                                d.waist.toDoubleOrNull(),
                                d.chest.toDoubleOrNull(),
                                d.hip.toDoubleOrNull(),
                                d.time,
                                d.note.ifBlank { null },
                            ),
                            d.id,
                        )
                    RecordKind.MEAL ->
                        repository.saveMeal(
                            MealInput(d.type, d.time, d.foods, d.note.ifBlank { null }),
                            d.id,
                        )
                    RecordKind.WORKOUT ->
                        repository.saveWorkout(
                            WorkoutInput(
                                d.type,
                                d.name.trim(),
                                d.time,
                                d.duration.toInt(),
                                d.calories.toIntOrNull(),
                                d.note.ifBlank { null },
                                d.exercises,
                            ),
                            d.id,
                        )
                    RecordKind.PHOTO ->
                        repository.savePhoto(
                            PhotoInput(d.type, d.imageUrl!!, d.time, d.note.ifBlank { null })
                        )
                }
            mutable.value =
                when (result) {
                    is AppResult.Success ->
                        mutable.value.copy(saving = false, saved = true, dirty = false)
                    is AppResult.Failure ->
                        mutable.value.copy(saving = false, error = result.error.recordMessage())
                }
        }
    }

    fun selectWorkout(item: WorkoutSelection, index: Int? = null) {
        if (mutable.value.saving) return
        val items = mutable.value.workouts.toMutableList()
        if (index == null) items.add(item) else if (index in items.indices) items[index] = item
        mutable.value = mutable.value.copy(workouts = items, dirty = true, error = null)
    }

    fun removeWorkout(index: Int) {
        if (mutable.value.saving) return
        mutable.value =
            mutable.value.copy(
                workouts = mutable.value.workouts.filterIndexed { i, _ -> i != index },
                dirty = true,
                error = null,
            )
    }

    fun saveWorkouts() {
        val state = mutable.value
        if (state.saving || state.loading || state.draft.id != null) return
        if (state.workouts.isEmpty()) {
            error("请至少添加一项运动")
            return
        }
        state.workouts.forEach { item ->
            validateRecord(
                    state.draft.copy(
                        type = item.type,
                        name = item.name,
                        duration = item.minutes,
                        calories = item.calories,
                        note = item.note,
                    )
                )
                ?.let {
                    error(it)
                    return
                }
        }
        mutable.value = state.copy(saving = true, error = null)
        viewModelScope.launch {
            // Remove each acknowledged item before continuing so retry never resends it.
            for (item in state.workouts) {
                val result =
                    repository.saveWorkout(
                        WorkoutInput(
                            item.type,
                            item.name.trim(),
                            state.draft.time,
                            item.minutes.toInt(),
                            item.calories.toIntOrNull(),
                            item.note.ifBlank { null },
                        )
                    )
                when (result) {
                    is AppResult.Success ->
                        mutable.value =
                            mutable.value.copy(workouts = mutable.value.workouts.drop(1))
                    is AppResult.Failure -> {
                        mutable.value =
                            mutable.value.copy(
                                saving = false,
                                error = "${result.error.recordMessage()}，未保存的运动已保留",
                            )
                        return@launch
                    }
                }
            }
            mutable.value = mutable.value.copy(saving = false, saved = true, dirty = false)
        }
    }

    fun delete() {
        val d = mutable.value.draft
        if (d.id == null || mutable.value.saving) return
        mutable.value = mutable.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = repository.delete(d.kind, d.id)) {
                is AppResult.Success ->
                    mutable.value = mutable.value.copy(saving = false, saved = true, dirty = false)
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(saving = false, error = result.error.recordMessage())
            }
        }
    }
}

private fun <T> AppResult<T>.draft(convert: (T) -> RecordDraft): AppResult<RecordDraft> =
    when (this) {
        is AppResult.Success -> AppResult.Success(convert(data))
        is AppResult.Failure -> this
    }

internal fun validateRecord(d: RecordDraft): String? {
    if (runCatching { OffsetDateTime.parse(d.time) }.isFailure) return "请选择正确的记录时间"
    if (d.note.length > 500) return "备注不能超过 500 字"
    fun invalid(s: String, min: Double, max: Double, required: Boolean = false): Boolean {
        if (s.isBlank()) return required
        val n = s.toDoubleOrNull() ?: return true
        return !n.isFinite() || n !in min..max || !s.matches(Regex("[0-9]+(\\.[0-9]{1,2})?"))
    }
    return when (d.kind) {
        RecordKind.BODY ->
            when {
                invalid(d.weight, 1.0, 500.0, true) -> "请输入 1–500 kg 的体重，最多两位小数"
                invalid(d.bodyFat, 0.0, 100.0) -> "体脂率应为 0–100%"
                listOf(d.waist, d.chest, d.hip).any { invalid(it, 1.0, 500.0) } -> "围度应为 1–500 cm"
                else -> null
            }
        RecordKind.MEAL ->
            if (d.foods.isEmpty()) "请至少添加一种食物" else if (d.foods.size > 50) "单次最多添加 50 种食物" else null
        RecordKind.WORKOUT ->
            when {
                d.name.trim().length !in 1..60 -> "请输入 1–60 字的运动名称"
                (d.duration.toIntOrNull() ?: 0) !in 1..1440 -> "运动时长应为 1–1440 整数分钟"
                d.calories.isNotBlank() && (d.calories.toIntOrNull() ?: -1) !in 0..10000 ->
                    "热量应为 0–10000 的整数"
                else -> null
            }
        RecordKind.PHOTO -> if (d.imageUrl == null) "请先选择并上传照片" else null
    }
}
