package com.fittrace.android.di

import com.fittrace.core.data.DefaultHealthRepository
import com.fittrace.core.domain.HealthRepository
import com.fittrace.core.network.NetworkClient

/** 应用级 composition root；实现类仅在这里装配，通过构造参数注入消费方。 */
class AppContainer(baseUrl: String) {
    private val network = NetworkClient(baseUrl)
    val healthRepository: HealthRepository = DefaultHealthRepository(network.healthApi)
}
