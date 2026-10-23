package com.fittrace.feature.auth

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import org.json.JSONObject

/** 独立验证视图只接收一次性验证凭据；不加载业务页面，也不接收账号会话。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun CaptchaDialog(
    challenge: CaptchaChallenge,
    onResult: (String?) -> Unit,
    onError: (String) -> Unit,
) {
    val currentResult by rememberUpdatedState(onResult)
    val currentError by rememberUpdatedState(onError)
    LaunchedEffect(challenge.id) {
        delay(120_000)
        currentError("安全验证已超时，请重试")
    }
    com.fittrace.core.designsystem.FitDialog(
        onDismissRequest = { currentResult(null) },
        title = { Text("安全验证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("由阿里云提供安全验证，验证通过后发送邮箱验证码", style = MaterialTheme.typography.bodySmall)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    factory = { context ->
                        CaptchaWebView(context).apply {
                            setBackgroundColor(Color.WHITE)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode =
                                android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean =
                                        request.isForMainFrame ||
                                            (request.url.scheme != "https" &&
                                                request.url.toString() != "about:blank")
                                }
                            val finished = AtomicBoolean(false)
                            addJavascriptInterface(
                                CaptchaBridge { proof, failed ->
                                    post {
                                        if (finished.compareAndSet(false, true)) {
                                            if (failed) currentError("安全验证加载失败，请稍后重试")
                                            else currentResult(proof)
                                        }
                                    }
                                },
                                "CaptchaResult",
                            )
                            loadDataWithBaseURL(
                                "https://fittrace.idoit.icu/",
                                captchaHtml(challenge),
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                    onReset = null,
                    onRelease = {
                        it.stopLoading()
                        it.removeJavascriptInterface("CaptchaResult")
                        it.destroy()
                    },
                    update = {},
                )
            }
        },
        confirmButton = {
            com.fittrace.core.designsystem.FitDialogButton(
                onClick = { currentResult(null) },
                primary = false,
            ) {
                Text("取消")
            }
        },
    )
}

private class CaptchaBridge(private val result: (String?, Boolean) -> Unit) {
    @JavascriptInterface
    fun success(proof: String) {
        result(proof, false)
    }

    @JavascriptInterface
    fun cancelled() {
        result(null, false)
    }

    @JavascriptInterface
    fun failed() {
        result(null, true)
    }
}

private fun captchaHtml(challenge: CaptchaChallenge): String {
    val config = challenge.config
    val payload =
        JSONObject()
            .put("region", config.region)
            .put("prefix", config.prefix)
            .put("scene", config.appSceneId)
            .toString()
    val encoded = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    return """
        <!doctype html><html lang="zh-CN"><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>body{margin:0;font:14px sans-serif;color:#202824;background:white}button{width:100%;height:48px;border:0;border-radius:12px;background:#2f7d5b;color:white;font-size:15px;margin-top:20px}button:disabled{opacity:.5}p{line-height:1.7;color:#637067}</style>
        <script>var c=JSON.parse(atob('$encoded'));window.AliyunCaptchaConfig={region:c.region,prefix:c.prefix};</script>
        </head><body><p id="hint">正在加载安全验证…</p><div id="captcha"></div><button id="verify" disabled>开始安全验证</button>
        <script>
        var ready=false;var timer=setTimeout(function(){if(!ready)CaptchaResult.failed()},15000);
        function initialize(){try{initAliyunCaptcha({SceneId:c.scene,mode:'popup',element:'#captcha',button:'#verify',language:'cn',rem:Math.min(1,Math.max(.5,(innerWidth-24)/360)),
        getInstance:function(instance){ready=true;clearTimeout(timer);document.getElementById('verify').disabled=false;document.getElementById('hint').textContent='请点击下方按钮完成验证';},
        success:function(proof){if(typeof proof==='string'&&proof.trim())CaptchaResult.success(proof);else CaptchaResult.failed();},
        onError:function(){CaptchaResult.failed();},onClose:function(reason){if(reason==='userDismiss')CaptchaResult.cancelled();}})}catch(e){CaptchaResult.failed()}}
        </script><script src="https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js" onload="initialize()" onerror="CaptchaResult.failed()"></script></body></html>
    """
        .trimIndent()
}

/** 服务端返回的静态 SVG 图形验证图；禁用 JavaScript、文件访问与所有页面导航。 */
@Composable
internal fun PasswordCaptchaImage(image: String, modifier: Modifier = Modifier) {
    val safe =
        image.takeIf {
            it.startsWith("data:image/svg+xml;base64,") &&
                it.length < 131072 &&
                it.substringAfter(',').matches(Regex("[A-Za-z0-9+/=\r\n]+"))
        }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.blockNetworkLoads = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = true
                    }
            }
        },
        onReset = null,
        onRelease = { it.destroy() },
        update = { view ->
            if (view.tag != safe) {
                view.tag = safe
                view.loadDataWithBaseURL(
                    null,
                    """<html><body style="margin:0"><img alt="图形验证码" style="width:100%;height:100%;object-fit:contain" src="${safe.orEmpty()}"></body></html>""",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}
