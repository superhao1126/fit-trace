package com.fittrace.feature.auth

import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

/** 验证码拖图由网页处理，避免弹窗正文在手指轻微纵向偏移时抢走手势。 */
internal class CaptchaWebView(context: Context) : WebView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN)
            parent?.requestDisallowInterceptTouchEvent(true)
        return try {
            super.dispatchTouchEvent(event)
        } finally {
            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
            )
                parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
}
