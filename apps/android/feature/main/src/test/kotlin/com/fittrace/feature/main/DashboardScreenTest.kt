package com.fittrace.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fittrace.core.designsystem.FitTraceTheme
import com.fittrace.core.model.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun `empty home date selection and entry actions work`() {
        var selected: LocalDate? = null
        var action: String? = null
        compose.setContent {
            FitTraceTheme {
                Scaffold(bottomBar = { HomeNavigation(0) {} }) { padding ->
                    DashboardScreen(
                        DashboardUiState(today, dashboardFixture(), false),
                        { selected = it },
                        {},
                        { action = it },
                        Modifier.padding(padding),
                        today,
                    )
                }
            }
        }
        compose.onNodeWithText("从今天的一次记录开始").assertIsDisplayed()
        compose.onNodeWithContentDescription("2026-10-15").performClick()
        assertEquals(today.minusDays(1), selected)
        compose.onNodeWithContentDescription("2026-10-17").assertIsNotEnabled()
        compose.onNodeWithText("记饮食").performClick()
        assertEquals("记饮食", action)
        capture("home-empty-390")
        compose.onNodeWithText("日历").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(today.minusDays(1), selected)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow home preserves records during failed refresh and retries`() {
        var retries = 0
        val records =
            listOf(
                DiaryRecord(
                    "b",
                    RecordKind.BODY,
                    "身体数据",
                    "体重记录",
                    "70.2 kg",
                    "2026-10-16T07:40:00+08:00",
                ),
                DiaryRecord(
                    "m",
                    RecordKind.MEAL,
                    "早餐",
                    "鸡蛋、牛奶、全麦吐司",
                    "热量未填写",
                    "2026-10-16T08:30:00+08:00",
                ),
            )
        val data = dashboardFixture(records = records).copy(weight = 70.2)
        compose.setContent {
            FitTraceTheme(darkTheme = true) {
                Scaffold(bottomBar = { HomeNavigation(0) {} }) { padding ->
                    DashboardScreen(
                        DashboardUiState(today, data, false, "网络连接失败，请稍后重试"),
                        {},
                        { retries++ },
                        {},
                        Modifier.padding(padding),
                        today,
                    )
                }
            }
        }
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("热量未填写").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("早餐").assertIsDisplayed()
        capture("home-records-dark-320")
    }

    @Test
    fun `populated home renders real overview goal and seven day summary`() {
        val records =
            listOf(
                DiaryRecord(
                    "b",
                    RecordKind.BODY,
                    "身体数据",
                    "体重记录",
                    "70.2 kg",
                    "2026-10-16T07:40:00+08:00",
                ),
                DiaryRecord(
                    "m",
                    RecordKind.MEAL,
                    "早餐",
                    "鸡蛋、牛奶、全麦吐司",
                    "360 kcal",
                    "2026-10-16T08:30:00+08:00",
                ),
            )
        val data =
            dashboardFixture(records = records)
                .copy(
                    weight = 70.2,
                    calories = 860.0,
                    workoutCount = 1,
                    workoutMinutes = 35,
                    goal = DashboardGoal("LOSE_FAT", 68.0),
                    trend = DashboardTrend("2026-10-10", "2026-10-16", -0.8, 5, 6, 4, 180),
                )
        compose.setContent {
            FitTraceTheme {
                Scaffold(bottomBar = { HomeNavigation(0) {} }) { padding ->
                    DashboardScreen(
                        DashboardUiState(today, data, false),
                        {},
                        {},
                        {},
                        Modifier.padding(padding),
                        today,
                    )
                }
            }
        }
        compose.onNodeWithText("860").assertIsDisplayed()
        compose.onNodeWithText("减脂 · 目标 68 kg").assertIsDisplayed()
        capture("home-populated-390")
        compose.onNodeWithText("180 分钟").performScrollTo().assertIsDisplayed()
        capture("home-populated-summary-390")
    }

    @Test
    fun `initial failure shows retry without empty account guidance`() {
        compose.setContent {
            FitTraceTheme {
                DashboardScreen(
                    DashboardUiState(today, null, false, "网络连接失败，请稍后重试"),
                    {},
                    {},
                    {},
                    today = today,
                )
            }
        }
        compose.onNodeWithText("重试").assertIsDisplayed()
        compose.onNodeWithText("从今天的一次记录开始").assertDoesNotExist()
        capture("home-failed-390")
    }

    @Test
    fun `initial loading has placeholders and keeps quick recording available`() {
        compose.setContent {
            FitTraceTheme {
                DashboardScreen(DashboardUiState(today, null, true), {}, {}, {}, today = today)
            }
        }
        compose.onNodeWithContentDescription("正在加载记录").assertIsDisplayed()
        compose.onNodeWithText("kcal").assertDoesNotExist()
        compose.onNodeWithText("记体重").assertIsDisplayed()
        capture("home-loading-390")
    }

    @Test
    fun `historical home hides today goal and offers supplementary recording`() {
        var action: String? = null
        val date = today.minusDays(1)
        compose.setContent {
            FitTraceTheme {
                DashboardScreen(
                    DashboardUiState(date, dashboardFixture(), false),
                    {},
                    {},
                    { action = it },
                    today = today,
                )
            }
        }
        compose.onNodeWithText("历史记录").assertIsDisplayed()
        compose.onNodeWithText("设置一个体重目标").assertDoesNotExist()
        capture("home-history-390")
        compose.onNodeWithText("补记 ›").performClick()
        compose.onAllNodesWithText("记运动").onLast().performClick()
        assertEquals("记训练", action)
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/home-screenshots/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
