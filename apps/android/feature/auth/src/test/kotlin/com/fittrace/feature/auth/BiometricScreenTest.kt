package com.fittrace.feature.auth

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.fittrace.core.designsystem.FitTraceTheme
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
class BiometricScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val binding = DeviceBinding("id", "owner", "owner@example.com", "alias")

    @Test
    fun `bound login masks account and never starts authentication automatically`() {
        var logins = 0
        var emails = 0
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE, binding, enabled = true)
                    ),
                    false,
                    null,
                    onLogin = { logins++ },
                    onEmail = { emails++ },
                )
            }
        }
        compose.onNodeWithText("o***@example.com").assertIsDisplayed()
        assertEquals(0, logins)
        capture("biometric-login-390")
        compose.onNodeWithText("指纹 / 生物识别登录").performScrollTo().performClick()
        assertEquals(1, logins)
        compose.onNodeWithText("使用邮箱登录").performScrollTo().performClick()
        assertEquals(1, emails)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `invalidated narrow dark login keeps email recovery reachable`() {
        compose.setContent {
            FitTraceTheme(darkTheme = true) {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE, binding, invalidated = true)
                    ),
                    false,
                    null,
                )
            }
        }
        compose.onNodeWithText("指纹 / 生物识别登录").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("使用邮箱登录").performScrollTo().assertIsDisplayed()
        capture("biometric-invalid-dark-320")
    }

    @Test
    fun `pending activation cannot log in or claim enabled`() {
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE, binding.copy(pending = true))
                    ),
                    false,
                    null,
                )
            }
        }
        compose.onNodeWithText("待确认开启结果，请联网检查").assertIsDisplayed()
        compose.onNodeWithText("指纹 / 生物识别登录").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `disabling requires explicit confirmation`() {
        var disabled = 0
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE, binding, enabled = true)
                    ),
                    true,
                    binding.email,
                    onDisable = { disabled++ },
                )
            }
        }
        capture("biometric-settings-390")
        compose.onNodeWithText("关闭本机指纹登录").performScrollTo().performClick()
        assertEquals(0, disabled)
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, disabled)
        compose.onNodeWithText("关闭本机指纹登录").performClick()
        compose.onNodeWithText("确认").performClick()
        assertEquals(1, disabled)
    }

    @Test
    fun `email step offers manual code send and preserves explicit activation`() {
        var sends = 0
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE),
                        needsEmail = true,
                    ),
                    true,
                    binding.email,
                    onSend = { sends++ },
                )
            }
        }
        assertEquals(0, sends)
        compose.onNodeWithText("发送验证码").performScrollTo().performClick()
        assertEquals(1, sends)
        compose.onNodeWithText("验证邮箱并开启").performScrollTo().assertIsDisplayed()
        capture("biometric-email-390")
    }

    private fun capture(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val output = File("build/reports/biometric-screenshots/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
