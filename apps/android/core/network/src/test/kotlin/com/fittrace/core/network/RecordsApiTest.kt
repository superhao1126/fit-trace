package com.fittrace.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RecordsApiTest {
    @Test
    fun `insights forwards range timezone and preserves unknown calories`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"code":"OK","message":"","data":{"days":7,"calories":{"average":null,"highest":null,"recordedDays":0},"meals":{"count":1,"recordedDays":1,"byType":[]},"workouts":{"count":3,"totalMinutes":96,"byType":[]}}}"""
                    )
            )
            val result =
                NetworkClient(server.url("/").toString())
                    .recordsApi
                    .insights("Bearer token", 7, 480)
                    .body()!!
                    .data!!
            assertNull(result.calories.average)
            assertEquals(3, result.workouts.count)
            val request = server.takeRequest()
            assertEquals("/insights/overview", request.requestUrl!!.encodedPath)
            assertEquals("7", request.requestUrl!!.queryParameter("days"))
            assertEquals("480", request.requestUrl!!.queryParameter("tzOffset"))
            assertEquals("Bearer token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `list forwards range in original timezone and decodes page metadata`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"code":"OK","message":"","data":[],"meta":{"page":2,"pageSize":20,"total":41}}"""
                    )
            )
            val api = NetworkClient(server.url("/api/v1/").toString()).recordsApi
            val response =
                api.bodies(
                    "Bearer token",
                    2,
                    "2026-10-19T00:00:00+08:00",
                    "2026-10-20T00:00:00+08:00",
                )
            assertEquals(41, response.body()?.meta?.total)
            val request = server.takeRequest()
            assertEquals("/api/v1/body-records", request.requestUrl?.encodedPath)
            assertEquals("2026-10-19T00:00:00+08:00", request.requestUrl?.queryParameter("from"))
            assertEquals("Bearer token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `unknown meal calories stay null and quick workout editing omits exercise replacement`() =
        runTest {
            MockWebServer().use { server ->
                val api = NetworkClient(server.url("/").toString()).recordsApi
                server.enqueue(MockResponse().setBody("""{"code":"OK","message":"","data":null}"""))
                api.createMeal(
                    "Bearer token",
                    MealInputDto(
                        "LUNCH",
                        "2026-10-19T12:00:00+08:00",
                        listOf(FoodEntryDto("自制午餐", "1 碗", null)),
                        null,
                        null,
                    ),
                )
                val meal = server.takeRequest().body.readUtf8()
                assertTrue(meal.contains("自制午餐"))
                assertTrue(meal.contains("\"imageUrl\":null"))
                assertFalse(meal.contains("\"calories\":0"))
                server.enqueue(MockResponse().setBody("""{"code":"OK","message":"","data":null}"""))
                api.updateWorkout(
                    "Bearer token",
                    "existing",
                    WorkoutInputDto("STRENGTH", "训练", "2026-10-19T12:00:00Z", 30, null, null),
                )
                val workout = server.takeRequest()
                assertEquals("PATCH", workout.method)
                assertFalse(workout.body.readUtf8().contains("exercises"))
            }
        }

    @Test
    fun `upload uses authenticated multipart file and delete permits null data`() = runTest {
        MockWebServer().use { server ->
            val api = NetworkClient(server.url("/").toString()).recordsApi
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"code":"OK","message":"","data":{"url":"https://example.test/photo.jpg"}}"""
                    )
            )
            assertEquals(
                "https://example.test/photo.jpg",
                api.upload("Bearer token", imagePart(byteArrayOf(1, 2, 3), "image/jpeg"))
                    .body()
                    ?.data
                    ?.url,
            )
            val request = server.takeRequest()
            assertEquals("Bearer token", request.getHeader("Authorization"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            assertTrue(request.body.readUtf8().contains("name=\"file\""))
            server.enqueue(MockResponse().setBody("""{"code":"OK","message":"","data":null}"""))
            assertNull(api.delete("Bearer token", "progress-photos", "id").body()?.data)
            assertEquals("DELETE", server.takeRequest().method)
        }
    }
}
