package com.shunsoco.trainlivemap.domain.train

import com.shunsoco.trainlivemap.data.model.TrainStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainFilteringTest {
    @Test
    fun `all includes unknown status`() {
        assertTrue(
            matchesStatusFilter(
                status = TrainStatus.UNKNOWN,
                delayMinutes = 0,
                filter = TrainStatusFilter.ALL,
            ),
        )
    }

    @Test
    fun `running includes on-time and delayed running trains`() {
        assertTrue(
            matchesStatusFilter(
                status = TrainStatus.RUNNING,
                delayMinutes = 0,
                filter = TrainStatusFilter.RUNNING,
            ),
        )
        assertTrue(
            matchesStatusFilter(
                status = TrainStatus.DELAYED,
                delayMinutes = 5,
                filter = TrainStatusFilter.RUNNING,
            ),
        )
        assertFalse(
            matchesStatusFilter(
                status = TrainStatus.STOPPED,
                delayMinutes = 0,
                filter = TrainStatusFilter.RUNNING,
            ),
        )
    }

    @Test
    fun `stopped only includes stopped status`() {
        assertTrue(
            matchesStatusFilter(
                status = TrainStatus.STOPPED,
                delayMinutes = 0,
                filter = TrainStatusFilter.STOPPED,
            ),
        )
        assertFalse(
            matchesStatusFilter(
                status = TrainStatus.RUNNING,
                delayMinutes = 0,
                filter = TrainStatusFilter.STOPPED,
            ),
        )
    }

    @Test
    fun `delayed matches delayed status or positive delay`() {
        assertTrue(
            matchesStatusFilter(
                status = TrainStatus.DELAYED,
                delayMinutes = 0,
                filter = TrainStatusFilter.DELAYED,
            ),
        )
        assertTrue(
            matchesStatusFilter(
                status = TrainStatus.RUNNING,
                delayMinutes = 2,
                filter = TrainStatusFilter.DELAYED,
            ),
        )
        assertFalse(
            matchesStatusFilter(
                status = TrainStatus.RUNNING,
                delayMinutes = 0,
                filter = TrainStatusFilter.DELAYED,
            ),
        )
    }

    @Test
    fun `suspended takes precedence over a positive delay`() {
        assertTrue(
            matchesStatusFilter(
                status = TrainStatus.SUSPENDED,
                delayMinutes = 10,
                filter = TrainStatusFilter.SUSPENDED,
            ),
        )
        assertFalse(
            matchesStatusFilter(
                status = TrainStatus.SUSPENDED,
                delayMinutes = 10,
                filter = TrainStatusFilter.DELAYED,
            ),
        )
    }
}
