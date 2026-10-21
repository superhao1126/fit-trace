package com.fittrace.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fittrace.core.model.*
import java.time.*
import java.util.TimeZone
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<ViewModel>()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `history date uses local midnight and failed refresh retains same date only`() =
        runTest(dispatcher) {
            val zone = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            try {
                var range: Pair<String?, String?>? = null
                var fail = false
                val body =
                    BodyEntry(
                        "body",
                        70.2,
                        null,
                        null,
                        null,
                        null,
                        "2026-10-19T08:00:00+08:00",
                        null,
                    )
                val repo =
                    object : RecordsRepositoryFake() {
                        override suspend fun bodies(
                            page: Int,
                            from: String?,
                            to: String?,
                        ): AppResult<RecordPage<BodyEntry>> {
                            range = from to to
                            return if (fail) AppResult.Failure(AppError.Network)
                            else AppResult.Success(RecordPage(listOf(body), 1))
                        }
                    }
                val model = RecordsViewModel(repo).also { models += it }
                model.select(RecordKind.BODY, LocalDate.of(2026, 10, 19))
                runCurrent()
                assertEquals("2026-10-19T00:00+08:00" to "2026-10-20T00:00+08:00", range)
                fail = true
                model.refresh()
                runCurrent()
                assertEquals("body", model.state.value.items.single().id)
                assertNotNull(model.state.value.error)
                model.select(RecordKind.BODY, LocalDate.of(2026, 10, 18))
                runCurrent()
                assertTrue(model.state.value.items.isEmpty())
            } finally {
                TimeZone.setDefault(zone)
            }
        }

    @Test
    fun `failed food save retains inputs and null calories and blocks duplicate submission`() =
        runTest(dispatcher) {
            var input: MealInput? = null
            var calls = 0
            val repo =
                object : RecordsRepositoryFake() {
                    override suspend fun saveMeal(
                        value: MealInput,
                        id: String?,
                    ): AppResult<MealEntry> {
                        input = value
                        calls++
                        delay(100)
                        return AppResult.Failure(AppError.Network)
                    }
                }
            val model =
                RecordEditorViewModel(repo, RecordKind.MEAL, LocalDate.of(2026, 10, 19), null)
                    .also { models += it }
            model.edit { it.copy(foods = listOf(FoodEntry("自制午餐", "1 碗", null)), note = "保留备注") }
            model.save()
            model.save()
            advanceUntilIdle()
            assertEquals(1, calls)
            assertNull(input!!.foods.single().calories)
            assertEquals(
                LocalDate.of(2026, 10, 19),
                OffsetDateTime.parse(input!!.recordedAt).toLocalDate(),
            )
            assertEquals("保留备注", model.state.value.draft.note)
            assertFalse(model.state.value.saved)
            assertNotNull(model.state.value.error)
        }

    @Test
    fun `photo upload failure removes old remote URL and prevents saving stale image`() =
        runTest(dispatcher) {
            val model =
                RecordEditorViewModel(
                        RecordsRepositoryFake(),
                        RecordKind.PHOTO,
                        LocalDate.now(),
                        null,
                    )
                    .also { models += it }
            model.edit { it.copy(imageUrl = "https://example.test/old.jpg") }
            model.upload(byteArrayOf(1), "image/jpeg")
            runCurrent()
            assertNull(model.state.value.draft.imageUrl)
            model.save()
            runCurrent()
            assertFalse(model.state.value.saved)
            assertEquals("请先选择并上传照片", model.state.value.error)
        }

    @Test
    fun `strength saves only completed sets with seconds unit and keeps draft on failure`() =
        runTest(dispatcher) {
            var saved: WorkoutInput? = null
            val records =
                object : RecordsRepositoryFake() {
                    override suspend fun saveWorkout(
                        input: WorkoutInput,
                        id: String?,
                    ): AppResult<WorkoutEntry> {
                        saved = input
                        return AppResult.Failure(AppError.Network)
                    }
                }
            val model =
                TrainingViewModel(TrainingRepositoryFake(), records, LocalDate.of(2026, 10, 19))
                    .also { models += it }
            runCurrent()
            model.add(CatalogExercise("plank", "平板支撑", "核心", "徒手", "seconds", emptyList(), ""))
            model.addSet(0)
            model.set(0, 0, TrainingSet(30, 0.0, true))
            model.minutes("15")
            model.save()
            runCurrent()
            val exercise = saved!!.exercises!!.single()
            assertEquals("seconds", exercise.unit)
            assertEquals(listOf(TrainingSet(30, 0.0, true)), exercise.sets)
            assertNull(saved!!.calories)
            assertEquals(2, model.state.value.exercises.single().sets.size)
            assertFalse(model.state.value.saved)
        }

    @Test
    fun `invalid numeric input is rejected before repository write`() {
        val base = RecordDraft(RecordKind.BODY, time = "2026-10-19T08:00:00+08:00", weight = "70.2")
        assertNull(validateRecord(base))
        listOf("NaN", "Infinity", "0", "501", "70.222", "not a number").forEach {
            assertNotNull(validateRecord(base.copy(weight = it)))
        }
        assertNotNull(validateRecord(base.copy(bodyFat = "101")))
        assertNull(validateRecord(base.copy(bodyFat = "0")))
    }
}
