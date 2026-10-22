package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
class BodyRecordScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val date = LocalDate.of(2026, 10, 19)

    @Test
    fun `body keypad supplementary fields and ruler preserve actual draft before save`() {
        var saved: BodyInput? = null
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun saveBody(input: BodyInput, id: String?): AppResult<BodyEntry> {
                    saved = input
                    return AppResult.Failure(AppError.Network)
                }
            }
        val model = RecordEditorViewModel(repo, RecordKind.BODY, date, null)
        model.edit { it.copy(weight = "70.2", time = "2026-10-19T08:00:00+08:00") }
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("记录身体数据").assertIsDisplayed()
        compose.onNodeWithContentDescription("输入体重").assertIsDisplayed()
        capture("body-main-390")
        compose.onNodeWithText("围度与备注").performClick()
        compose.onNodeWithText("腰围 · cm（选填）").performTextInput("78")
        compose.onNodeWithText("确认补充").performScrollTo().performClick()
        assertEquals("78", model.state.value.draft.waist)
        compose.onNodeWithContentDescription("体重标尺").performTouchInput { swipeLeft() }
        assertNotEquals("70.2", model.state.value.draft.weight)
        compose.onNodeWithContentDescription("输入体重").performClick()
        capture("body-keypad-390", sheet = true)
        listOf("7", "0", ".", "2").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).performClick()
        }
        compose.onNodeWithText("完成输入").performClick()
        compose.onNodeWithText("保存记录").performClick()
        compose.waitUntil { saved != null }
        assertEquals(70.2, saved!!.weight, 0.0)
        assertEquals(78.0, saved!!.waist)
        assertNull(saved!!.bodyFat)
        assertEquals(date, java.time.OffsetDateTime.parse(saved!!.recordedAt).toLocalDate())
        compose.waitUntil { model.state.value.error != null }
        compose.onNodeWithText("重新保存").assertIsDisplayed()
        assertEquals("70.2", model.state.value.draft.weight)
        capture("body-save-failed-390")
    }

    @Test
    fun `body details show unknown fields and edit delete remain explicit`() {
        val entry =
            BodyEntry("body", 70.2, 23.5, 78.0, null, null, "2026-10-19T08:00:00+08:00", "晨起记录")
        val repo =
            object : RecordsRepositoryFake() {
                override suspend fun body(id: String) = AppResult.Success(entry)
            }
        val model = RecordEditorViewModel(repo, RecordKind.BODY, date, "body")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { !model.state.value.loading }
        compose.onNodeWithText("编辑记录").assertIsDisplayed()
        compose.onAllNodesWithText("—").assertCountEquals(2)
        capture("body-detail-390")
        compose.onNodeWithText("删除这条记录").performClick()
        compose.onNodeWithText("确认删除").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("编辑记录").performClick()
        compose.onNodeWithContentDescription("输入体重").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow blank body does not prefill sample weight and rejects empty submission`() {
        val model = RecordEditorViewModel(RecordsRepositoryFake(), RecordKind.BODY, date, null)
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme(true) { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithText("70.2").assertDoesNotExist()
        compose.onNodeWithText("保存记录").performClick()
        compose.waitUntil { model.state.value.error != null }
        assertEquals("", model.state.value.draft.weight)
        capture("body-invalid-dark-320")
    }

    @Test
    fun `body load failure retries without rendering empty record data`() {
        val model = RecordEditorViewModel(RecordsRepositoryFake(), RecordKind.BODY, date, "body")
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.waitUntil { model.state.value.loadingFailed }
        compose.onNodeWithText("身体数据读取失败").assertIsDisplayed()
        compose.onNodeWithText("编辑记录").assertDoesNotExist()
        capture("body-load-failed-390")
    }

    @Test
    fun `drag visibly translates ruler marks while center pointer selects actual weight`() {
        val model = RecordEditorViewModel(RecordsRepositoryFake(), RecordKind.BODY, date, null)
        model.edit { it.copy(weight = "70.2") }
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        val ruler = compose.onNodeWithContentDescription("体重标尺").performScrollTo()
        fun rulerImage(): Bitmap {
            val bounds = ruler.fetchSemanticsNode().boundsInRoot
            lateinit var bitmap: Bitmap
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(full))
                bitmap =
                    Bitmap.createBitmap(
                        full,
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.width.toInt(),
                        bounds.height.toInt(),
                    )
            }
            return bitmap
        }
        val before = rulerImage()
        ruler.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(-70f, 0f))
        }
        ruler.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(-3f, 0f)) }
        val during = rulerImage()
        assertFalse("标尺刻度必须随手指移动", before.sameAs(during))
        assertTrue(model.state.value.draft.weight.toDouble() > 70.2)
        capture("body-ruler-drag-390")
        ruler.performTouchInput { up() }
        assertTrue(model.state.value.draft.weight.toDouble() in 1.0..500.0)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w390dp-h640dp-mdpi")
    fun `weight keypad opens with complete confirm button above gesture navigation without pulling sheet`() {
        val model = RecordEditorViewModel(RecordsRepositoryFake(), RecordKind.BODY, date, null)
        model.edit { it.copy(weight = "90.3") }
        compose.setContent {
            val state by model.state.collectAsState()
            FitTraceTheme { RecordEditorScreen(state, model, {}) }
        }
        compose.onNodeWithContentDescription("输入体重").performClick()
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(
                WindowInspector.getGlobalWindowViews().last(),
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                    .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                    .build(),
            )
        }
        val button = compose.onNodeWithText("完成输入").assertIsDisplayed()
        val clipped = button.fetchSemanticsNode().boundsInRoot
        val full = button.getUnclippedBoundsInRoot()
        assertTrue("完成输入文字不能被底部裁切", clipped.height >= (full.bottom - full.top).value - .5f)
        val windowHeight =
            compose.runOnIdle { WindowInspector.getGlobalWindowViews().last().height }
        assertTrue("按钮必须在手势导航区域之上", full.bottom.value <= windowHeight - 24)
        capture("body-keypad-visible-390", sheet = true)
        button.performClick()
        assertEquals("90.3", model.state.value.draft.weight)
        compose.onNodeWithText("输入体重 · kg").assertDoesNotExist()
    }

    private fun capture(name: String, sheet: Boolean = false) {
        compose.runOnIdle {
            val view =
                if (sheet) WindowInspector.getGlobalWindowViews().last()
                else compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/body-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
