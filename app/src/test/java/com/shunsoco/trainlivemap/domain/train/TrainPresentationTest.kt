package com.shunsoco.trainlivemap.domain.train

import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainPresentationTest {
    @Test
    fun `normal train uses smiling face`() {
        assertEquals(
            TrainFace.NORMAL,
            resolveTrainFace(
                status = TrainStatus.RUNNING,
                delayMinutes = 0,
            ),
        )
    }

    @Test
    fun `delayed status uses worried face`() {
        assertEquals(
            TrainFace.DELAYED,
            resolveTrainFace(
                status = TrainStatus.DELAYED,
                delayMinutes = 0,
            ),
        )
    }

    @Test
    fun `positive delay uses worried face even when API status is running`() {
        assertEquals(
            TrainFace.DELAYED,
            resolveTrainFace(
                status = TrainStatus.RUNNING,
                delayMinutes = 4,
            ),
        )
    }

    @Test
    fun `suspended status takes precedence over delay`() {
        assertEquals(
            TrainFace.SUSPENDED,
            resolveTrainFace(
                status = TrainStatus.SUSPENDED,
                delayMinutes = 12,
            ),
        )
    }

    @Test
    fun `inbound direction has up arrow and Japanese label`() {
        assertEquals("↑ 上り", directionLabelJa(TrainDirection.INBOUND))
    }

    @Test
    fun `outbound direction has down arrow and Japanese label`() {
        assertEquals("↓ 下り", directionLabelJa(TrainDirection.OUTBOUND))
    }

    @Test
    fun `TalkBack description includes semantic details but not train number`() {
        val description = trainContentDescription(
            TrainLocation(
                id = "train-1",
                lineId = "tokaido",
                lineName = "東海道線",
                lineColor = "#f68b1e",
                trainNumber = "1234E",
                direction = TrainDirection.INBOUND,
                destination = "東京",
                trainType = TrainType.RAPID,
                latitude = 35.6812,
                longitude = 139.7671,
                delayMinutes = 6,
                speedKmh = 70.0,
                status = TrainStatus.DELAYED,
                lastUpdatedAt = "2026-07-28T12:00:00Z",
                stoppedSince = null,
                dataAccuracy = DataAccuracy.ESTIMATED,
                routeSegment = null,
            ),
        )

        assertTrue(description.contains("東海道線"))
        assertTrue(description.contains("上り"))
        assertTrue(description.contains("東京行き"))
        assertTrue(description.contains("快速"))
        assertTrue(description.contains("遅延"))
        assertTrue(description.contains("6分遅れ"))
        assertFalse(description.contains("1234E"))
    }

    @Test
    fun `TalkBack description keeps delay information for a suspended train`() {
        val description = trainContentDescription(
            TrainLocation(
                id = "train-suspended",
                lineId = "tokaido",
                lineName = "東海道線",
                lineColor = "#f68b1e",
                trainNumber = "9999E",
                direction = TrainDirection.OUTBOUND,
                destination = "横浜",
                trainType = TrainType.LOCAL,
                latitude = 35.5,
                longitude = 139.6,
                delayMinutes = 12,
                speedKmh = 0.0,
                status = TrainStatus.SUSPENDED,
                lastUpdatedAt = "2026-07-28T12:00:00Z",
                stoppedSince = null,
                dataAccuracy = DataAccuracy.ESTIMATED,
                routeSegment = null,
            ),
        )

        assertTrue(description.contains("運転見合わせ"))
        assertTrue(description.contains("12分遅れ"))
    }
}
