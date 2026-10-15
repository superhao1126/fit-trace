package com.fittrace.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class NetworkClient(baseUrl: String, client: OkHttpClient = defaultHttpClient()) {
    init {
        val url = baseUrl.toHttpUrl()
        require(url.encodedPath.endsWith("/")) { "API base URL must end with /" }
        require(
            url.username.isEmpty() &&
                url.password.isEmpty() &&
                url.query == null &&
                url.fragment == null
        ) {
            "API base URL must not contain credentials, query or fragment"
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    val healthApi: HealthApi = retrofit.create(HealthApi::class.java)
}

// 不记录请求体、Authorization 或用户健康数据；认证续期在登录模块接入时实现。
fun defaultHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
