package com.shunsoco.trainlivemap.ui

import com.shunsoco.trainlivemap.data.remote.ApiFailure
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollingRetryPolicyTest {
    private val error = IOException("test")

    @Test
    fun `400 blocks automatic retry for only the same query`() {
        val gate = nextPollingRetryGate(null, "tokaido", ApiFailure.BadRequest(error), 1_000L)

        assertFalse(gate.allowsRequest("tokaido", 999_999L, manual = false))
        assertTrue(gate.allowsRequest("tokaido", 1_001L, manual = true))
        assertTrue(gate.allowsRequest("yamanote", 1_001L, manual = false))
    }

    @Test
    fun `429 respects retry after even for a manual request`() {
        val gate = nextPollingRetryGate(
            previous = null,
            lineQuery = "tokaido",
            apiFailure = ApiFailure.RateLimited(error, retryAfterMillis = 30_000L),
            nowMillis = 5_000L,
        )

        assertFalse(gate.allowsRequest("tokaido", 34_999L, manual = true))
        assertTrue(gate.allowsRequest("tokaido", 35_000L, manual = false))
    }

    @Test
    fun `503 uses bounded exponential backoff and manual retry can bypass it`() {
        val first = nextPollingRetryGate(
            previous = null,
            lineQuery = "tokaido",
            apiFailure = ApiFailure.ServiceUnavailable(error, retryAfterMillis = null),
            nowMillis = 0L,
        )
        val second = nextPollingRetryGate(
            previous = first,
            lineQuery = "tokaido",
            apiFailure = ApiFailure.ServiceUnavailable(error, retryAfterMillis = null),
            nowMillis = 10_000L,
        )

        assertEquals(10_000L, first.retryAtMillis)
        assertEquals(30_000L, second.retryAtMillis)
        assertFalse(second.allowsRequest("tokaido", 20_000L, manual = false))
        assertTrue(second.allowsRequest("tokaido", 20_000L, manual = true))
    }
}
