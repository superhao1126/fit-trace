package com.fittrace.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fittrace.core.designsystem.FitTraceTheme

private const val Welcome = "welcome"
private const val About = "about"

/** 工程启动入口；业务页面完成设计后，以独立 feature 模块接入。 */
@Composable
fun MainRoute(versionName: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Welcome) {
        composable(Welcome) {
            WelcomeScreen(onAbout = { navController.navigate(About) { launchSingleTop = true } })
        }
        composable(About) { AboutScreen(versionName, onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun WelcomeScreen(onAbout: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.brand_slogan), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onAbout) { Text(stringResource(R.string.about_title)) }
        }
    }
}

@Composable
private fun AboutScreen(versionName: String, onBack: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                stringResource(R.string.about_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(stringResource(R.string.version, versionName))
            Text(stringResource(R.string.brand_slogan))
        }
    }
}

@Preview(showBackground = true, locale = "zh")
@Composable
private fun WelcomePreview() {
    FitTraceTheme { WelcomeScreen(onAbout = {}) }
}
