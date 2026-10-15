package com.fittrace.core.network

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetworkDiagnosticsTest {
    private lateinit var server: MockWebServer
    private val events = mutableListOf<NetworkLogEvent>()

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
    fun `records method static path status duration and matching request id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        client().authApi.currentUser("Bearer private-token")
        assertEquals(2, events.size)
        assertEquals(NetworkLogStage.REQUEST, events[0].stage)
        assertEquals(NetworkLogStage.RESPONSE, events[1].stage)
        assertEquals(events[0].requestId, events[1].requestId)
        assertEquals("GET", events[0].method)
        assertEquals("/api/v1/auth/me", events[0].path)
        assertEquals(401, events[1].statusCode)
        assertTrue(requireNotNull(events[1].durationMs) >= 0)
        assertFalse(events.toString().contains("private-token"))
    }

    @Test
    fun `logs plaintext credentials and response fields without consuming response`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "private-cookie")
                .setBody(
                    """{"code":"OK","message":"private-message","data":{"accessToken":"private-access","accessTokenExpiresAt":"2026-10-15T12:00:00Z","refreshToken":"private-refresh","user":{"id":"private-id","email":"private@example.com"}}}"""
                )
        )
        val response =
            client()
                .authApi
                .loginByPassword(
                    PasswordLoginRequest(
                        "private@example.com",
                        "private-password",
                        "private-captcha-id",
                        "private-code",
                    )
                )
        assertEquals("private-access", response.body()?.data?.accessToken)
        val request = server.takeRequest()
        assertTrue(request.body.readUtf8().contains("private-password"))
        assertTrue(events[0].body!!.contains("private-password"))
        assertTrue(events[0].body!!.contains("private-captcha-id"))
        assertTrue(events[1].body!!.contains("private-access"))
        assertTrue(events[1].body!!.contains("private-refresh"))
        assertTrue(events[1].body!!.contains("private@example.com"))
        assertFalse(events.toString().contains("private-cookie"))
    }

    @Test
    fun `logs complete URL including query parameters`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":"OK","message":"成功","data":null}"""))
        val queryClient =
            OkHttpClient.Builder()
                .addInterceptor(NetworkDiagnostics(NetworkLogSink(events::add)))
                .build()
        val url = server.url("/api/v1/auth/me?token=plain-token&page=2")
        queryClient.newCall(okhttp3.Request.Builder().url(url).build()).execute().use {
            assertEquals(200, it.code)
        }
        assertTrue(events[0].url!!.contains("token=plain-token&page=2"))
        assertEquals("/api/v1/auth/me", events[0].path)
    }

    @Test
    fun `chunks log lines without losing JSON content and identifies every line`() {
        val body = "x".repeat(5000)
        val event =
            NetworkLogEvent(
                12,
                NetworkLogStage.RESPONSE,
                "GET",
                "/api/v1/health",
                200,
                20,
                body = body,
            )
        val lines = event.logLines()
        assertTrue(lines.all { it.startsWith("#12 ") && it.length <= 804 })
        assertEquals(body, lines.drop(1).joinToString("") { it.removePrefix("#12 ") })
    }

    @Test
    fun `large response and invalid JSON do not change the original body`() {
        val transport =
            OkHttpClient.Builder()
                .addInterceptor(NetworkDiagnostics(NetworkLogSink(events::add)))
                .build()
        val large = "x".repeat(70 * 1024)
        server.enqueue(MockResponse().setHeader("Content-Type", "text/plain").setBody(large))
        transport
            .newCall(okhttp3.Request.Builder().url(server.url("/large")).build())
            .execute()
            .use { assertEquals(large, it.body!!.string()) }
        assertTrue(events.last().body!!.contains("超过 64 KiB"))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody("invalid-json")
        )
        transport
            .newCall(okhttp3.Request.Builder().url(server.url("/invalid")).build())
            .execute()
            .use { assertEquals("invalid-json", it.body!!.string()) }
        assertEquals("invalid-json", events.last().body)
    }

    @Test
    fun `does not replay a one-shot request body`() {
        var writes = 0
        val body =
            object : okhttp3.RequestBody() {
                override fun contentType() = "application/json".toMediaType()

                override fun isOneShot() = true

                override fun writeTo(sink: okio.BufferedSink) {
                    writes++
                    sink.writeUtf8("{}")
                }
            }
        server.enqueue(MockResponse().setBody("{}"))
        val transport =
            OkHttpClient.Builder()
                .addInterceptor(NetworkDiagnostics(NetworkLogSink(events::add)))
                .build()
        transport
            .newCall(okhttp3.Request.Builder().url(server.url("/once")).post(body).build())
            .execute()
            .close()
        assertEquals(1, writes)
        assertTrue(events.first().body!!.contains("一次性"))
    }

    @Test
    fun `records network failure type and rethrows without exposing exception message`() = runTest {
        val failure = IOException("private-host private-token")
        val transport = OkHttpClient.Builder().addInterceptor { throw failure }.build()
        try {
            client(transport).authApi.currentUser("Bearer private-token")
            fail("Expected original failure")
        } catch (error: IOException) {
            assertEquals(failure.message, error.message)
        }
        assertEquals(NetworkLogStage.FAILURE, events.last().stage)
        assertEquals("IOException", events.last().failureType)
        assertFalse(events.toString().contains("private"))
    }

    @Test
    fun `logging sink failure does not change a successful request`() = runTest {
        server.enqueue(MockResponse().setBody("""{"code":"OK","message":"成功","data":null}"""))
        val response =
            NetworkClient(
                    server.url("/api/v1/").toString(),
                    logSink = NetworkLogSink { throw IllegalStateException("Logging unavailable") },
                )
                .authApi
                .currentUser("Bearer token")
        assertEquals(200, response.code())
    }

    @Test
    fun `default client has no diagnostic interceptor and still sends requests`() = runTest {
        val transport = defaultHttpClient()
        assertTrue(transport.interceptors.isEmpty())
        server.enqueue(MockResponse().setBody("""{"code":"OK","message":"成功","data":null}"""))
        val response =
            NetworkClient(server.url("/api/v1/").toString(), transport)
                .authApi
                .currentUser("Bearer token")
        assertEquals(200, response.code())
        assertTrue(events.isEmpty())
    }

    private fun client(transport: OkHttpClient = defaultHttpClient()) =
        NetworkClient(server.url("/api/v1/").toString(), transport, NetworkLogSink(events::add))
}
