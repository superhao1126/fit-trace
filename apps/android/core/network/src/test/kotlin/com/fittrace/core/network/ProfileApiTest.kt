package com.fittrace.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ProfileApiTest {
    private val user =
        """{"code":"OK","message":"成功","data":{"id":"u","email":"hello@example.com","nickname":null,"gender":"UNSPECIFIED","avatarUrl":null,"createdAt":"2026-10-01T00:00:00Z","goal":{"type":"LOSE_FAT","startWeight":71,"targetWeight":68,"targetDate":null,"startedAt":"2026-10-01T00:00:00Z"}}}"""

    @Test
    fun `explicit avatar removal includes null without affecting ordinary profile patches`() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody("""{"code":"OK","message":"","data":null}"""))
                NetworkClient(server.url("/").toString())
                    .profileApi
                    .updateProfileWithAvatar(
                        "Bearer token",
                        ProfileWithAvatarUpdateDto("昵称", "UNSPECIFIED", null),
                    )
                assertTrue(server.takeRequest().body.readUtf8().contains("\"avatarUrl\":null"))
            }
        }

    @Test
    fun `profile reads nullable fields and latest body can be absent`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = NetworkClient(server.url("/api/v1/").toString()).profileApi
            server.enqueue(MockResponse().setBody(user))
            val response = api.profile("Bearer access")
            assertNull(response.body()?.data?.nickname)
            assertEquals(68.0, response.body()?.data?.goal?.targetWeight)
            assertEquals("/api/v1/auth/me", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"code":"OK","message":"成功","data":null}"""))
            assertNull(api.latestBody("Bearer access").body()?.data)
            val request = server.takeRequest()
            assertEquals("/api/v1/body-records/latest", request.path)
            assertEquals("Bearer access", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `profile patch preserves avatar and goal patch does not invent body record`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val api = NetworkClient(server.url("/api/v1/").toString()).profileApi
            server.enqueue(MockResponse().setBody(user))
            api.updateProfile("Bearer access", ProfileUpdateDto("新昵称", "FEMALE"))
            val profile = server.takeRequest()
            assertEquals("PATCH", profile.method)
            assertEquals("/api/v1/users/me/profile", profile.path)
            assertFalse(profile.body.readUtf8().contains("avatarUrl"))
            server.enqueue(MockResponse().setBody(user))
            api.updateGoal("Bearer access", GoalUpdateDto("LOSE_FAT", 68.0))
            val goal = server.takeRequest()
            assertEquals("PATCH", goal.method)
            assertEquals("/api/v1/users/me/goal", goal.path)
            assertFalse(goal.body.readUtf8().contains("currentWeight"))
        }
    }
}
