package com.fittrace.feature.main

import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.*
import com.fittrace.core.model.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingWorkflowTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = mutableListOf<TrainingViewModel>()
    private val date = LocalDate.of(2026, 10, 19)
    private val exercise = CatalogExercise("plank", "平板支撑", "核心", "徒手", "seconds", emptyList(), "")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private class Drafts : TrainingDraftStore {
        val values = mutableMapOf<String, TrainingDraft>()

        override fun read(date: String) = values[date]

        override fun write(date: String, draft: TrainingDraft) {
            values[date] = draft
        }

        private var favorites = emptySet<String>()

        override fun favoriteExercises() = favorites

        override fun setFavoriteExercises(ids: Set<String>) {
            favorites = ids
        }

        override fun clear(date: String) {
            values.remove(date)
        }
    }

    @Test
    fun `persistent draft restores completed groups rest deadline and selected date only`() =
        runTest(dispatcher) {
            val drafts = Drafts()
            val first =
                TrainingViewModel(TrainingRepositoryFake(), RecordsRepositoryFake(), date, drafts) {
                        1000L
                    }
                    .also { models += it }
            runCurrent()
            first.favoriteExercise("plank")
            first.add(exercise)
            first.minutes("15")
            first.set(0, 0, TrainingSet(35, 0.0, true))
            first.startRest(0)
            runCurrent()
            assertEquals(61000L, drafts.read(date.toString())!!.restUntil)
            first.viewModelScope.cancel()
            val second =
                TrainingViewModel(TrainingRepositoryFake(), RecordsRepositoryFake(), date, drafts)
                    .also { models += it }
            runCurrent()
            assertEquals(setOf("plank"), second.state.value.favoriteExercises)
            assertTrue(second.state.value.restored)
            assertEquals(
                TrainingSet(35, 0.0, true),
                second.state.value.exercises.single().sets.single(),
            )
            val other =
                TrainingViewModel(
                        TrainingRepositoryFake(),
                        RecordsRepositoryFake(),
                        date.minusDays(1),
                        drafts,
                    )
                    .also { models += it }
            runCurrent()
            assertTrue(other.state.value.exercises.isEmpty())
            second.discard()
            runCurrent()
            assertNull(drafts.read(date.toString()))
        }

    @Test
    fun `previous uses real paginated groups resets completion and never invents data`() =
        runTest(dispatcher) {
            var pages = 0
            val previous =
                TrainingExercise(
                    "plank",
                    "平板支撑",
                    "seconds",
                    60,
                    listOf(TrainingSet(40, 0.0, true), TrainingSet(15, 0.0, false)),
                )
            val records =
                object : RecordsRepositoryFake() {
                    override suspend fun workouts(
                        page: Int,
                        from: String?,
                        to: String?,
                    ): AppResult<RecordPage<WorkoutEntry>> {
                        pages++
                        assertNotNull(to)
                        return AppResult.Success(
                            RecordPage(
                                listOf(
                                    WorkoutEntry(
                                        "$page",
                                        "STRENGTH",
                                        "训练",
                                        "2026-10-18T10:00:00+08:00",
                                        20,
                                        null,
                                        null,
                                        if (page == 2) listOf(previous) else null,
                                    )
                                ),
                                2,
                            )
                        )
                    }
                }
            val model =
                TrainingViewModel(TrainingRepositoryFake(), records, date).also { models += it }
            runCurrent()
            model.add(exercise)
            model.bringPrevious(0)
            runCurrent()
            assertEquals(2, pages)
            assertEquals(
                listOf(TrainingSet(40, 0.0, false)),
                model.state.value.exercises.single().sets,
            )
        }

    @Test
    fun `template edit updates existing id restores session and leaves its draft intact`() =
        runTest(dispatcher) {
            var savedId: String? = null
            val template =
                TrainingTemplate(
                    "existing",
                    "模板",
                    25,
                    listOf(
                        TrainingExercise(
                            "row",
                            "划船",
                            "reps",
                            60,
                            listOf(TrainingSet(10, 12.0, true)),
                        )
                    ),
                )
            val repo =
                object : TrainingRepositoryFake() {
                    override suspend fun saveTemplate(
                        name: String,
                        minutes: Int,
                        exercises: List<TrainingExercise>,
                        id: String?,
                    ): AppResult<TrainingTemplate> {
                        savedId = id
                        return AppResult.Success(template.copy(name = name, exercises = exercises))
                    }
                }
            val drafts = Drafts()
            val model =
                TrainingViewModel(repo, RecordsRepositoryFake(), date, drafts).also { models += it }
            runCurrent()
            model.add(exercise)
            model.minutes("15")
            runCurrent()
            model.beginTemplate(template)
            runCurrent()
            assertFalse(model.state.value.exercises.single().sets.single().completed)
            model.name("编辑模板")
            model.save(true)
            runCurrent()
            assertEquals("existing", savedId)
            assertFalse(model.state.value.editingTemplate)
            assertEquals("plank", model.state.value.exercises.single().exerciseId)
            assertEquals("plank", drafts.read(date.toString())!!.exercises.single().exerciseId)
        }

    @Test
    fun `draft write failure is not reported as retained and clears after successful retry`() =
        runTest(dispatcher) {
            var fail = true
            val store =
                object : TrainingDraftStore {
                    override fun read(date: String): TrainingDraft? = null

                    override fun write(date: String, draft: TrainingDraft) {
                        if (fail) error("disk full")
                    }

                    override fun clear(date: String) {}

                    override fun favoriteExercises() = emptySet<String>()

                    override fun setFavoriteExercises(ids: Set<String>) {}
                }
            val model =
                TrainingViewModel(TrainingRepositoryFake(), RecordsRepositoryFake(), date, store)
                    .also { models += it }
            runCurrent()
            model.add(exercise)
            runCurrent()
            assertTrue(model.state.value.draftError)
            assertEquals("本机草稿保存失败，请勿关闭应用", model.state.value.notice)
            fail = false
            model.minutes("20")
            runCurrent()
            assertFalse(model.state.value.draftError)
            assertNull(model.state.value.notice)
        }
}
