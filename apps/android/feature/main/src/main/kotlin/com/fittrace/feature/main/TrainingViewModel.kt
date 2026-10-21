package com.fittrace.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.RecordsRepository
import com.fittrace.core.domain.TrainingRepository
import com.fittrace.core.model.*
import java.time.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class TrainingViewModel(
    private val repository: TrainingRepository,
    private val records: RecordsRepository,
    private val date: LocalDate,
) : ViewModel() {
    private val mutable = MutableStateFlow(TrainingState())
    val state = mutable.asStateFlow()

    private var planReady = false

    init {
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

    fun add(exercise: CatalogExercise) {
        val s = mutable.value
        if (s.saving || s.exercises.size >= 20) return
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
                            listOf(
                                TrainingSet(if (exercise.unit == "seconds") 20 else 10, 0.0, false)
                            ),
                        ),
                error = null,
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
                if (template) repository.saveTemplate(s.name.trim(), s.minutes.toInt(), exercises)
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
            if (template && result is AppResult.Success) load()
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
