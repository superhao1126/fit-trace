package com.fittrace.android.di

import android.content.Context
import android.util.Log
import com.fittrace.core.data.DefaultAuthRepository
import com.fittrace.core.data.DefaultHealthRepository
import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.domain.HealthRepository
import com.fittrace.core.network.NetworkClient
import com.fittrace.core.network.NetworkLogSink
import com.fittrace.core.network.NetworkLogStage
import com.fittrace.core.session.KeystoreSessionStore

/** 唯一的应用级依赖装配入口，feature 只接收领域接口。 */
class AppContainer(context: Context, baseUrl: String, debugLogging: Boolean = false) {
    private val network =
        NetworkClient(
            baseUrl,
            logSink =
                if (debugLogging)
                    NetworkLogSink { event ->
                        if (
                            event.stage == NetworkLogStage.FAILURE || (event.statusCode ?: 0) >= 400
                        )
                            event.logLines().forEach { Log.w("FitTrace.Http", it) }
                        else event.logLines().forEach { Log.d("FitTrace.Http", it) }
                    }
                else null,
        )
    val healthRepository: HealthRepository = DefaultHealthRepository(network.healthApi)
    val authRepository: AuthRepository =
        DefaultAuthRepository(network.authApi, KeystoreSessionStore(context))
}
