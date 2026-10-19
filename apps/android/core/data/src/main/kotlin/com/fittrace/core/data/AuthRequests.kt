package com.fittrace.core.data

import com.fittrace.core.model.AppError
import com.fittrace.core.model.AppResult
import com.fittrace.core.network.ApiEnvelope
import com.fittrace.core.network.ApiErrorDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.Response

internal class AuthRequests {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> call(block: suspend () -> Response<ApiEnvelope<T>>): AppResult<T> =
        when (val result = nullableCall(block)) {
            is AppResult.Failure -> result
            is AppResult.Success ->
                result.data?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(AppError.InvalidResponse)
        }

    suspend fun <T> unit(block: suspend () -> Response<ApiEnvelope<T>>): AppResult<Unit> =
        when (val result = nullableCall(block)) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(Unit)
        }

    internal suspend fun <T> nullableCall(
        block: suspend () -> Response<ApiEnvelope<T>>
    ): AppResult<T?> =
        try {
            val response = block()
            if (!response.isSuccessful) {
                val error =
                    response.errorBody()?.use { body ->
                        runCatching { json.decodeFromString<ApiErrorDto>(body.string()) }
                            .getOrNull()
                    }
                val retryAfter =
                    response.headers()["Retry-After"]?.toIntOrNull()?.coerceIn(1, 86400)
                AppResult.Failure(
                    if (error != null && error.code.isNotBlank())
                        AppError.Business(
                            error.code,
                            error.message.take(300),
                            response.code(),
                            retryAfter,
                        )
                    else AppError.Http(response.code())
                )
            } else {
                val body = response.body()
                when {
                    body == null -> AppResult.Failure(AppError.InvalidResponse)
                    body.code != "OK" ->
                        AppResult.Failure(AppError.Business(body.code, body.message.take(300)))
                    else -> AppResult.Success(body.data)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (_: IllegalArgumentException) {
            AppResult.Failure(AppError.InvalidResponse)
        }
}
