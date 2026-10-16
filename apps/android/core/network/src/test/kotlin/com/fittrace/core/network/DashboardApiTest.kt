package com.fittrace.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DashboardApiTest {
    @Test
    fun `dashboard query uses local timezone convention and nullable calories`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"code":"OK","message":"成功","meta":null,"data":{"date":"2026-10-16","latestBodyRecord":null,"today":{"bodyRecord":null,"calories":null,"mealCount":0,"workoutCount":0,"workoutMinutes":0,"photoCount":0,"mealPreview":[],"workoutPreview":[],"photoPreview":[]},"recentTrend":{"days":7,"from":"2026-10-10","to":"2026-10-16","weightChange":null,"bodyRecordedDays":0,"mealRecordedDays":0,"workoutCount":0,"workoutMinutes":0}}}"""
                    )
            )
            val response =
                NetworkClient(server.url("/api/v1/").toString())
                    .dashboardApi
                    .overview("Bearer access", "2026-10-16", 480)
            assertNull(response.body()?.data?.today?.calories)
            assertEquals("2026-10-16", response.body()?.data?.date)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/dashboard", request.requestUrl?.encodedPath)
            assertEquals("480", request.requestUrl?.queryParameter("tzOffset"))
            assertEquals("2026-10-16", request.requestUrl?.queryParameter("date"))
            assertEquals("Bearer access", request.getHeader("Authorization"))
        }
    }
}
