package me.rerere.ai.provider.providers.openai

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors

class ResponseAPIThreadingTest {
    @Test
    fun `response body is read off caller dispatcher`() {
        val callerThreadName = "response-api-caller"
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com/v1/responses").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                ForbiddenThreadResponseBody(
                    delegate = "ok".toResponseBody("text/plain".toMediaType()),
                    forbiddenThreadName = callerThreadName,
                )
            )
            .build()
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, callerThreadName)
        }.asCoroutineDispatcher()

        try {
            val body = runBlocking(dispatcher) {
                response.readBodyStringOnIo()
            }

            assertEquals("ok", body)
        } finally {
            response.close()
            dispatcher.close()
        }
    }
}

private class ForbiddenThreadResponseBody(
    private val delegate: ResponseBody,
    private val forbiddenThreadName: String,
) : ResponseBody() {
    override fun contentLength(): Long = delegate.contentLength()

    override fun contentType(): MediaType? = delegate.contentType()

    override fun source(): BufferedSource {
        return object : ForwardingSource(delegate.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                check(Thread.currentThread().name != forbiddenThreadName) {
                    "Response body read on caller dispatcher"
                }
                return super.read(sink, byteCount)
            }
        }.buffer()
    }
}
