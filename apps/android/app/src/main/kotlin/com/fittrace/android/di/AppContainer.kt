package com.fittrace.android.di

import android.content.Context
import com.fittrace.core.data.DefaultAuthRepository
import com.fittrace.core.data.DefaultHealthRepository
import com.fittrace.core.domain.AuthRepository
import com.fittrace.core.domain.HealthRepository
import com.fittrace.core.network.NetworkClient
import com.fittrace.core.session.KeystoreSessionStore

/** 唯一的应用级依赖装配入口，feature 只接收领域接口。 */
class AppContainer(context: Context, baseUrl: String) {
    private val network = NetworkClient(baseUrl)
    val healthRepository: HealthRepository = DefaultHealthRepository(network.healthApi)
    val authRepository: AuthRepository =
        DefaultAuthRepository(network.authApi, KeystoreSessionStore(context))
}
