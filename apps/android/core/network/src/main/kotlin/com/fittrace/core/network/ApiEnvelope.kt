package com.fittrace.core.network

import kotlinx.serialization.Serializable

@Serializable data class ApiEnvelope<T>(val code: String, val message: String, val data: T? = null)

@Serializable data class HealthDto(val status: String)
