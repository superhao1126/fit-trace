package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
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
@Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `dark narrow body editor validates and retains failed input`() {
        val model =
            RecordEditorViewModel(
                RecordsRepositoryFake(),
                RecordKind.BODY,
                LocalDate.of(2026, 10, 19),
                null,
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("体重 · kg").performTextInput("70.2")
        compose.onNodeWithText("保存记录").performScrollTo().performClick()
        compose.waitUntil {
            compose
                .onAllNodesWithText("网络连接失败", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("70.2").performScrollTo().assertIsDisplayed()
        capture("body-dark-320")
    }

    @Test
    fun `food picker adds actual catalog selection before explicit save`() {
        val model =
            RecordEditorViewModel(
                RecordsRepositoryFake(),
                RecordKind.MEAL,
                LocalDate.of(2026, 10, 19),
                null,
            )
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("＋ 添加食物").performScrollTo().performClick()
        compose.onNodeWithText("米饭（熟）").performClick()
        compose.onNodeWithText("加入清单").performClick()
        compose.onNodeWithText("米饭（熟）").assertIsDisplayed()
        compose.onNodeWithText("100 g · 130 kcal").assertIsDisplayed()
        Assert.assertFalse(model.state.value.saved)
        capture("meal-selected-320")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/record-screenshots/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
