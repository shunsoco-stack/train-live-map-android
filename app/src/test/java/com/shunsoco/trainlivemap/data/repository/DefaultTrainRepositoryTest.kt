package com.shunsoco.trainlivemap.data.repository

import com.shunsoco.trainlivemap.data.local.SnapshotStore
import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.LngLat
import com.shunsoco.trainlivemap.data.model.ProviderSource
import com.shunsoco.trainlivemap.data.model.RailwayCoverage
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.data.model.RailwayKind
import com.shunsoco.trainlivemap.data.model.RailwayMapLine
import com.shunsoco.trainlivemap.data.model.RailwaySource
import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.RouteSegmentEstimate
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse
import com.shunsoco.trainlivemap.data.remote.ApiClientFactory
import com.shunsoco.trainlivemap.data.remote.TrainRemoteDataSource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTrainRepositoryTest {
    @Test
    fun `successful responses replace endpoint snapshots`() = runTest {
        val remote = FakeRemoteDataSource()
        val cache = MemorySnapshotStore()
        val repository = DefaultTrainRepository(remote, cache)

        val trains = repository.refreshTrains()
        val status = repository.refreshServiceStatus()
        val railways = repository.refreshRailways()

        assertTrue(trains.isSuccess)
        assertTrue(status.isSuccess)
        assertTrue(railways.isSuccess)
        assertFalse(trains.fromCache)
        assertNotNull(cache.trainsJson)
        assertNotNull(cache.serviceStatusJson)
        assertNotNull(cache.railwaysJson)

        assertEquals(
            remote.trainsResponse,
            ApiClientFactory.json.decodeFromString<TrainsApiResponse>(
                requireNotNull(cache.trainsJson),
            ),
        )
        assertEquals(
            remote.statusResponse,
            ApiClientFactory.json.decodeFromString<ServiceStatusApiResponse>(
                requireNotNull(cache.serviceStatusJson),
            ),
        )
        assertEquals(
            remote.railwaysResponse,
            ApiClientFactory.json.decodeFromString<RailwaysApiResponse>(
                requireNotNull(cache.railwaysJson),
            ),
        )
    }

    @Test
    fun `API failure returns each last successful snapshot as stale`() = runTest {
        val remote = FakeRemoteDataSource()
        val cache = MemorySnapshotStore()
        val repository = DefaultTrainRepository(remote, cache)
        repository.refreshTrains()
        repository.refreshServiceStatus()
        repository.refreshRailways()

        val failure = IOException("offline")
        remote.failure = failure

        val trains = repository.refreshTrains()
        val status = repository.refreshServiceStatus()
        val railways = repository.refreshRailways()

        assertTrue(trains.fromCache)
        assertTrue(status.fromCache)
        assertTrue(railways.fromCache)
        assertSame(failure, trains.error)
        assertSame(failure, status.error)
        assertSame(failure, railways.error)
        assertEquals(remote.trainsResponse, trains.data)
        assertEquals(remote.statusResponse, status.data)
        assertEquals(remote.railwaysResponse, railways.data)
        assertTrue(requireNotNull(trains.data).isMock)
        assertTrue(requireNotNull(trains.data).fallback)
        assertEquals("モックへフォールバック", trains.data?.notice)
    }

    @Test
    fun `API failure without a valid cache preserves error and returns no data`() = runTest {
        val remote = FakeRemoteDataSource().apply {
            failure = IOException("offline")
        }
        val cache = MemorySnapshotStore().apply {
            trainsJson = "{not valid json"
        }
        val repository = DefaultTrainRepository(remote, cache)

        val result = repository.refreshTrains()

        assertNull(result.data)
        assertFalse(result.fromCache)
        assertSame(remote.failure, result.error)
    }

    private class FakeRemoteDataSource : TrainRemoteDataSource {
        var failure: IOException? = null
        val trainsResponse: TrainsApiResponse = sampleTrainsResponse()
        val statusResponse: ServiceStatusApiResponse = sampleStatusResponse()
        val railwaysResponse: RailwaysApiResponse = sampleRailwaysResponse()

        override suspend fun fetchTrains(): TrainsApiResponse {
            failure?.let { throw it }
            return trainsResponse
        }

        override suspend fun fetchServiceStatus(): ServiceStatusApiResponse {
            failure?.let { throw it }
            return statusResponse
        }

        override suspend fun fetchRailways(): RailwaysApiResponse {
            failure?.let { throw it }
            return railwaysResponse
        }
    }

    private class MemorySnapshotStore : SnapshotStore {
        var trainsJson: String? = null
        var serviceStatusJson: String? = null
        var railwaysJson: String? = null

        override suspend fun readTrainsJson(): String? = trainsJson

        override suspend fun writeTrainsJson(json: String) {
            trainsJson = json
        }

        override suspend fun readServiceStatusJson(): String? = serviceStatusJson

        override suspend fun writeServiceStatusJson(json: String) {
            serviceStatusJson = json
        }

        override suspend fun readRailwaysJson(): String? = railwaysJson

        override suspend fun writeRailwaysJson(json: String) {
            railwaysJson = json
        }
    }

    private companion object {
        fun sampleTrainsResponse() = TrainsApiResponse(
            trains = listOf(
                TrainLocation(
                    id = "train-1",
                    lineId = "tokaido",
                    lineName = "東海道線",
                    lineColor = "#f68b1e",
                    trainNumber = "1234E",
                    direction = TrainDirection.INBOUND,
                    destination = "東京",
                    trainType = TrainType.LOCAL,
                    latitude = 35.68116,
                    longitude = 139.76713,
                    delayMinutes = 3,
                    speedKmh = 60.0,
                    status = TrainStatus.DELAYED,
                    lastUpdatedAt = "2026-07-28T20:00:00+09:00",
                    stoppedSince = null,
                    dataAccuracy = DataAccuracy.MOCK,
                    routeSegment = RouteSegmentEstimate(
                        fromFraction = 0.0,
                        toFraction = 1.0,
                        coordinates = listOf(
                            LngLat(139.76713, 35.68116),
                            LngLat(139.73919, 35.62866),
                        ),
                    ),
                ),
            ),
            generatedAt = "2026-07-28T11:00:07Z",
            dataUpdatedAt = "2026-07-28T20:00:00+09:00",
            isMock = true,
            source = ProviderSource.MOCK,
            fallback = true,
            notice = "モックへフォールバック",
        )

        fun sampleStatusResponse() = ServiceStatusApiResponse(
            serviceStatus = ServiceStatus(
                lineName = "東海道線",
                severity = ServiceSeverity.NORMAL,
                message = "平常運転",
                updatedAt = "2026-07-28T20:00:00+09:00",
                dataAccuracy = DataAccuracy.ACTUAL,
            ),
            isMock = false,
            source = ProviderSource.ODPT,
            fallback = false,
            notice = null,
        )

        fun sampleRailwaysResponse() = RailwaysApiResponse(
            lines = listOf(
                RailwayMapLine(
                    id = "tokaido",
                    odptId = "odpt.Railway:JR-East.Tokaido",
                    name = "東海道線",
                    color = "#f68b1e",
                    coordinates = listOf(
                        listOf(
                            LngLat(139.76713, 35.68116),
                            LngLat(139.73919, 35.62866),
                        ),
                    ),
                ),
            ),
            options = listOf(
                RailwayFilterOption(
                    id = "tokaido",
                    name = "東海道線",
                    category = "東海道方面",
                    color = "#f68b1e",
                    aliases = listOf("東海道", "Tokaido"),
                    coverage = RailwayCoverage.REALTIME,
                    coverageNote = null,
                    kind = RailwayKind.LINE,
                    available = true,
                ),
            ),
            generatedAt = "2026-07-28T11:00:00Z",
            source = RailwaySource.ODPT,
        )
    }
}
