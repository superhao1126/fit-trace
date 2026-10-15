package com.fittrace.core.domain

import com.fittrace.core.model.AppResult
import com.fittrace.core.model.HealthStatus

interface HealthRepository {
    suspend fun getHealth(): AppResult<HealthStatus>
}
