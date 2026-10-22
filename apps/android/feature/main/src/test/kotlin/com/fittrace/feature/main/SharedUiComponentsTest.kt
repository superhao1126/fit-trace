package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.fittrace.core.designsystem.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedUiComponentsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun selectWheel(label: String, value: Int) {
        compose.onNodeWithContentDescription(label).performSemanticsAction(
            SemanticsActions.SetProgress
        ) {
            it(value.toFloat())
        }
        compose.waitForIdle()
    }

    private fun assertWheel(label: String, value: String) {
        compose
            .onNodeWithContentDescription(label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))
    }

    @Test
    fun `date wheels clamp month lengths and leap year without editable fields`() {
        var saved: LocalDate? = null
        compose.setContent {
            FitTraceTheme {
                FitDatePickerDialog(
                    LocalDate.of(2024, 1, 31),
                    {},
                    { saved = it },
                    maxDate = LocalDate.of(2024, 12, 31),
                )
            }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        selectWheel("月份", 2)
        assertWheel("日期", "29日")
        selectWheel("年份", 2023)
        assertWheel("日期", "28日")
        assertNull(saved)
        capture("shared-date-wheel-390")
        compose.onNodeWithText("确认").performClick()
        assertEquals(LocalDate.of(2023, 2, 28), saved)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `goal date wheels only offer permitted dates in narrow dark mode`() {
        var saved: LocalDate? = null
        compose.setContent {
            FitTraceTheme(true) {
                FitDatePickerDialog(
                    LocalDate.of(2024, 3, 31),
                    {},
                    { saved = it },
                    "选择目标日期",
                    minDate = LocalDate.of(2024, 2, 29),
                    maxDate = LocalDate.of(2024, 4, 10),
                )
            }
        }
        selectWheel("月份", 2)
        assertWheel("日期", "29日")
        selectWheel("日期", 1)
        assertWheel("日期", "29日")
        selectWheel("月份", 4)
        assertWheel("日期", "10日")
        selectWheel("年份", 2023)
        assertWheel("年份", "2024年")
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        capture("shared-date-wheel-dark-320")
        compose.onNodeWithText("确认").performClick()
        assertEquals(LocalDate.of(2024, 4, 10), saved)
    }

    @Test
    fun `record date wheels do not offer future dates and cancel does not save`() {
        var saved: LocalDate? = null
        var dismissed = false
        compose.setContent {
            FitTraceTheme {
                FitDatePickerDialog(
                    LocalDate.of(2024, 2, 29),
                    { dismissed = true },
                    { saved = it },
                    maxDate = LocalDate.of(2024, 2, 29),
                )
            }
        }
        selectWheel("月份", 12)
        assertWheel("月份", "2月")
        selectWheel("日期", 31)
        assertWheel("日期", "29日")
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertNull(saved)
    }

    @Test
    fun `time wheels change by vertical gestures and commit only after snapping`() {
        var saved: LocalTime? = null
        compose.setContent {
            FitTraceTheme { FitTimePickerDialog(LocalTime.of(12, 30), {}, { saved = it }) }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithContentDescription("分钟").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -65f), delayMillis = 500)
        }
        compose.onNodeWithText("确认").assertIsNotEnabled()
        capture("shared-time-wheel-drag-390")
        compose.onNodeWithContentDescription("分钟").performTouchInput { up() }
        compose.waitForIdle()
        val minute =
            compose
                .onNodeWithContentDescription("分钟")
                .fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription]
                .removeSuffix("分")
                .toInt()
        assertTrue(minute > 30)
        assertNull(saved)
        selectWheel("小时", 23)
        selectWheel("分钟", 59)
        capture("shared-time-wheel-390")
        compose.onNodeWithText("确认").assertIsEnabled().performClick()
        assertEquals(LocalTime.of(23, 59), saved)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `time wheel boundaries midnight and cancel work without keyboard`() {
        var saved: LocalTime? = null
        var dismissed = false
        compose.setContent {
            FitTraceTheme(true) {
                FitTimePickerDialog(LocalTime.of(23, 59), { dismissed = true }, { saved = it })
            }
        }
        selectWheel("小时", 0)
        selectWheel("分钟", 0)
        assertWheel("小时", "0时")
        assertWheel("分钟", "0分")
        capture("shared-time-wheel-dark-320")
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertNull(saved)
        compose.onNodeWithText("确认").performClick()
        assertEquals(LocalTime.MIDNIGHT, saved)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `confirmation keeps actions visible with long text and larger font`() {
        var confirmed = false
        var dismissed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                FitTraceTheme(true) {
                    FitDialog(
                        onDismissRequest = { dismissed = true },
                        title = { Text("删除这条记录？") },
                        text = { Text("删除后无法恢复。\n".repeat(30)) },
                        confirmButton = {
                            FitDialogButton(onClick = { confirmed = true }, destructive = true) {
                                Text("确认删除")
                            }
                        },
                        dismissButton = {
                            FitDialogButton(onClick = { dismissed = true }, primary = false) {
                                Text("取消")
                            }
                        },
                    )
                }
            }
        }
        compose.onNodeWithText("确认删除").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        assertFalse(confirmed)
        capture("shared-confirm-long-dark-320")
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
        assertFalse(confirmed)
    }

    @Test
    fun `field error and notice share accessible feedback with explicit retry`() {
        var value by mutableStateOf("")
        var retries = 0
        compose.setContent {
            FitTraceTheme {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FitTextField(
                        value,
                        { value = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("名称") },
                        isError = value.isBlank(),
                    )
                    if (value.isBlank()) FitInlineMessage("请填写名称")
                    FitNotice("保存失败，输入已保留", error = true, onRetry = { retries++ })
                }
            }
        }
        compose.onNodeWithText("名称").performTextInput("本地草稿")
        compose.onNodeWithText("请填写名称").assertDoesNotExist()
        assertEquals(0, retries)
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        assertEquals("本地草稿", value)
        compose
            .onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .onFirst()
            .assertExists()
        capture("shared-input-notice-390")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/shared-ui-screenshots/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
