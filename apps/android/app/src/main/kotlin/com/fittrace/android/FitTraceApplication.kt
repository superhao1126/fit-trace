package com.fittrace.android

import android.app.Application
import com.fittrace.android.di.AppContainer

class FitTraceApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this, BuildConfig.API_BASE_URL, debugLogging = BuildConfig.DEBUG)
    }
}
