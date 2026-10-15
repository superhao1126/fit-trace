package com.fittrace.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthApiTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun cleanup() {
        server.shutdown()
    }

    @Test
    fun `login uses native JSON contract and decodes nullable profile fields`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"code":"OK","message":"成功","meta":null,"data":{"accessToken":"access","accessTokenExpiresAt":"2030-01-01T00:00:00Z","refreshToken":"refresh","user":{"id":"u1","email":"user@example.com","nickname":null,"hasPassword":null,"goal":null}}}"""
                )
        )
        val result =
            NetworkClient(server.url("/api/v1/").toString())
                .authApi
                .loginByEmail(EmailLoginRequest("user@example.com", "123456", true))
        assertEquals("refresh", result.body()?.data?.refreshToken)
        assertNull(result.body()?.data?.user?.nickname)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login/email", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains(""""acceptedTerms":true"""))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `protected request includes bearer header only on specified API call`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"code":"OK","message":"成功","data":{"id":"u1","email":"user@example.com"}}"""
                )
        )
        NetworkClient(server.url("/api/v1/").toString()).authApi.currentUser("Bearer test-token")
        assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `email verification serializes app scene and preserves opaque proof`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"code":"OK","message":"成功","data":{"retryAfterSeconds":60,"expiresInSeconds":600}}"""
                )
        )
        NetworkClient(server.url("/api/v1/").toString())
            .authApi
            .sendCode(EmailCodeRequest("user@example.com", "LOGIN", " opaque-proof ", "app"))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""captchaPlatform":"app""""))
        assertTrue(body.contains(""""captchaVerifyParam":" opaque-proof """"))
    }
}
