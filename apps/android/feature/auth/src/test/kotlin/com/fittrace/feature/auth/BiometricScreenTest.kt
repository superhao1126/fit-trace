package com.fittrace.feature.auth

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
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
        compose.onNodeWithText("指纹登录").performScrollTo().performClick()
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
        compose.onNodeWithText("本机指纹登录已失效，请使用邮箱登录。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("使用邮箱登录").performScrollTo().assertIsEnabled()
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
        compose.onNodeWithText("开启结果尚未确认，请先使用邮箱登录，再到设置中检查。").assertIsDisplayed()
        compose.onNodeWithText("指纹登录").performScrollTo().assertIsNotEnabled()
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
        compose.onNodeWithContentDescription("指纹登录开关").performScrollTo().performClick()
        assertEquals(0, disabled)
        compose.onNodeWithText("暂不关闭").performClick()
        assertEquals(0, disabled)
        compose.onNodeWithContentDescription("指纹登录开关").performClick()
        compose.onNodeWithText("确认关闭").performClick()
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
        compose.onNodeWithText("获取验证码").performScrollTo().performClick()
        assertEquals(1, sends)
        compose.onNodeWithText("验证并继续").performScrollTo().assertIsNotEnabled()
        capture("biometric-email-390")
    }

    @Test
    fun `settings switch does not claim off before server status is confirmed`() {
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE, binding, enabled = true),
                        notice = "网络连接异常，请检查网络后重试",
                        error = AppError.Network,
                        statusConfirmed = false,
                    ),
                    true,
                    binding.email,
                )
            }
        }
        compose.onNodeWithText("暂时无法确认状态").assertIsDisplayed()
        compose.onNodeWithContentDescription("指纹登录开关").assertDoesNotExist()
        compose.onNodeWithContentDescription("正在确认指纹登录状态").assertExists()
        compose.onNodeWithText("重试").performScrollTo().assertIsEnabled()
        capture("biometric-status-unknown-390")
    }

    @Test
    fun `unsupported device keeps toggle disabled and offers no redundant retry`() {
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(BiometricState(BiometricCapability.UNSUPPORTED)),
                    true,
                    binding.email,
                )
            }
        }
        compose.onNodeWithContentDescription("指纹登录开关").assertIsNotEnabled()
        compose.onNodeWithText("重新检查状态").assertDoesNotExist()
        compose.onNodeWithText("移除本机凭据").assertDoesNotExist()
        compose.onNodeWithText("重试").assertDoesNotExist()
    }

    @Test
    fun `expired login removal asks confirmation and keeps email as primary action`() {
        var removals = 0
        var emails = 0
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE, binding),
                        error = AppError.Business("DEVICE_CREDENTIAL_INVALID", "已失效"),
                    ),
                    false,
                    null,
                    onRemove = { removals++ },
                    onEmail = { emails++ },
                )
            }
        }
        compose.onNodeWithText("使用邮箱登录").performScrollTo().performClick()
        assertEquals(1, emails)
        compose.onNodeWithText("移除此设备上的账号").performScrollTo().performClick()
        assertEquals(0, removals)
        compose.onNodeWithText("取消").performClick()
        assertEquals(0, removals)
        compose.onNodeWithText("移除此设备上的账号").performClick()
        compose.onNodeWithText("确认移除").performClick()
        assertEquals(1, removals)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h720dp-mdpi")
    fun `large text email step keeps keyboard submit guarded and navigation reachable`() {
        var sends = 0
        var enabled = 0
        var back = 0
        compose.setContent {
            var code by remember { mutableStateOf("") }
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)
            ) {
                FitTraceTheme(darkTheme = true) {
                    BiometricScreen(
                        BiometricUiState(
                            BiometricState(BiometricCapability.AVAILABLE),
                            needsEmail = true,
                        ),
                        true,
                        binding.email,
                        code = code,
                        onCode = { code = it },
                        onSend = { sends++ },
                        onEnable = { enabled++ },
                        onBack = { back++ },
                    )
                }
            }
        }
        compose.onNodeWithText("验证并继续").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("获取验证码").performScrollTo().performClick()
        assertEquals(1, sends)
        compose.onNodeWithText("邮箱验证码").performScrollTo().performTextInput("1234567")
        compose.onNodeWithText("验证并继续").performScrollTo().performClick()
        assertEquals(1, enabled)
        compose.onNodeWithText("暂不开启").performScrollTo().performClick()
        assertEquals(1, back)
        capture("biometric-email-dark-large-320")
    }

    @Test
    fun `locked login prevents repeat prompt but email recovery works`() {
        var emails = 0
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE, binding),
                        error = AppError.Business("BIOMETRIC_LOCKED", "已锁定"),
                    ),
                    false,
                    null,
                    onEmail = { emails++ },
                )
            }
        }
        compose.onNodeWithText("指纹暂不可用").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("使用邮箱登录").performScrollTo().performClick()
        assertEquals(1, emails)
        capture("biometric-locked-390")
    }

    @Test
    fun `activation stays off until confirmed and is only requested by the user`() {
        var enables = 0
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(BiometricState(BiometricCapability.AVAILABLE)),
                    true,
                    binding.email,
                    onEnable = { enables++ },
                )
            }
        }
        compose.onNodeWithContentDescription("指纹登录开关").assertIsOff()
        assertEquals(0, enables)
        capture("biometric-settings-off-390")
        compose.onNodeWithContentDescription("指纹登录开关").performClick()
        assertEquals(1, enables)
        compose.onNodeWithContentDescription("指纹登录开关").assertIsOff()
    }

    @Test
    fun `network error during activation is not presented as an invalid email code`() {
        compose.setContent {
            FitTraceTheme {
                BiometricScreen(
                    BiometricUiState(
                        BiometricState(BiometricCapability.AVAILABLE),
                        needsEmail = true,
                        error = AppError.Network,
                        notice = "网络连接异常，请检查网络后重试",
                    ),
                    true,
                    binding.email,
                    code = "123456",
                )
            }
        }
        compose
            .onNodeWithText("邮箱验证码")
            .assert(
                SemanticsMatcher.keyNotDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.Error
                )
            )
        compose.onNodeWithText("网络连接异常，请检查网络后重试").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("验证并继续").performScrollTo().assertIsEnabled()
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
