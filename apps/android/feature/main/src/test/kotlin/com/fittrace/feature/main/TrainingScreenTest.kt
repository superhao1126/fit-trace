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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrainingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `strength sets sheet completion rest and save failure preserve session`() {
        val model =
            TrainingViewModel(
                TrainingRepositoryFake(),
                RecordsRepositoryFake(),
                LocalDate.of(2026, 10, 19),
            )
        model.add(CatalogExercise("plank", "平板支撑", "核心", "徒手", "seconds", emptyList(), ""))
        model.minutes("20")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { TrainingScreen(state, model, {}, {}) }
        }
        compose.onNodeWithText("继续当前训练").performClick()
        compose.onNodeWithText("修改组数").performScrollTo().performClick()
        compose.onNodeWithContentDescription("完成第 1 组").performClick()
        capture("strength-sets-390", true)
        compose.onNodeWithText("确认修改").performScrollTo().performClick()
        compose.onNodeWithText("组间休息", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("结束休息").performClick()
        compose.onNodeWithText("完成并核对").performClick()
        compose.onNodeWithText("保存记录").performClick()
        compose.waitUntil { !model.state.value.saving && model.state.value.error != null }
        Assert.assertTrue(model.state.value.exercises.single().sets.single().completed)
        capture("strength-failed-390")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow template editing is available without completing any groups`() {
        val model =
            TrainingViewModel(
                TrainingRepositoryFake(),
                RecordsRepositoryFake(),
                LocalDate.of(2026, 10, 19),
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) { TrainingScreen(state, model, {}, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        compose.onNodeWithText("训练模板").performClick()
        compose.onNodeWithText("创建我的模板").performClick()
        compose.onNodeWithText("模板名称").assertIsDisplayed()
        compose.onNodeWithText("保存模板").assertIsDisplayed()
        capture("strength-template-dark-320")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `numeric panel adds planned sets without marking complete and favorites filter actual actions`() {
        val model =
            TrainingViewModel(
                TrainingRepositoryFake(),
                RecordsRepositoryFake(),
                LocalDate.of(2026, 10, 19),
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) { TrainingScreen(state, model, {}, {}) }
        }
        compose.onNodeWithText("开始力量训练").performClick()
        compose.onNodeWithText("＋ 添加动作").performScrollTo().performClick()
        compose.onNodeWithContentDescription("收藏徒手深蹲").performClick()
        compose.onNodeWithText("我的常用").performClick()
        compose.onNodeWithText("徒手深蹲").assertIsDisplayed()
        compose.onNodeWithText("跪姿俯卧撑").assertDoesNotExist()
        compose.onNodeWithText("徒手深蹲").performClick()
        compose.onNodeWithText("加入训练").performClick()
        compose.onNodeWithText("1", useUnmergedTree = true).performClick()
        compose.onNodeWithText("2", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("输入次数").performClick()
        compose.onNodeWithText("1", useUnmergedTree = true).performClick()
        compose.onNodeWithText("0", useUnmergedTree = true).performClick()
        capture("strength-numeric-dark-320", true)
        compose.onNodeWithText("加入动作清单").performScrollTo().performClick()
        compose.waitUntil { model.state.value.exercises.isNotEmpty() }
        val sets = model.state.value.exercises.single().sets
        Assert.assertEquals(3, sets.size)
        Assert.assertTrue(sets.all { it.reps == 10 && it.weightKg == 12.0 && !it.completed })
    }

    @Test
    fun `unavailable plan template is not presented as a rest day and save is disabled`() {
        val model =
            TrainingViewModel(
                object : TrainingRepositoryFake() {
                    override suspend fun templates(): AppResult<List<TrainingTemplate>> =
                        AppResult.Failure(AppError.Network)

                    override suspend fun plan() =
                        AppResult.Success(
                            TrainingPlan(
                                true,
                                (0..6).map {
                                    TrainingPlanDay(it, if (it == 0) "unavailable" else null)
                                },
                            )
                        )
                },
                RecordsRepositoryFake(),
                LocalDate.of(2026, 10, 19),
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { TrainingScreen(state, model, {}, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        compose.onNodeWithText("每周计划").performClick()
        compose.onNodeWithText("模板暂不可用").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("保存每周计划").performScrollTo().assertIsNotEnabled()
    }

    private fun capture(name: String, sheet: Boolean = false) {
        compose.runOnIdle {
            val view =
                if (sheet) WindowInspector.getGlobalWindowViews().last()
                else compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/strength-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
