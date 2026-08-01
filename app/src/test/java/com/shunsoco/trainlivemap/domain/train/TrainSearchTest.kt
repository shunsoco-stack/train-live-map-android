package com.shunsoco.trainlivemap.domain.train

import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainSearchTest {
    @Test
    fun `search trims query and matches train number prefix without case`() {
        val result = searchTrainsByNumberPrefix(
            trains = listOf(train("1000G"), train("1001g"), train("21000G")),
            query = " 100 ",
        )

        assertEquals(listOf("1000G", "1001g"), result.map { it.trainNumber })
    }

    @Test
    fun `search is empty for blank query and limits results`() {
        assertTrue(searchTrainsByNumberPrefix(listOf(train("1000G")), " ").isEmpty())
        assertEquals(
            2,
            searchTrainsByNumberPrefix(
                trains = listOf(train("1A"), train("1B"), train("1C")),
                query = "1",
                limit = 2,
            ).size,
        )
    }

    private fun train(number: String) = TrainLocation(
        id = number,
        lineId = "tokaido",
        lineName = "東海道線",
        lineColor = "#f68b1e",
        trainNumber = number,
        direction = TrainDirection.INBOUND,
        destination = "東京",
        trainType = TrainType.LOCAL,
        latitude = 35.68,
        longitude = 139.76,
        delayMinutes = 0,
        speedKmh = 0.0,
        status = TrainStatus.RUNNING,
        lastUpdatedAt = "2026-08-01T00:00:00Z",
        stoppedSince = null,
        dataAccuracy = DataAccuracy.ESTIMATED,
        routeSegment = null,
    )
}
