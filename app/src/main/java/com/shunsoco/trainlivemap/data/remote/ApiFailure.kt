package com.shunsoco.trainlivemap.data.remote

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import retrofit2.HttpException

/** A stable, reusable classification for network and HTTP failures. */
sealed interface ApiFailure {
    val cause: Throwable

    data class BadRequest(
        override val cause: Throwable,
    ) : ApiFailure

    data class RateLimited(
        override val cause: Throwable,
        /** Server-requested delay from the classification time, in milliseconds. */
        val retryAfterMillis: Long?,
    ) : ApiFailure

    data class ServiceUnavailable(
        override val cause: Throwable,
        /** Server-requested delay from the classification time, in milliseconds. */
        val retryAfterMillis: Long?,
    ) : ApiFailure

    data class Http(
        override val cause: Throwable,
        val statusCode: Int,
    ) : ApiFailure

    data class Network(
        override val cause: Throwable,
    ) : ApiFailure

    data class Unexpected(
        override val cause: Throwable,
    ) : ApiFailure
}

fun classifyApiFailure(
    error: Throwable,
    nowMillis: Long = System.currentTimeMillis(),
): ApiFailure {
    if (error is HttpException) {
        val retryAfterMillis = parseRetryAfterMillis(
            value = error.response()?.headers()?.get("Retry-After"),
            nowMillis = nowMillis,
        )
        return classifyHttpFailure(
            statusCode = error.code(),
            retryAfterMillis = retryAfterMillis,
            cause = error,
        )
    }
    return if (error is IOException) {
        ApiFailure.Network(error)
    } else {
        ApiFailure.Unexpected(error)
    }
}

internal fun classifyHttpFailure(
    statusCode: Int,
    retryAfterMillis: Long?,
    cause: Throwable,
): ApiFailure = when (statusCode) {
    400 -> ApiFailure.BadRequest(cause)
    429 -> ApiFailure.RateLimited(cause, retryAfterMillis)
    503 -> ApiFailure.ServiceUnavailable(cause, retryAfterMillis)
    else -> ApiFailure.Http(cause, statusCode)
}

/**
 * Parses both RFC 9110 Retry-After forms: non-negative delta-seconds and an
 * RFC 1123 HTTP date. Invalid/overflowing values are ignored.
 */
internal fun parseRetryAfterMillis(
    value: String?,
    nowMillis: Long,
): Long? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    normalized.toLongOrNull()
        ?.takeIf { it >= 0L }
        ?.let { seconds ->
            return runCatching { Math.multiplyExact(seconds, 1_000L) }.getOrNull()
        }

    val retryAtMillis = runCatching {
        ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli()
    }.getOrNull() ?: return null
    return (retryAtMillis - nowMillis).coerceAtLeast(0L)
}
