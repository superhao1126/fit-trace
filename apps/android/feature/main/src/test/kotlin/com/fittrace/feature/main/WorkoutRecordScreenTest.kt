package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fittrace.core.designsystem.FitTraceTheme
import com.fittrace.core.model.*
import java.io.File
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutRecordScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 19)

    @Test
    fun `selection duration review and failed save retain actual inputs`() {
        var saved: WorkoutInput? = null
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun saveWorkout(
                    input: WorkoutInput,
                    id: String?,
                ): AppResult<WorkoutEntry> {
                    saved = input
                    return AppResult.Failure(AppError.Network)
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.WORKOUT, date, null)
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        capture("workout-select-390")
        compose.onNodeWithText("跑步（快速）").performClick()
        compose.onNodeWithText("1", useUnmergedTree = true).performClick()
        compose.onNodeWithText("2", useUnmergedTree = true).performClick()
        capture("workout-duration-390", true)
        compose.onNodeWithText("加入清单").performScrollTo().performClick()
        assertNull(saved)
        compose.onNodeWithText("核对并保存").performClick()
        compose.onNodeWithText("12 分钟 · — kcal").assertIsDisplayed()
        capture("workout-review-390")
        compose.onNodeWithText("完成记录").performClick()
        compose.waitUntil { saved != null && !model.state.value.saving }
        assertNull(saved!!.calories)
        assertEquals(12, saved!!.durationMinutes)
        assertTrue(saved!!.startedAt.startsWith("2026-10-19"))
        assertEquals(1, model.state.value.workouts.size)
        compose.onNodeWithText("重新保存").assertIsDisplayed()
        compose.onNodeWithText("移除").performScrollTo().performClick()
        assertTrue(model.state.value.workouts.isEmpty())
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow detail edit retains completed strength sets and delete confirms`() {
        var saved: WorkoutInput? = null
        val sets =
            listOf(TrainingExercise("squat", "深蹲", "reps", 60, listOf(TrainingSet(10, 20.0, true))))
        val entry =
            WorkoutEntry(
                "id",
                "STRENGTH",
                "下肢训练",
                "2026-10-19T10:00:00+08:00",
                30,
                null,
                "实际完成",
                sets,
            )
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun workout(id: String) = AppResult.Success(entry)

                override suspend fun saveWorkout(
                    input: WorkoutInput,
                    id: String?,
                ): AppResult<WorkoutEntry> {
                    saved = input
                    return AppResult.Failure(AppError.Network)
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.WORKOUT, date, "id")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        compose.onNodeWithText("30 分钟 · — kcal").assertIsDisplayed()
        capture("workout-detail-dark-320")
        compose.onNodeWithText("删除这条记录").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("编辑运动").performClick()
        compose.onNodeWithText("保存修改").performClick()
        compose.waitUntil { saved != null }
        assertEquals(sets, saved!!.exercises)
    }

    @Test
    fun `partial batch failure retries only unacknowledged entries`() {
        val sent = mutableListOf<String>()
        var fail = true
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun saveWorkout(
                    input: WorkoutInput,
                    id: String?,
                ): AppResult<WorkoutEntry> {
                    sent += input.name
                    return if (input.name == "骑行" && fail) AppResult.Failure(AppError.Network)
                    else
                        AppResult.Success(
                            WorkoutEntry(
                                input.name,
                                input.type,
                                input.name,
                                input.startedAt,
                                input.durationMinutes,
                                input.calories,
                                input.note,
                            )
                        )
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.WORKOUT, date, null)
        model.selectWorkout(WorkoutSelection("RUNNING", "跑步", "12"))
        model.selectWorkout(WorkoutSelection("CYCLING", "骑行", "10"))
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("核对并保存").performClick()
        compose.onNodeWithText("完成记录").performClick()
        compose.waitUntil { model.state.value.error != null }
        assertEquals(listOf("跑步", "骑行"), sent)
        assertEquals("骑行", model.state.value.workouts.single().name)
        fail = false
        compose.onNodeWithText("重新保存").performClick()
        compose.waitUntil { model.state.value.saved }
        assertEquals(listOf("跑步", "骑行", "骑行"), sent)
    }

    @Test
    fun `load failure is distinct from empty and search supports custom sport`() {
        val model =
            RecordEditorViewModel(RecordsRepositoryFake(), RecordKind.WORKOUT, date, "missing")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { model.state.value.loadingFailed }
        compose.onNodeWithText("运动记录读取失败").assertIsDisplayed()
        compose.onNodeWithText("保存修改").assertDoesNotExist()
    }

    private fun capture(name: String, sheet: Boolean = false) {
        compose.runOnIdle {
            val view =
                if (sheet) WindowInspector.getGlobalWindowViews().last()
                else compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/workout-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
