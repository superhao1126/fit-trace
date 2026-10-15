package com.fittrace.core.model

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    data object Network : AppError

    data class Http(val statusCode: Int) : AppError

    data class Business(
        val code: String,
        val message: String,
        val statusCode: Int? = null,
        val retryAfterSeconds: Int? = null,
    ) : AppError

    data object InvalidResponse : AppError

    data object Storage : AppError
}

data class HealthStatus(val available: Boolean)
