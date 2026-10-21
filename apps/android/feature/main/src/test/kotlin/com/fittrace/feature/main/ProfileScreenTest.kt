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
import com.fittrace.core.domain.DashboardRepository
import com.fittrace.core.model.*
import java.io.File
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
class ProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `nickname confirmation updates draft without saving account`() {
        var nickname = ""
        compose.setContent {
            FitTraceTheme {
                ProfileEditScreen(
                    ProfileUiState(profile = profileFixture(), nickname = "循形用户", loading = false),
                    {},
                    { nickname = it },
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("昵称").performClick()
        compose.onNodeWithText("修改昵称").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("本地草稿")
        compose.onNodeWithText("确认").performClick()
        assertEquals("本地草稿", nickname)
    }

    @Test
    fun `profile shows real account goal and working entry actions`() {
        var action = ""
        compose.setContent {
            FitTraceTheme {
                Scaffold(bottomBar = { HomeNavigation(3) {} }) { padding ->
                    ProfileScreen(
                        ProfileUiState(
                            profile = profileFixture(),
                            body = ProfileBody(70.2, "2026-10-16T07:40:00+08:00"),
                            loading = false,
                        ),
                        {},
                        { action = "settings" },
                        { action = "profile" },
                        { action = "goal" },
                        { action = "archive" },
                        Modifier.padding(padding),
                        onRecords = { action = it.name },
                    )
                }
            }
        }
        compose.onNodeWithText("hello@example.com").assertIsDisplayed()
        compose.onNodeWithText("已完成 27%", substring = true).assertIsDisplayed()
        capture("profile-goal-390")
        compose.onNodeWithText("循形用户").performClick()
        assertEquals("profile", action)
        compose.onNodeWithText("调整 ›").performClick()
        assertEquals("goal", action)
        listOf(
                "身体记录" to RecordKind.BODY,
                "饮食记录" to RecordKind.MEAL,
                "运动记录" to RecordKind.WORKOUT,
                "身材照片" to RecordKind.PHOTO,
            )
            .forEach { (title, kind) ->
                compose.onNodeWithText(title).performScrollTo().performClick()
                assertEquals(kind.name, action)
            }
        compose.onNodeWithText("显示与设置").performScrollTo().performClick()
        assertEquals("settings", action)
        compose.onNodeWithText("记录档案 ›").performScrollTo().performClick()
        assertEquals("archive", action)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `dark narrow empty goal and errors remain readable`() {
        compose.setContent {
            FitTraceTheme(darkTheme = true) {
                Scaffold(bottomBar = { HomeNavigation(3) {} }) { padding ->
                    ProfileScreen(
                        ProfileUiState(
                            profile = profileFixture(null),
                            loading = false,
                            bodyError = "体重暂时无法读取，请重试",
                        ),
                        {},
                        {},
                        {},
                        {},
                        {},
                        Modifier.padding(padding),
                    )
                }
            }
        }
        compose.onNodeWithText("设置健身目标").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重试").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("已完成", substring = true).assertDoesNotExist()
        capture("profile-empty-dark-320")
    }

    @Test
    fun `goal date dialog cancellation preserves unselected date`() {
        var selected: String? = null
        compose.setContent {
            FitTraceTheme {
                GoalEditScreen(
                    ProfileUiState(
                        profile = profileFixture(),
                        loading = false,
                        currentWeight = "70.2",
                        targetWeight = "68",
                    ),
                    {},
                    {},
                    {},
                    {},
                    { selected = it },
                    {},
                    {},
                )
            }
        }
        capture("profile-goal-editor-390")
        compose.onNodeWithText("下一步").performScrollTo().performClick()
        compose.onNodeWithText("选择目标日期").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        assertNull(selected)
        compose.onNodeWithText("保存目标").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `main navigation hides tabs on settings and saves edited nickname`() {
        val profile = ProfileRepositoryFake()
        compose.setContent {
            FitTraceTheme {
                MainRoute(
                    "0.1.1",
                    "hello@example.com",
                    {},
                    false,
                    object : DashboardRepository {
                        override suspend fun overview(
                            date: String,
                            timezoneOffset: Int,
                        ): AppResult<Dashboard> = AppResult.Success(dashboardFixture(date))
                    },
                    "u",
                    profile,
                    "SYSTEM",
                    {},
                    {},
                    RecordsRepositoryFake(),
                    TrainingRepositoryFake(),
                    {},
                )
            }
        }
        compose.onNodeWithText("我的").performClick()
        compose.waitUntil { compose.onAllNodesWithText("循形用户").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("循形用户").performClick()
        compose.onNodeWithText("编辑资料").assertIsDisplayed()
        capture("profile-info-390")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("显示与设置").performScrollTo().performClick()
        compose.onNodeWithText("首页").assertDoesNotExist()
        capture("profile-settings-390")
        compose.onNodeWithText("版本与关于").performScrollTo().performClick()
        capture("profile-about-390")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("编辑资料").performScrollTo().performClick()
        capture("profile-edit-390")
        compose.onNodeWithText("昵称").performClick()
        compose.onNodeWithText("修改昵称").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("测试昵称")
        compose.onNodeWithText("确认").performClick()
        compose.onNodeWithText("保存资料").performScrollTo().performClick()
        compose.waitUntil { profile.saves == 1 }
        compose.waitUntil { compose.onAllNodesWithText("显示模式").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("测试昵称").assertIsDisplayed()
        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun `settings switches appearance opens policies and logout can be cancelled`() {
        var mode = "SYSTEM"
        var policy: Boolean? = null
        var logouts = 0
        compose.setContent {
            FitTraceTheme {
                ProfileSettingsScreen(
                    {},
                    {},
                    { policy = it },
                    {},
                    mode,
                    { mode = it },
                    false,
                    { logouts++ },
                )
            }
        }
        compose.onNodeWithText("显示模式").performScrollTo().performClick()
        compose.onNodeWithText("深色").performClick()
        assertEquals("DARK", mode)
        compose.onNodeWithText("隐私政策").performScrollTo().performClick()
        assertEquals(true, policy)
        compose.onNodeWithText("退出登录").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, logouts)
        compose.onNodeWithText("退出登录").performClick()
        compose.onAllNodesWithText("退出登录")[1].performClick()
        assertEquals(1, logouts)
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/profile-screenshots/$name.png")
            output.parentFile?.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
