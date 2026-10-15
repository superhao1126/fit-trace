package com.fittrace.core.network

import retrofit2.http.GET

interface HealthApi {
    @GET("health") suspend fun getHealth(): ApiEnvelope<HealthDto>
}
