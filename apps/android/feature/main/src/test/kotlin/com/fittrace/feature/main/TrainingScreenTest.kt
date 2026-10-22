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
