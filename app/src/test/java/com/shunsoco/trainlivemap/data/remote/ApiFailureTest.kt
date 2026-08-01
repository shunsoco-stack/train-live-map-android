package com.shunsoco.trainlivemap.data.remote

import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFailureTest {
    @Test
    fun `HTTP 400 is non-retryable bad request`() {
        val cause = IllegalStateException("400")
        val failure = classifyHttpFailure(400, null, cause)

        assertTrue(failure is ApiFailure.BadRequest)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `HTTP 429 preserves Retry-After delay`() {
        val failure = classifyHttpFailure(
            statusCode = 429,
            retryAfterMillis = 60_000L,
            cause = IllegalStateException("429"),
        )

        assertEquals(60_000L, (failure as ApiFailure.RateLimited).retryAfterMillis)
    }

    @Test
    fun `Retrofit HTTP exception is routed through status classifier`() {
        val exception = HttpException(
            Response.error<Unit>(429, "{}".toResponseBody()),
        )

        val failure = classifyApiFailure(exception, nowMillis = 0L)

        assertTrue(failure is ApiFailure.RateLimited)
        assertNull((failure as ApiFailure.RateLimited).retryAfterMillis)
        assertSame(exception, failure.cause)
    }

    @Test
    fun `HTTP 503 preserves Retry-After delay`() {
        val failure = classifyHttpFailure(
            statusCode = 503,
            retryAfterMillis = 15_000L,
            cause = IllegalStateException("503"),
        )

        assertEquals(
            15_000L,
            (failure as ApiFailure.ServiceUnavailable).retryAfterMillis,
        )
    }

    @Test
    fun `delta seconds and HTTP date Retry-After forms are parsed`() {
        val now = Instant.parse("2026-08-01T00:00:00Z")
        val retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.format(
            now.plusSeconds(75).atZone(ZoneOffset.UTC),
        )

        assertEquals(12_000L, parseRetryAfterMillis("12", now.toEpochMilli()))
        assertEquals(75_000L, parseRetryAfterMillis(retryAt, now.toEpochMilli()))
        assertEquals(0L, parseRetryAfterMillis("0", now.toEpochMilli()))
    }

    @Test
    fun `invalid or overflowing Retry-After is ignored`() {
        assertNull(parseRetryAfterMillis("later", 0L))
        assertNull(parseRetryAfterMillis("-1", 0L))
        assertNull(parseRetryAfterMillis(Long.MAX_VALUE.toString(), 0L))
    }

    @Test
    fun `IO failures are classified as network failures`() {
        val cause = IOException("offline")
        val failure = classifyApiFailure(cause)

        assertTrue(failure is ApiFailure.Network)
        assertSame(cause, failure.cause)
    }
}
