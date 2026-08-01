package com.shunsoco.trainlivemap.ui

import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType
import com.shunsoco.trainlivemap.data.local.UserPreferences
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainUiStateServiceStatusTest {
    @Test
    fun `effective status derives disruption from the current train snapshot`() {
        val state = MainUiState(
            trains = listOf(train(delayMinutes = 8)),
            serviceStatus = officialStatus(),
            preferences = visiblePreferences("tokaido"),
            trainResponseLineQuery = "tokaido",
            serviceResponseLineQuery = "tokaido",
            nowMillis = NOW_MILLIS,
        )

        assertEquals(ServiceSeverity.MINOR, state.effectiveServiceStatus?.severity)
        assertEquals(
            "列車位置情報では最大8分程度の遅れが確認されています。",
            state.effectiveServiceStatus?.message,
        )
        // The API value remains authoritative and is never overwritten by the estimate.
        assertEquals(ServiceSeverity.NORMAL, state.serviceStatus?.severity)
    }

    @Test
    fun `effective status returns to official normal when train evidence becomes old`() {
        val official = officialStatus()
        val state = MainUiState(
            trains = listOf(train(delayMinutes = 20)),
            serviceStatus = official,
            nowMillis = Instant.parse("2026-07-31T08:47:00.001Z").toEpochMilli(),
        )

        assertSame(official, state.effectiveServiceStatus)
    }

    @Test
    fun `official non-normal status remains authoritative`() {
        val official = officialStatus().copy(
            severity = ServiceSeverity.MINOR,
            message = "運転を再開しましたが、一部列車に遅れがでています。",
        )
        val state = MainUiState(
            trains = listOf(train(delayMinutes = 72)),
            serviceStatus = official,
            nowMillis = NOW_MILLIS,
        )

        assertSame(official, state.effectiveServiceStatus)
    }

    @Test
    fun `effective status uses the all-line API entry for a visible line`() {
        val tokaido = officialStatus()
        val yamanote = officialStatus(
            lineId = "yamanote",
            lineName = "山手線",
        )
        val state = MainUiState(
            trains = listOf(train(delayMinutes = 11, lineId = "yamanote")),
            serviceStatus = tokaido,
            serviceStatuses = listOf(tokaido, yamanote),
            preferences = UserPreferences(
                visibleLineIds = setOf("yamanote"),
                visibleLineIdsInitialized = true,
            ),
            trainResponseLineQuery = "yamanote",
            serviceResponseLineQuery = "yamanote",
            nowMillis = NOW_MILLIS,
        )

        assertEquals("yamanote", state.effectiveServiceStatus?.lineId)
        assertEquals(ServiceSeverity.MINOR, state.effectiveServiceStatus?.severity)
        assertEquals(2, state.effectiveServiceStatuses.size)
    }

    @Test
    fun `effective status is hidden when the user hides every line`() {
        val official = officialStatus()
        val state = MainUiState(
            serviceStatus = official,
            serviceStatuses = listOf(official),
            preferences = UserPreferences(
                visibleLineIds = emptySet(),
                visibleLineIdsInitialized = true,
            ),
        )

        assertEquals(null, state.effectiveServiceStatus)
    }

    @Test
    fun `favorites-only status selection matches the lines rendered on the map`() {
        val tokaido = officialStatus().copy(severity = ServiceSeverity.MAJOR)
        val yamanote = officialStatus(
            lineId = "yamanote",
            lineName = "山手線",
        ).copy(severity = ServiceSeverity.MINOR)
        val state = MainUiState(
            serviceStatus = tokaido,
            serviceStatuses = listOf(tokaido, yamanote),
            preferences = UserPreferences(
                favoriteLineIds = setOf("yamanote"),
                visibleLineIds = setOf("tokaido", "yamanote"),
                favoritesOnly = true,
                visibleLineIdsInitialized = true,
            ),
            serviceResponseLineQuery = "yamanote",
        )

        assertEquals("yamanote", state.effectiveServiceStatus?.lineId)
    }

    private fun officialStatus(
        lineId: String = "tokaido",
        lineName: String = "東海道線",
    ) = ServiceStatus(
        lineId = lineId,
        lineName = lineName,
        severity = ServiceSeverity.NORMAL,
        message = "平常どおり運転しています。",
        updatedAt = "2026-07-31T08:44:00Z",
        dataAccuracy = DataAccuracy.ACTUAL,
    )

    private fun visiblePreferences(lineId: String) = UserPreferences(
        visibleLineIds = setOf(lineId),
        visibleLineIdsInitialized = true,
    )

    private fun train(
        delayMinutes: Int,
        lineId: String = "tokaido",
    ) = TrainLocation(
        id = "train-1",
        lineId = lineId,
        lineName = if (lineId == "tokaido") "東海道線" else "山手線",
        lineColor = "#f68b1e",
        trainNumber = "",
        direction = TrainDirection.INBOUND,
        destination = "東京",
        trainType = TrainType.LOCAL,
        latitude = 35.68116,
        longitude = 139.76713,
        delayMinutes = delayMinutes,
        speedKmh = 0.0,
        status = if (delayMinutes > 0) TrainStatus.DELAYED else TrainStatus.RUNNING,
        lastUpdatedAt = "2026-07-31T08:45:00Z",
        stoppedSince = null,
        dataAccuracy = DataAccuracy.ESTIMATED,
        routeSegment = null,
    )

    private companion object {
        val NOW_MILLIS: Long = Instant.parse("2026-07-31T08:45:30Z").toEpochMilli()
    }
}
