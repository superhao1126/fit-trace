package com.fittrace.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DeviceCredentialApiTest {
    @Test
    fun `registration requires bearer and preserves exact server payload bytes`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"code":"OK","message":"成功","data":{"challengeId":"challenge","credentialId":"credential","payload":"{\"nonce\":\"opaque\",\"scope\":\"android-prod\"}","expiresAt":"2030-01-01T00:00:00Z"}}"""
                    )
            )
            val api = NetworkClient(server.url("/api/v1/").toString()).deviceCredentialApi
            val challenge =
                api.registrationOptions(
                        "Bearer current",
                        DeviceRegistrationRequest("SPKI-key", "android-prod", "Phone", "123456"),
                    )
                    .body()!!
                    .data!!
            assertEquals("{\"nonce\":\"opaque\",\"scope\":\"android-prod\"}", challenge.payload)
            val request = server.takeRequest()
            assertEquals("/api/v1/auth/device-credentials/registration-options", request.path)
            assertEquals("Bearer current", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"emailCode\":\"123456\""))
            assertTrue(body.contains("\"publicKey\":\"SPKI-key\""))
        }
    }

    @Test
    fun `post logout login sends challenge signature without reusing bearer or refresh token`() =
        runTest {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse()
                        .setBody(
                            """{"code":"OK","message":"成功","data":{"accessToken":"new","refreshToken":"fresh","accessTokenExpiresAt":"2030-01-01T00:00:00Z","user":{"id":"owner","email":"user@example.com"}}}"""
                        )
                )
                val response =
                    NetworkClient(server.url("/api/v1/").toString())
                        .deviceCredentialApi
                        .loginVerify(DeviceVerifyRequest("challenge", "DER-signature"))
                assertEquals("fresh", response.body()?.data?.refreshToken)
                val request = server.takeRequest()
                assertNull(request.getHeader("Authorization"))
                assertEquals("/api/v1/auth/device-credentials/login-verify", request.path)
                assertEquals(
                    "{\"challengeId\":\"challenge\",\"signature\":\"DER-signature\"}",
                    request.body.readUtf8(),
                )
            }
        }
}
