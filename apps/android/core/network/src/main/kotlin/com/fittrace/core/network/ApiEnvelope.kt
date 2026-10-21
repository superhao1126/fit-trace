package com.fittrace.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: String,
    val message: String,
    val data: T? = null,
    val meta: ListMetaDto? = null,
)

@Serializable data class HealthDto(val status: String)

@Serializable data class ListMetaDto(val page: Int, val pageSize: Int, val total: Int)
