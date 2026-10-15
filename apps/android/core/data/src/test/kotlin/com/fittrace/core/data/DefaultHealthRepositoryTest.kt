package com.fittrace.core.data

import com.fittrace.core.model.AppError
import com.fittrace.core.model.AppResult
import com.fittrace.core.model.HealthStatus
import com.fittrace.core.network.ApiEnvelope
import com.fittrace.core.network.HealthApi
import com.fittrace.core.network.HealthDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DefaultHealthRepositoryTest {
    private fun repository(block: suspend () -> ApiEnvelope<HealthDto>) =
        DefaultHealthRepository(
            object : HealthApi {
                override suspend fun getHealth() = block()
            }
        )

    @Test
    fun `successful DTO maps to domain model`() = runTest {
        assertEquals(
            AppResult.Success(HealthStatus(true)),
            repository { ApiEnvelope("OK", "成功", HealthDto("ok")) }.getHealth(),
        )
    }

    @Test
    fun `HTTP 200 with business failure is not success`() = runTest {
        assertEquals(
            AppResult.Failure(AppError.Business("UNAVAILABLE", "不可用")),
            repository { ApiEnvelope("UNAVAILABLE", "不可用") }.getHealth(),
        )
    }

    @Test
    fun `missing or unknown health status is rejected`() = runTest {
        for (data in listOf(null, HealthDto("unknown"))) {
            assertEquals(
                AppResult.Failure(AppError.InvalidResponse),
                repository { ApiEnvelope("OK", "成功", data) }.getHealth(),
            )
        }
    }

    @Test
    fun `network failure maps to domain error`() = runTest {
        assertEquals(
            AppResult.Failure(AppError.Network),
            repository { throw IOException() }.getHealth(),
        )
    }

    @Test
    fun `serialization failure maps to domain error`() = runTest {
        assertEquals(
            AppResult.Failure(AppError.InvalidResponse),
            repository { throw IllegalArgumentException("invalid payload") }.getHealth(),
        )
    }

    @Test
    fun `cancellation propagates to coroutine caller`() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            repository { throw cancellation }.getHealth()
            fail("Cancellation must propagate")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }
}
