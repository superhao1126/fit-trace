package com.fittrace.feature.auth

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.fittrace.core.designsystem.FitTraceTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `email entry has unchecked consent and working password switch`() {
        compose.setContent {
            var state by remember { mutableStateOf(AuthUiState()) }
            FitTraceTheme { AuthScreen(state, onMode = { state = state.copy(mode = it) }) }
        }
        compose.onNodeWithContentDescription("我已阅读并同意用户协议和隐私政策").assertIsOff()
        compose.onNodeWithText("登录循形").assertIsDisplayed()
        compose.onNodeWithText("获取验证码").assertIsDisplayed()
        capture("email-login-390")
        compose.onNodeWithText("使用密码登录").performScrollTo().performClick()
        compose.onNodeWithText("密码登录").assertIsDisplayed()
        compose.onNodeWithText("设置或找回密码").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `compact password layout keeps submit and recovery reachable`() {
        compose.setContent { FitTraceTheme { AuthScreen(AuthUiState(mode = AuthMode.PASSWORD)) } }
        compose.onNodeWithText("设置或找回密码").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("登录", substring = false).performScrollTo().assertIsDisplayed()
        val consentLayout = consentLayout()
        org.junit.Assert.assertEquals(1, consentLayout.lineCount)
        org.junit.Assert.assertFalse(consentLayout.hasVisualOverflow)
        capture("password-login-320")
    }

    @Test
    fun `dark reset page shows verified email and password fields`() {
        compose.setContent {
            FitTraceTheme(darkTheme = true) {
                AuthScreen(AuthUiState(mode = AuthMode.RESET_PASSWORD, email = "user@example.com"))
            }
        }
        compose.onNodeWithText("设置新密码").assertIsDisplayed()
        compose.onNodeWithText("已验证 user@example.com").assertIsDisplayed()
        compose.onNodeWithText("确认新密码").assertIsDisplayed()
        compose.onNodeWithText("保存新密码").performScrollTo().assertIsDisplayed()
        capture("reset-password-dark-390")
    }

    @Test
    fun `agreement and privacy actions are independent from checkbox`() {
        var selected: PolicyKind? = null
        compose.setContent {
            FitTraceTheme { AuthScreen(AuthUiState(), onPolicy = { selected = it }) }
        }
        clickPolicy("用户协议")
        org.junit.Assert.assertEquals(PolicyKind.AGREEMENT, selected)
        compose.onNodeWithContentDescription("我已阅读并同意用户协议和隐私政策").assertIsOff()
        clickPolicy("隐私政策")
        org.junit.Assert.assertEquals(PolicyKind.PRIVACY, selected)
    }

    @Test
    fun `dark email login retains branded fields and error feedback`() {
        compose.setContent {
            FitTraceTheme(darkTheme = true) {
                AuthScreen(AuthUiState(errors = mapOf(AuthField.EMAIL to "请输入正确的邮箱地址")))
            }
        }
        compose.onNodeWithText("请输入正确的邮箱地址").assertIsDisplayed()
        capture("email-login-dark-390")
    }

    private val consentText = "我已阅读并同意用户协议与隐私政策"

    private fun consentLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(consentText).performScrollTo().performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(results)
        }
        return results.single()
    }

    private fun clickPolicy(label: String) {
        val layout = consentLayout()
        val position = layout.getBoundingBox(consentText.indexOf(label)).center
        compose.onNodeWithText(consentText).performTouchInput { click(position) }
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/auth-screenshots/$name.png")
            requireNotNull(output.parentFile).mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
