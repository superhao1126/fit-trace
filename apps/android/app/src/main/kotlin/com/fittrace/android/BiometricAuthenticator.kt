package com.fittrace.android

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.fittrace.core.domain.BiometricSigner
import com.fittrace.core.model.*
import java.security.KeyStore
import java.security.Signature
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 只接受 CryptoObject 中获系统授权的签名操作；取消时关闭系统窗口。 */
class BiometricAuthenticator(private val activity: FragmentActivity) : BiometricSigner {
    override suspend fun sign(alias: String, payload: String): AppResult<String> {
        val signature =
            try {
                val key =
                    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
                        as java.security.PrivateKey
                Signature.getInstance("SHA256withECDSA").apply { initSign(key) }
            } catch (_: KeyPermanentlyInvalidatedException) {
                return AppResult.Failure(
                    AppError.Business("BIOMETRIC_INVALIDATED", "生物识别已变化，请使用邮箱登录后重新开启")
                )
            } catch (_: Exception) {
                return AppResult.Failure(
                    AppError.Business("BIOMETRIC_KEY_UNAVAILABLE", "设备密钥不可用，请使用邮箱登录后重新开启")
                )
            }
        return suspendCancellableCoroutine { continuation ->
            val prompt =
                BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            val value =
                                try {
                                    val authorized =
                                        result.cryptoObject?.signature
                                            ?: error("Missing authorized signature")
                                    authorized.update(payload.toByteArray(Charsets.UTF_8))
                                    AppResult.Success(
                                        Base64.encodeToString(
                                            authorized.sign(),
                                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                                        )
                                    )
                                } catch (_: Exception) {
                                    AppResult.Failure(
                                        AppError.Business("BIOMETRIC_SIGN_FAILED", "系统验证未完成，请重试")
                                    )
                                }
                            if (continuation.isActive) continuation.resume(value)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            val cancelled =
                                errorCode in
                                    listOf(
                                        BiometricPrompt.ERROR_CANCELED,
                                        BiometricPrompt.ERROR_USER_CANCELED,
                                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                                    )
                            if (continuation.isActive)
                                continuation.resume(
                                    AppResult.Failure(
                                        AppError.Business(
                                            if (cancelled) "BIOMETRIC_CANCELLED"
                                            else "BIOMETRIC_UNAVAILABLE",
                                            if (cancelled) "已取消验证，可使用邮箱登录"
                                            else "生物识别暂不可用：$errString",
                                        )
                                    )
                                )
                        }
                    },
                )
            continuation.invokeOnCancellation {
                activity.runOnUiThread { prompt.cancelAuthentication() }
            }
            try {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("循形生物识别验证")
                        .setSubtitle("验证后授权本机密钥完成登录签名")
                        .setAllowedAuthenticators(
                            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                        )
                        .setNegativeButtonText("取消，使用邮箱")
                        .build(),
                    BiometricPrompt.CryptoObject(signature),
                )
            } catch (_: Exception) {
                if (continuation.isActive)
                    continuation.resume(
                        AppResult.Failure(
                            AppError.Business("BIOMETRIC_UNAVAILABLE", "无法启动系统验证，请稍后重试")
                        )
                    )
            }
        }
    }
}
