package com.fittrace.core.domain

import com.fittrace.core.model.AppResult
import com.fittrace.core.model.Dashboard

interface DashboardRepository {
    suspend fun overview(date: String, timezoneOffset: Int): AppResult<Dashboard>
}
