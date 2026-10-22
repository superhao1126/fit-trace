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

    @Test
    fun `record calendar handles leap day and prevents future selection until explicit confirmation`() {
        var saved: LocalDate? = null
        compose.setContent {
            FitTraceTheme {
                FitDatePickerDialog(
                    LocalDate.of(2024, 2, 28),
                    {},
                    { saved = it },
                    maxDate = LocalDate.of(2024, 2, 29),
                )
            }
        }
        compose.onNodeWithContentDescription("下个月").assertIsNotEnabled()
        compose.onNodeWithContentDescription("2024-02-29").performClick().assertIsSelected()
        assertNull(saved)
        capture("shared-date-record-390")
        compose.onNodeWithText("确认").performClick()
        assertEquals(LocalDate.of(2024, 2, 29), saved)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `goal calendar manual entry shares range validation and supports narrow dark mode`() {
        var saved: LocalDate? = null
        compose.setContent {
            FitTraceTheme(true) {
                FitDatePickerDialog(
                    LocalDate.of(2024, 3, 1),
                    {},
                    { saved = it },
                    "选择目标日期",
                    minDate = LocalDate.of(2024, 2, 29),
                )
            }
        }
        compose.onNodeWithText("输入日期").performClick()
        compose.onNodeWithText("日期 · YYYY-MM-DD").performTextReplacement("2024-02-28")
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.onNodeWithText("日期 · YYYY-MM-DD").performTextReplacement("2024-02-30")
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.onNodeWithText("日期 · YYYY-MM-DD").performTextReplacement("2024-02-29")
        compose.onNodeWithText("确认").assertIsEnabled()
        capture("shared-date-manual-dark-320")
        assertNull(saved)
        compose.onNodeWithText("确认").performClick()
        assertEquals(LocalDate.of(2024, 2, 29), saved)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `calendar days remain reachable on narrow screen`() {
        var dismissed = false
        compose.setContent {
            FitTraceTheme(true) {
                FitDatePickerDialog(LocalDate.of(2026, 8, 31), { dismissed = true }, {})
            }
        }
        compose.onNodeWithContentDescription("2026-08-31").assertIsDisplayed()
        compose.onNodeWithContentDescription("2026-08-01").assertIsDisplayed()
        capture("shared-date-calendar-dark-320")
        compose.onNodeWithText("取消").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `time validates hour and minute and cancels without committing changes`() {
        var saved: LocalTime? = null
        var dismissed = false
        compose.setContent {
            FitTraceTheme {
                FitTimePickerDialog(LocalTime.of(23, 59), { dismissed = true }, { saved = it })
            }
        }
        compose.onNodeWithText("时").performTextReplacement("24")
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.onNodeWithText("时").performTextReplacement("00")
        compose.onNodeWithText("分").performTextReplacement("60")
        compose.onNodeWithText("确认").assertIsNotEnabled()
        compose.onNodeWithText("分").performTextReplacement("00")
        capture("shared-time-390")
        assertNull(saved)
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
