package com.fittrace.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fittrace.core.designsystem.FitTraceTheme
import com.fittrace.feature.main.MainRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FitTraceTheme { MainRoute(versionName = BuildConfig.VERSION_NAME) } }
    }
}
