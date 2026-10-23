package com.fittrace.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class NetworkClient(
    baseUrl: String,
    client: OkHttpClient = defaultHttpClient(),
    logSink: NetworkLogSink? = null,
) {
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
            .client(
                if (logSink == null) client
                else
                    client
                        .newBuilder()
                        .apply { interceptors().add(0, NetworkDiagnostics(logSink)) }
                        .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    val recordsApi: RecordsApi = retrofit.create(RecordsApi::class.java)
    val healthApi: HealthApi = retrofit.create(HealthApi::class.java)
    val deviceCredentialApi: DeviceCredentialApi = retrofit.create(DeviceCredentialApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val profileApi: ProfileApi = retrofit.create(ProfileApi::class.java)
    val dashboardApi: DashboardApi = retrofit.create(DashboardApi::class.java)
}

// 默认不输出日志；Debug 由 app 显式注入日志接收器，认证续期由数据层处理。
fun defaultHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
