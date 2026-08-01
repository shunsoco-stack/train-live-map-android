package com.shunsoco.trainlivemap.ui

import com.shunsoco.trainlivemap.data.remote.ApiFailure

internal enum class RetryBlockReason {
    BAD_REQUEST,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
}

internal data class PollingRetryGate(
    val lineQuery: String,
    val reason: RetryBlockReason? = null,
    val retryAtMillis: Long = 0L,
    val consecutiveServiceUnavailable: Int = 0,
)

internal fun PollingRetryGate?.allowsRequest(
    lineQuery: String,
    nowMillis: Long,
    manual: Boolean,
): Boolean {
    if (this == null || this.lineQuery != lineQuery || reason == null) return true
    return when (reason) {
        RetryBlockReason.BAD_REQUEST -> manual
        RetryBlockReason.RATE_LIMITED -> nowMillis >= retryAtMillis
        RetryBlockReason.SERVICE_UNAVAILABLE -> manual || nowMillis >= retryAtMillis
    }
}

internal fun nextPollingRetryGate(
    previous: PollingRetryGate?,
    lineQuery: String,
    apiFailure: ApiFailure?,
    nowMillis: Long,
): PollingRetryGate {
    if (apiFailure == null) return PollingRetryGate(lineQuery = lineQuery)
    return when (apiFailure) {
        is ApiFailure.BadRequest -> PollingRetryGate(
            lineQuery = lineQuery,
            reason = RetryBlockReason.BAD_REQUEST,
            retryAtMillis = Long.MAX_VALUE,
        )

        is ApiFailure.RateLimited -> PollingRetryGate(
            lineQuery = lineQuery,
            reason = RetryBlockReason.RATE_LIMITED,
            retryAtMillis = nowMillis.saturatingPlus(
                apiFailure.retryAfterMillis ?: DEFAULT_RATE_LIMIT_MILLIS,
            ),
        )

        is ApiFailure.ServiceUnavailable -> {
            val previousCount = previous
                ?.takeIf { it.lineQuery == lineQuery }
                ?.consecutiveServiceUnavailable
                ?: 0
            val failureCount = (previousCount + 1).coerceAtMost(MAX_BACKOFF_EXPONENT + 1)
            val exponentialDelay = (
                BASE_SERVICE_UNAVAILABLE_MILLIS shl
                    (failureCount - 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
                ).coerceAtMost(MAX_SERVICE_UNAVAILABLE_MILLIS)
            PollingRetryGate(
                lineQuery = lineQuery,
                reason = RetryBlockReason.SERVICE_UNAVAILABLE,
                retryAtMillis = nowMillis.saturatingPlus(
                    apiFailure.retryAfterMillis ?: exponentialDelay,
                ),
                consecutiveServiceUnavailable = failureCount,
            )
        }

        is ApiFailure.Http,
        is ApiFailure.Network,
        is ApiFailure.Unexpected,
        -> PollingRetryGate(lineQuery = lineQuery)
    }
}

private fun Long.saturatingPlus(other: Long): Long =
    if (other > 0L && this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other

private const val DEFAULT_RATE_LIMIT_MILLIS = 60_000L
private const val BASE_SERVICE_UNAVAILABLE_MILLIS = 10_000L
private const val MAX_SERVICE_UNAVAILABLE_MILLIS = 120_000L
private const val MAX_BACKOFF_EXPONENT = 4
