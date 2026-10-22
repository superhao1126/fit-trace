package com.fittrace.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.domain.TrainingDraft
import com.fittrace.core.domain.TrainingDraftStore
import com.fittrace.core.domain.TrainingRepository
import com.fittrace.core.model.*
import java.time.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class TrainingState(
    val exercises: List<TrainingExercise> = emptyList(),
    val templates: List<TrainingTemplate> = emptyList(),
    val plan: List<TrainingPlanDay> = (0..6).map { TrainingPlanDay(it, null) },
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val name: String = "力量训练",
    val minutes: String = "",
    val saved: Boolean = false,
    val notice: String? = null,
    val restUntil: Long? = null,
    val restored: Boolean = false,
    val editingTemplate: Boolean = false,
    val templateId: String? = null,
    val previousLoading: Boolean = false,
    val favoriteExercises: Set<String> = emptySet(),
)

class TrainingViewModel(
    private val repository: TrainingRepository,
    private val records: RecordsRepository,
    private val date: LocalDate,
    private val drafts: TrainingDraftStore? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutable = MutableStateFlow(TrainingState())
    val state = mutable.asStateFlow()

    private var planReady = false
    private var sessionBeforeTemplate: TrainingState? = null

    init {
        mutable.value =
            mutable.value.copy(favoriteExercises = drafts?.favoriteExercises().orEmpty())
        drafts?.read(date.toString())?.let {
            mutable.value =
                mutable.value.copy(
                    name = it.name,
                    minutes = it.minutes,
                    exercises = it.exercises,
                    restUntil = it.restUntil,
                    restored = true,
                )
        }
        viewModelScope.launch {
            state
                .map {
                    if (it.editingTemplate) null
                    else
                        (it.saved to TrainingDraft(it.name, it.minutes, it.exercises, it.restUntil))
                }
                .distinctUntilChanged()
                .collect { value ->
                    if (value != null && drafts != null) {
                        val (saved, draft) = value
                        runCatching {
                                if (saved || draft.exercises.isEmpty())
                                    drafts.clear(date.toString())
                                else drafts.write(date.toString(), draft)
                            }
                            .onFailure {
                                mutable.value = mutable.value.copy(notice = "本机草稿保存失败，请勿关闭应用")
                            }
                    }
                }
        }
        load()
    }

    fun load() {
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val templates = repository.templates()
            val plan = repository.plan()
            planReady = plan is AppResult.Success && templates is AppResult.Success
            mutable.value =
                mutable.value.copy(
                    loading = false,
                    templates = (templates as? AppResult.Success)?.data ?: mutable.value.templates,
                    plan = (plan as? AppResult.Success)?.data?.days ?: mutable.value.plan,
                    error =
                        ((templates as? AppResult.Failure)?.error
                                ?: (plan as? AppResult.Failure)?.error)
                            ?.recordMessage(),
                )
        }
    }

    fun name(value: String) {
        if (!mutable.value.saving) mutable.value = mutable.value.copy(name = value, error = null)
    }

    fun minutes(value: String) {
        if (!mutable.value.saving) mutable.value = mutable.value.copy(minutes = value, error = null)
    }

    fun add(
        exercise: CatalogExercise,
        reps: Int = if (exercise.unit == "seconds") 20 else 10,
        weight: Double = 0.0,
        groups: Int = 1,
    ) {
        val s = mutable.value
        if (
            s.saving ||
                s.exercises.size >= 20 ||
                reps !in 1..3600 ||
                groups !in 1..20 ||
                !weight.isFinite() ||
                weight !in 0.0..1000.0
        )
            return
        if (s.exercises.any { it.exerciseId == exercise.id }) return
        mutable.value =
            s.copy(
                exercises =
                    s.exercises +
                        TrainingExercise(
                            exercise.id,
                            exercise.name,
                            exercise.unit,
                            60,
                            List(groups) { TrainingSet(reps, weight, false) },
                        ),
                error = null,
            )
    }

    fun favoriteExercise(id: String) {
        val old = mutable.value.favoriteExercises
        val next = if (id in old) old - id else old + id
        runCatching { drafts?.setFavoriteExercises(next) }
            .fold(
                {
                    mutable.value =
                        mutable.value.copy(
                            favoriteExercises = next,
                            notice = if (id in next) "已加入常用动作" else "已移除常用动作",
                        )
                },
                { mutable.value = mutable.value.copy(notice = "常用动作保存失败，请重试") },
            )
    }

    fun remove(index: Int) {
        if (!mutable.value.saving)
            mutable.value =
                mutable.value.copy(
                    exercises = mutable.value.exercises.filterIndexed { i, _ -> i != index }
                )
    }

    fun set(exerciseIndex: Int, setIndex: Int, set: TrainingSet) {
        if (mutable.value.saving) return
        mutable.value =
            mutable.value.copy(
                exercises =
                    mutable.value.exercises.mapIndexed { index, exercise ->
                        if (index == exerciseIndex)
                            exercise.copy(
                                sets =
                                    exercise.sets.mapIndexed { i, old ->
                                        if (i == setIndex) set else old
                                    }
                            )
                        else exercise
                    },
                error = null,
            )
    }

    fun addSet(index: Int) {
        if (mutable.value.saving) return
        mutable.value =
            mutable.value.copy(
                exercises =
                    mutable.value.exercises.mapIndexed { i, exercise ->
                        if (i == index && exercise.sets.size < 20)
                            exercise.copy(
                                sets = exercise.sets + exercise.sets.last().copy(completed = false)
                            )
                        else exercise
                    }
            )
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        if (mutable.value.saving) return
        mutable.value =
            mutable.value.copy(
                exercises =
                    mutable.value.exercises.mapIndexed { i, exercise ->
                        if (i == exerciseIndex && exercise.sets.size > 1)
                            exercise.copy(
                                sets = exercise.sets.filterIndexed { j, _ -> j != setIndex }
                            )
                        else exercise
                    }
            )
    }

    fun use(template: TrainingTemplate) {
        if (!mutable.value.saving)
            mutable.value =
                mutable.value.copy(
                    name = template.name,
                    minutes = template.durationMinutes.toString(),
                    exercises =
                        template.exercises.map {
                            it.copy(sets = it.sets.map { set -> set.copy(completed = false) })
                        },
                    error = null,
                )
    }

    fun planDay(day: Int, templateId: String?) {
        if (!mutable.value.saving)
            mutable.value =
                mutable.value.copy(
                    plan =
                        mutable.value.plan.map {
                            if (it.weekday == day) it.copy(templateId = templateId) else it
                        }
                )
    }

    fun startRest(index: Int) {
        val exercise = mutable.value.exercises.getOrNull(index) ?: return
        if (!mutable.value.saving)
            mutable.value = mutable.value.copy(restUntil = now() + exercise.restSeconds * 1000L)
    }

    fun extendRest() {
        if (!mutable.value.saving)
            mutable.value =
                mutable.value.copy(
                    restUntil = maxOf(now(), mutable.value.restUntil ?: now()) + 30000
                )
    }

    fun stopRest() {
        mutable.value = mutable.value.copy(restUntil = null)
    }

    fun restSeconds(index: Int, seconds: Int) {
        if (mutable.value.saving || seconds !in 0..600) return
        mutable.value =
            mutable.value.copy(
                exercises =
                    mutable.value.exercises.mapIndexed { i, e ->
                        if (i == index) e.copy(restSeconds = seconds) else e
                    }
            )
    }

    fun discard() {
        if (mutable.value.saving) return
        mutable.value =
            mutable.value.copy(
                exercises = emptyList(),
                minutes = "",
                name = "力量训练",
                restUntil = null,
                restored = false,
            )
    }

    fun beginTemplate(template: TrainingTemplate? = null) {
        if (mutable.value.saving) return
        sessionBeforeTemplate = mutable.value
        mutable.value =
            mutable.value.copy(
                editingTemplate = true,
                templateId = template?.id,
                name = template?.name ?: "",
                minutes = template?.durationMinutes?.toString() ?: "",
                exercises =
                    template?.exercises?.map {
                        it.copy(sets = it.sets.map { s -> s.copy(completed = false) })
                    } ?: emptyList(),
                restUntil = null,
                error = null,
            )
    }

    fun cancelTemplate() {
        if (mutable.value.saving) return
        sessionBeforeTemplate?.let {
            mutable.value =
                mutable.value.copy(
                    name = it.name,
                    minutes = it.minutes,
                    exercises = it.exercises,
                    restUntil = it.restUntil,
                    editingTemplate = false,
                    templateId = null,
                    error = null,
                )
        }
        sessionBeforeTemplate = null
    }

    fun bringPrevious(index: Int) {
        val exercise = mutable.value.exercises.getOrNull(index) ?: return
        if (mutable.value.saving || mutable.value.previousLoading) return
        mutable.value = mutable.value.copy(previousLoading = true, notice = null)
        viewModelScope.launch {
            val until =
                date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString()
            var page = 1
            var seen = 0
            var found: TrainingExercise? = null
            var failure: String? = null
            while (found == null && failure == null) {
                when (val result = records.workouts(page, to = until)) {
                    is AppResult.Failure -> failure = result.error.recordMessage()
                    is AppResult.Success -> {
                        val entries =
                            result.data.entries.sortedByDescending {
                                OffsetDateTime.parse(it.startedAt).toInstant()
                            }
                        found =
                            entries
                                .asSequence()
                                .flatMap { it.exercises.orEmpty().asSequence() }
                                .firstOrNull {
                                    it.exerciseId == exercise.exerciseId &&
                                        it.unit == exercise.unit &&
                                        it.sets.any { s -> s.completed }
                                }
                        seen += entries.size
                        if (entries.isEmpty() || seen >= result.data.total) break
                        page++
                    }
                }
            }
            val same = mutable.value.exercises.getOrNull(index) == exercise
            mutable.value =
                mutable.value.copy(
                    previousLoading = false,
                    exercises =
                        if (found != null && same && !mutable.value.saving)
                            mutable.value.exercises.mapIndexed { i, e ->
                                if (i == index)
                                    e.copy(
                                        sets =
                                            found!!
                                                .sets
                                                .filter { it.completed }
                                                .map { it.copy(completed = false) }
                                    )
                                else e
                            }
                        else mutable.value.exercises,
                    notice = failure ?: if (found == null) "暂无这个动作的历史完成组" else "已带入上次，请按实际完成情况勾选",
                )
        }
    }

    private fun valid(): Boolean {
        val s = mutable.value
        val error =
            when {
                s.name.trim().length !in 1..60 -> "训练名称需 1–60 字"
                (s.minutes.toIntOrNull() ?: 0) !in 1..1440 -> "请填写 1–1440 分钟的实际时长"
                s.exercises.isEmpty() -> "请先添加动作"
                else -> null
            }
        if (error != null) mutable.value = s.copy(error = error)
        return error == null
    }

    fun save(template: Boolean = false) {
        if (mutable.value.saving || !valid()) return
        val s = mutable.value
        val exercises =
            if (template) s.exercises
            else
                s.exercises
                    .map { it.copy(sets = it.sets.filter { set -> set.completed }) }
                    .filter { it.sets.isNotEmpty() }
        if (exercises.isEmpty()) {
            mutable.value = s.copy(error = "请至少标记一组已完成")
            return
        }
        mutable.value = s.copy(saving = true, error = null, notice = null)
        viewModelScope.launch {
            val result =
                if (template)
                    repository.saveTemplate(
                        s.name.trim(),
                        s.minutes.toInt(),
                        exercises,
                        s.templateId,
                    )
                else
                    records.saveWorkout(
                        WorkoutInput(
                            "STRENGTH",
                            s.name.trim(),
                            date
                                .atTime(LocalTime.now())
                                .atZone(ZoneId.systemDefault())
                                .toOffsetDateTime()
                                .toString(),
                            s.minutes.toInt(),
                            null,
                            null,
                            exercises,
                        )
                    )
            mutable.value =
                when (result) {
                    is AppResult.Success ->
                        mutable.value.copy(
                            saving = false,
                            saved = !template,
                            notice = if (template) "模板已保存" else null,
                        )
                    is AppResult.Failure ->
                        mutable.value.copy(saving = false, error = result.error.recordMessage())
                }
            if (template && result is AppResult.Success) {
                if (s.editingTemplate) cancelTemplate()
                mutable.value = mutable.value.copy(notice = "模板已保存")
                load()
            }
        }
    }

    fun savePlan() {
        if (mutable.value.saving || mutable.value.loading || !planReady) return
        val days = mutable.value.plan
        mutable.value = mutable.value.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = repository.savePlan(days)) {
                is AppResult.Success ->
                    mutable.value =
                        mutable.value.copy(saving = false, plan = result.data, notice = "每周计划已保存")
                is AppResult.Failure ->
                    mutable.value =
                        mutable.value.copy(saving = false, error = result.error.recordMessage())
            }
        }
    }
}
