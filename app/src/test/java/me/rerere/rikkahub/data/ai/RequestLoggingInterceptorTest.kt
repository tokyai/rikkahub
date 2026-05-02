package me.rerere.rikkahub.data.ai

import me.rerere.common.android.Logging
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RequestLoggingInterceptorTest {
    @Before
    fun setUp() {
        Logging.clear()
    }

    @After
    fun tearDown() {
        Logging.clear()
    }

    @Test
    fun `intercept redacts sensitive request headers before logging`() {
        val request = Request.Builder()
            .url("https://example.com/v1/chat/completions")
            .header("Authorization", "Bearer secret")
            .header("X-Api-Key", "secret")
            .header("x-goog-api-key", "google-secret")
            .header("Content-Type", "application/json")
            .post("""{"model":"grok-4.20-expert"}""".toRequestBody("application/json".toMediaType()))
            .build()

        RequestLoggingInterceptor().intercept(FakeChain(request))

        val log = Logging.getRequestLogs().first()
        assertEquals("<redacted>", log.requestHeaders["Authorization"])
        assertEquals("<redacted>", log.requestHeaders["X-Api-Key"])
        assertEquals("<redacted>", log.requestHeaders["x-goog-api-key"])
        assertEquals("application/json", log.requestHeaders["Content-Type"])
    }
}

private class FakeChain(
    private val request: Request
) : Interceptor.Chain {
    override fun request(): Request = request

    override fun proceed(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }

    override fun connection(): Connection? = null

    override fun call(): Call = error("call is not used by this test")

    override fun connectTimeoutMillis(): Int = 0

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun readTimeoutMillis(): Int = 0

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

    override fun writeTimeoutMillis(): Int = 0

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
}
