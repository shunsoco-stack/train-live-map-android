package com.shunsoco.trainlivemap.data.model

import com.shunsoco.trainlivemap.data.remote.ApiClientFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiJsonDecodingTest {
    private val json = ApiClientFactory.json

    @Test
    fun `decodes trains response and LngLat arrays`() {
        val decoded = json.decodeFromString<TrainsApiResponse>(TRAINS_JSON)

        assertEquals("2026-07-28T11:00:07Z", decoded.generatedAt)
        assertEquals("2026-07-28T20:00:00+09:00", decoded.dataUpdatedAt)
        assertTrue(decoded.isMock)
        assertEquals(ProviderSource.MOCK, decoded.source)
        assertTrue(decoded.fallback)
        assertEquals("フォールバックデータを表示中", decoded.notice)
        assertEquals(1, decoded.trains.size)

        val train = decoded.trains.single()
        assertEquals("tokaido", train.lineId)
        assertEquals(TrainDirection.INBOUND, train.direction)
        assertEquals(TrainType.SPECIAL_RAPID, train.trainType)
        assertEquals(TrainStatus.DELAYED, train.status)
        assertEquals(DataAccuracy.ESTIMATED, train.dataAccuracy)
        assertEquals(5, train.delayMinutes)
        assertNull(train.stoppedSince)

        val segment = requireNotNull(train.routeSegment)
        assertEquals(0.2, segment.fromFraction, 0.0)
        assertEquals(0.8, segment.toFraction, 0.0)
        assertEquals(
            LngLat(longitude = 139.7671, latitude = 35.6812),
            segment.coordinates?.first(),
        )
        assertEquals(
            LngLat(longitude = 139.7009, latitude = 35.6895),
            segment.coordinates?.last(),
        )
    }

    @Test
    fun `decodes service status response`() {
        val decoded = json.decodeFromString<ServiceStatusApiResponse>(SERVICE_STATUS_JSON)

        assertFalse(decoded.isMock)
        assertEquals(ProviderSource.ODPT, decoded.source)
        assertFalse(decoded.fallback)
        assertNull(decoded.notice)
        assertEquals("tokaido", decoded.serviceStatus.lineId)
        assertEquals("東海道線", decoded.serviceStatus.lineName)
        assertEquals(ServiceSeverity.MINOR, decoded.serviceStatus.severity)
        assertEquals(DataAccuracy.ACTUAL, decoded.serviceStatus.dataAccuracy)
        assertEquals(listOf("tokaido", "yamanote"), decoded.serviceStatuses?.map { it.lineId })
    }

    @Test
    fun `legacy service status cache without line id remains decodable`() {
        val decoded = json.decodeFromString<ServiceStatusApiResponse>(LEGACY_SERVICE_STATUS_JSON)

        assertEquals("tokaido", decoded.serviceStatus.lineId)
        assertNull(decoded.serviceStatuses)
    }

    @Test
    fun `decodes railway lines options coverage and nested coordinate arrays`() {
        val decoded = json.decodeFromString<RailwaysApiResponse>(RAILWAYS_JSON)

        assertEquals(RailwaySource.ODPT, decoded.source)
        assertEquals(1, decoded.lines.size)
        assertEquals(1, decoded.options.size)

        val line = decoded.lines.single()
        assertEquals("odpt.Railway:JR-East.Tokaido", line.odptId)
        assertEquals(2, line.coordinates.single().size)
        assertEquals(139.76713, line.coordinates.single().first().longitude, 0.0)

        val option = decoded.options.single()
        assertTrue(option.available)
        assertEquals(RailwayCoverage.LIMITED, option.coverage)
        assertEquals(RailwayKind.LINE, option.kind)
        assertEquals(listOf("東海道", "Tokaido"), option.aliases)
    }

    private companion object {
        val TRAINS_JSON = """
            {
              "trains": [
                {
                  "id": "odpt.Train:JR-East.Tokaido.1234E",
                  "lineId": "tokaido",
                  "lineName": "東海道線",
                  "lineColor": "#f68b1e",
                  "trainNumber": "1234E",
                  "direction": "inbound",
                  "destination": "東京",
                  "trainType": "special_rapid",
                  "latitude": 35.685,
                  "longitude": 139.75,
                  "delayMinutes": 5,
                  "speedKmh": 72,
                  "status": "delayed",
                  "lastUpdatedAt": "2026-07-28T20:00:00+09:00",
                  "stoppedSince": null,
                  "dataAccuracy": "estimated",
                  "routeSegment": {
                    "fromFraction": 0.2,
                    "toFraction": 0.8,
                    "coordinates": [
                      [139.7671, 35.6812],
                      [139.7009, 35.6895]
                    ]
                  },
                  "futureServerField": "ignored"
                }
              ],
              "generatedAt": "2026-07-28T11:00:07Z",
              "dataUpdatedAt": "2026-07-28T20:00:00+09:00",
              "isMock": true,
              "source": "mock",
              "fallback": true,
              "notice": "フォールバックデータを表示中",
              "futureResponseField": 1
            }
        """.trimIndent()

        val SERVICE_STATUS_JSON = """
            {
              "serviceStatus": {
                "lineId": "tokaido",
                "lineName": "東海道線",
                "severity": "minor",
                "message": "一部列車に遅れ",
                "updatedAt": "2026-07-28T20:00:00+09:00",
                "dataAccuracy": "actual"
              },
              "serviceStatuses": [
                {
                  "lineId": "tokaido",
                  "lineName": "東海道線",
                  "severity": "minor",
                  "message": "一部列車に遅れ",
                  "updatedAt": "2026-07-28T20:00:00+09:00",
                  "dataAccuracy": "actual"
                },
                {
                  "lineId": "yamanote",
                  "lineName": "山手線",
                  "severity": "normal",
                  "message": "平常どおり運転しています。",
                  "updatedAt": "2026-07-28T20:00:00+09:00",
                  "dataAccuracy": "actual"
                }
              ],
              "isMock": false,
              "source": "odpt",
              "fallback": false,
              "notice": null
            }
        """.trimIndent()

        val LEGACY_SERVICE_STATUS_JSON = """
            {
              "serviceStatus": {
                "lineName": "東海道線",
                "severity": "normal",
                "message": "平常運転",
                "updatedAt": "2026-07-28T20:00:00+09:00",
                "dataAccuracy": "actual"
              },
              "isMock": false,
              "source": "odpt",
              "fallback": false,
              "notice": null
            }
        """.trimIndent()

        val RAILWAYS_JSON = """
            {
              "lines": [
                {
                  "id": "tokaido",
                  "odptId": "odpt.Railway:JR-East.Tokaido",
                  "name": "東海道線",
                  "color": "#f68b1e",
                  "coordinates": [
                    [
                      [139.76713, 35.68116],
                      [139.73919, 35.62866]
                    ]
                  ]
                }
              ],
              "options": [
                {
                  "id": "tokaido",
                  "name": "東海道線",
                  "category": "東海道方面",
                  "color": "#f68b1e",
                  "aliases": ["東海道", "Tokaido"],
                  "coverage": "limited",
                  "coverageNote": "東京駅から小田原駅まで",
                  "kind": "line",
                  "available": true
                }
              ],
              "generatedAt": "2026-07-28T11:00:00Z",
              "source": "odpt"
            }
        """.trimIndent()
    }
}
