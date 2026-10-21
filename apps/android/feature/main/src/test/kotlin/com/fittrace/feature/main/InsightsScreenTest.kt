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
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val points =
        listOf(
            TrendPoint("2026-10-13T12:00:00Z", 70.8),
            TrendPoint("2026-10-14T12:00:00Z", 70.9),
            TrendPoint("2026-10-16T12:00:00Z", 70.6),
            TrendPoint("2026-10-17T12:00:00Z", 70.5),
            TrendPoint("2026-10-19T12:00:00Z", 70.2),
        )
    private val data =
        BodyTrends(
            7,
            "2026-10-13T00:00:00Z",
            "2026-10-19T23:59:59Z",
            TrendSeries(points),
            TrendSeries(listOf(TrendPoint("2026-10-19T12:00:00Z", 23.5))),
            TrendSeries(emptyList()),
        )
    private val items =
        listOf(
            RecordItem(
                "b",
                RecordKind.BODY,
                "身体数据",
                "体脂 23.5%",
                "70.2 kg",
                "2026-10-19T07:40:00+08:00",
            ),
            RecordItem(
                "m",
                RecordKind.MEAL,
                "午餐",
                "2 项食物",
                "246 kcal",
                "2026-10-19T12:30:00+08:00",
            ),
            RecordItem(
                "w",
                RecordKind.WORKOUT,
                "跑步（快速）",
                "有氧运动",
                "12 分钟",
                "2026-10-19T18:20:00+08:00",
            ),
            RecordItem("m2", RecordKind.MEAL, "晚餐", "3 项食物", "热量待补充", "2026-10-18T12:00:00Z"),
        )

    @Test
    fun `trends metric range and chart interaction reflect real data`() {
        var days = 0
        compose.setContent {
            FitTraceTheme {
                Scaffold(bottomBar = { HomeNavigation(1) {} }) { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                        TrendsScreen(
                            TrendsState(
                                7,
                                data,
                                false,
                                insights = PeriodInsights(7, 1860.0, 8, 4, 3, 96),
                                insightsLoading = false,
                            ),
                            { days = it },
                            {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("70.2").assertIsDisplayed()
        compose.onNodeWithText("1,860 kcal").assertDoesNotExist()
        compose.onNodeWithText("1860 kcal").assertIsDisplayed()
        capture("trends-populated-390")
        compose.onNodeWithText("30 天").performClick()
        assertEquals(30, days)
        compose.onNodeWithContentDescription("身体趋势图，5 个记录点").performClick()
        compose.onNodeWithText("体脂").performClick()
        compose.onNodeWithText("仅 1 次记录").assertIsDisplayed()
        compose.onNodeWithText("再记录一次，就能看到变化").assertIsDisplayed()
        capture("trends-single-390")
        compose.onNodeWithText("腰围").performClick()
        compose.onNodeWithText("这段时间还没有腰围记录").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow curve survives independent period failure`() {
        var retries = 0
        compose.setContent {
            FitTraceTheme(darkTheme = true) {
                TrendsScreen(
                    TrendsState(7, data, false, insightsLoading = false, insightsError = "网络失败"),
                    {},
                    {},
                    { retries++ },
                )
            }
        }
        compose.onNodeWithText("70.2").assertIsDisplayed()
        compose.onNodeWithText("重试 ›").performScrollTo().performClick()
        assertEquals(1, retries)
        capture("trends-dark-320")
    }

    @Test
    fun `archive rows open and month and type filters dispatch`() {
        var open: RecordItem? = null
        var month: YearMonth? = null
        var kind: RecordKind? = null
        compose.setContent {
            FitTraceTheme {
                Scaffold(bottomBar = { HomeNavigation(2) {} }) { padding ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                        RecordsScreen(
                            RecordsState(
                                items.sortedByDescending {
                                    OffsetDateTime.parse(it.time).toInstant()
                                },
                                false,
                                page = 1,
                                total = 24,
                            ),
                            null,
                            null,
                            { kind = it },
                            {},
                            {},
                            {},
                            { open = it },
                            {},
                            month = YearMonth.of(2026, 10),
                            onMonth = { month = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("热量待补充").assertIsDisplayed()
        capture("archive-populated-390")
        compose.onNodeWithText("午餐").performClick()
        assertEquals("m", open!!.id)
        compose.onNodeWithText("饮食").performClick()
        assertEquals(RecordKind.MEAL, kind)
        compose.onNodeWithText("2026年10月 ⌄").performClick()
        compose.onNodeWithText("9月").performClick()
        assertEquals(YearMonth.of(2026, 9), month)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `archive more failure keeps rows and retries more`() {
        var more = 0
        var refresh = 0
        compose.setContent {
            FitTraceTheme(darkTheme = true) {
                RecordsScreen(
                    RecordsState(
                        items.sortedByDescending { OffsetDateTime.parse(it.time).toInstant() },
                        false,
                        "网络错误",
                        1,
                        24,
                        true,
                    ),
                    null,
                    null,
                    {},
                    {},
                    { refresh++ },
                    { more++ },
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("重试 ›").performScrollTo().performClick()
        assertEquals(1, more)
        assertEquals(0, refresh)
        compose.onNodeWithText("热量待补充").assertIsDisplayed()
        capture("archive-more-failed-dark-320")
    }

    @Test
    fun `archive initial error retries refresh without empty state`() {
        var retries = 0
        compose.setContent {
            FitTraceTheme {
                RecordsScreen(
                    RecordsState(error = "网络错误", loading = false),
                    null,
                    null,
                    {},
                    {},
                    { retries++ },
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("这段时间还没有记录").assertDoesNotExist()
        compose.onNodeWithText("重新加载").performClick()
        assertEquals(1, retries)
        capture("archive-failed-390")
    }

    @Test
    fun `empty archive creates selected record kind and clears day filter`() {
        var kind: RecordKind? = null
        var date: LocalDate? = LocalDate.of(2026, 10, 19)
        compose.setContent {
            FitTraceTheme {
                RecordsScreen(
                    RecordsState(loading = false),
                    RecordKind.MEAL,
                    date,
                    {},
                    { date = it },
                    {},
                    {},
                    {},
                    { kind = it },
                )
            }
        }
        compose.onNodeWithText("添加记录").performClick()
        assertEquals(RecordKind.MEAL, kind)
        capture("archive-empty-390")
        compose.onNodeWithText("查看整月记录 ›").performClick()
        assertNull(date)
    }

    @Test
    fun `loading has placeholders without fake values`() {
        compose.setContent { FitTraceTheme { TrendsScreen(TrendsState(), {}, {}) } }
        compose.onNodeWithContentDescription("正在加载趋势").assertIsDisplayed()
        compose.onNodeWithText("70.2").assertDoesNotExist()
        capture("trends-loading-390")
    }

    @Test
    fun `trend initial error retries without fabricated empty values`() {
        var retries = 0
        compose.setContent {
            FitTraceTheme {
                TrendsScreen(
                    TrendsState(
                        loading = false,
                        error = "网络错误",
                        insightsLoading = false,
                        insightsError = "网络错误",
                    ),
                    { retries++ },
                    {},
                )
            }
        }
        compose.onNodeWithText("趋势暂时加载失败").assertIsDisplayed()
        compose.onNodeWithText("体重趋势已加载，可稍后重试。").assertDoesNotExist()
        compose.onNodeWithText("这段时间还没有体重记录").assertDoesNotExist()
        compose.onNodeWithText("重新加载").performClick()
        assertEquals(1, retries)
        capture("trends-failed-390")
    }

    @Test
    fun `archive first load uses row placeholders`() {
        compose.setContent {
            FitTraceTheme { RecordsScreen(RecordsState(), null, null, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithContentDescription("正在加载档案").assertIsDisplayed()
        compose.onNodeWithText("这段时间还没有记录").assertDoesNotExist()
        capture("archive-loading-390")
    }

    @Test
    fun `archive filtered day shows selected type and clears date`() {
        var cleared = false
        compose.setContent {
            FitTraceTheme {
                RecordsScreen(
                    RecordsState(listOf(items[1]), false, page = 1, total = 1),
                    RecordKind.MEAL,
                    LocalDate.of(2026, 10, 19),
                    {},
                    { cleared = it == null },
                    {},
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("午餐").assertIsDisplayed()
        compose.onNodeWithText("10月19日 ⌄").assertIsDisplayed()
        capture("archive-filtered-390")
        compose.onNodeWithText("查看整月").performClick()
        assertTrue(cleared)
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/insights-screenshots/$name.png")
            output.parentFile.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
