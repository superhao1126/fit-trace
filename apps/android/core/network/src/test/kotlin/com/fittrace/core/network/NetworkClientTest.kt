package com.fittrace.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class NetworkClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `health uses API prefix and accepts extra response fields`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"code":"OK","message":"请求成功","data":{"status":"ok"},"meta":null,"traceId":"future"}"""
                )
        )
        val response = NetworkClient(server.url("/api/v1/").toString()).healthApi.getHealth()
        assertEquals("ok", response.data?.status)
        assertEquals("/api/v1/health", server.takeRequest().path)
    }

    @Test
    fun `HTTP errors remain errors`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"code":"UNAUTHORIZED","message":"登录失效","data":null}""")
        )
        try {
            NetworkClient(server.url("/api/v1/").toString()).healthApi.getHealth()
            fail("Expected HTTP exception")
        } catch (error: HttpException) {
            assertEquals(401, error.code())
        }
    }

    @Test
    fun `malformed JSON is not accepted as successful data`() = runTest {
        server.enqueue(MockResponse().setBody("not-json"))
        try {
            NetworkClient(server.url("/api/v1/").toString()).healthApi.getHealth()
            fail("Expected serialization failure")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `base URL rejects missing slash and embedded credentials`() {
        for (url in listOf("https://example.com/api/v1", "https://user:pass@example.com/api/v1/")) {
            assertThrows(IllegalArgumentException::class.java) { NetworkClient(url) }
        }
    }
}
