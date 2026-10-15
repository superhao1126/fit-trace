package com.fittrace.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

fun interface NetworkLogSink {
    fun write(event: NetworkLogEvent)
}

enum class NetworkLogStage {
    REQUEST,
    RESPONSE,
    FAILURE,
}

/** 仅用于 Debug 的完整参数诊断，包含用户授权明文输出的认证字段。 */
data class NetworkLogEvent(
    val requestId: Long,
    val stage: NetworkLogStage,
    val method: String,
    val path: String,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val failureType: String? = null,
    val url: String? = null,
    val body: String? = null,
) {
    /** 每行带请求编号，长行按小段输出，避免 Logcat 单条大小限制。 */
    fun logLines(): List<String> {
        val summary =
            when (stage) {
                NetworkLogStage.REQUEST -> "--> $method ${url ?: path}"
                NetworkLogStage.RESPONSE ->
                    "<-- $statusCode $method ${url ?: path} (${durationMs}ms)"
                NetworkLogStage.FAILURE ->
                    "<-- FAILED $method ${url ?: path} ${failureType} (${durationMs}ms)"
            }
        val text = if (body == null) summary else "$summary\n$body"
        return text
            .lineSequence()
            .flatMap { line -> line.ifEmpty { " " }.chunked(800).asSequence() }
            .map { "#$requestId $it" }
            .toList()
    }
}

internal class NetworkDiagnostics(private val sink: NetworkLogSink) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = sequence.incrementAndGet()
        val startedAt = System.nanoTime()
        fun elapsed() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        emit(
            NetworkLogEvent(
                id,
                NetworkLogStage.REQUEST,
                request.method,
                request.url.encodedPath,
                url = request.url.toString(),
                body = request.body?.diagnosticBody(),
            )
        )
        try {
            val response = chain.proceed(request)
            val duration = elapsed()
            emit(
                NetworkLogEvent(
                    id,
                    NetworkLogStage.RESPONSE,
                    request.method,
                    request.url.encodedPath,
                    statusCode = response.code,
                    durationMs = duration,
                    url = request.url.toString(),
                    body = response.diagnosticBody(),
                )
            )
            return response
        } catch (error: IOException) {
            emit(
                NetworkLogEvent(
                    id,
                    NetworkLogStage.FAILURE,
                    request.method,
                    request.url.encodedPath,
                    durationMs = elapsed(),
                    failureType = error.javaClass.simpleName,
                    url = request.url.toString(),
                )
            )
            throw error
        }
    }

    private fun emit(event: NetworkLogEvent) {
        try {
            sink.write(event)
        } catch (_: RuntimeException) {}
    }

    companion object {
        private val sequence = AtomicLong()
    }
}

private const val MAX_BODY_BYTES = 64 * 1024L
private val prettyJson = Json { prettyPrint = true }

private fun String.prettyBody(): String {
    if (trimStart().firstOrNull() !in listOf('{', '[')) return this
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), prettyJson.parseToJsonElement(this))
    } catch (_: IllegalArgumentException) {
        this
    }
}

private fun isText(type: okhttp3.MediaType?): Boolean =
    type != null &&
        (type.type == "text" ||
            type.subtype.contains("json") ||
            type.subtype.contains("xml") ||
            type.subtype == "x-www-form-urlencoded")

private fun RequestBody.diagnosticBody(): String =
    try {
        when {
            isOneShot() || isDuplex() -> "[流式或一次性请求体，跳过读取]"
            !isText(contentType()) -> "[非文本请求体，跳过读取]"
            contentLength() !in 0..MAX_BODY_BYTES -> "[请求体超过 64 KiB 或长度未知，跳过读取]"
            else ->
                Buffer()
                    .also { writeTo(it) }
                    .readString(contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                    .prettyBody()
        }
    } catch (error: Exception) {
        "[请求体日志读取失败：${error.javaClass.simpleName}]"
    }

private fun Response.diagnosticBody(): String =
    try {
        val original = body
        when {
            original == null -> "[无响应体]"
            original.contentType()?.subtype == "event-stream" -> "[流式响应，跳过读取]"
            original.contentType() != null && !isText(original.contentType()) -> "[非文本响应体，跳过读取]"
            else -> {
                // peekBody 不消费 Retrofit 后续需要读取的原始响应体。
                val bytes = peekBody(MAX_BODY_BYTES + 1).bytes()
                if (bytes.size > MAX_BODY_BYTES) "[响应体超过 64 KiB，跳过输出]"
                else
                    String(bytes, original.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                        .prettyBody()
            }
        }
    } catch (error: Exception) {
        "[响应体日志读取失败：${error.javaClass.simpleName}]"
    }
