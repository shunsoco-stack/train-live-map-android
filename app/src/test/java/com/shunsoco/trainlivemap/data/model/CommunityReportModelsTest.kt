package com.shunsoco.trainlivemap.data.model

import com.shunsoco.trainlivemap.data.remote.ApiClientFactory
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityReportModelsTest {
    @Test
    fun `GET response decodes Web community report contract`() {
        val response = ApiClientFactory.json.decodeFromString<CommunityReportsApiResponse>(
            GET_RESPONSE_JSON,
        )

        assertEquals(30, response.windowMinutes)
        assertEquals(60, response.cooldownSeconds)
        assertTrue(response.persistent)
        assertTrue(response.votingEnabled)
        val summary = response.summaries.single()
        assertEquals("tokaido", summary.lineId)
        assertEquals(CommunityReportStatus.DELAYED, summary.status)
        assertEquals(12, summary.delayMinutes)
        assertEquals(4, summary.voteCount)
        assertEquals(2, summary.counts.delayed)
    }

    @Test
    fun `POST response includes aggregate list and submitted line summary`() {
        val response = ApiClientFactory.json.decodeFromString<CommunityReportSubmitResponse>(
            POST_RESPONSE_JSON,
        )

        assertEquals(response.summaries.single(), response.summary)
        assertFalse(response.summary.status == CommunityReportStatus.SUSPENDED)
    }

    @Test
    fun `POST body encodes exact status and null delay semantics`() {
        val delayed = CommunityReportSubmitRequest(
            lineId = "yamanote",
            status = CommunityReportStatus.DELAYED,
            delayMinutes = 8,
        )
        val onTime = CommunityReportSubmitRequest(
            lineId = "yamanote",
            status = CommunityReportStatus.ON_TIME,
            delayMinutes = null,
        )

        val delayedJson = ApiClientFactory.json
            .encodeToJsonElement(CommunityReportSubmitRequest.serializer(), delayed)
            .jsonObject
        assertEquals(
            setOf("lineId", "status", "delayMinutes"),
            delayedJson.keys,
        )
        assertEquals(JsonPrimitive("delayed"), delayedJson["status"])
        assertEquals(JsonPrimitive(8), delayedJson["delayMinutes"])

        val onTimeJson = ApiClientFactory.json
            .encodeToJsonElement(CommunityReportSubmitRequest.serializer(), onTime)
            .jsonObject
        assertEquals(JsonPrimitive("on-time"), onTimeJson["status"])
        assertEquals(JsonNull, onTimeJson["delayMinutes"])
    }

    @Test
    fun `submission enforces backend delay validation`() {
        assertThrows(IllegalArgumentException::class.java) {
            CommunityReportSubmitRequest(
                lineId = "tokaido",
                status = CommunityReportStatus.DELAYED,
                delayMinutes = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommunityReportSubmitRequest(
                lineId = "tokaido",
                status = CommunityReportStatus.SUSPENDED,
                delayMinutes = 10,
            )
        }
    }

    private companion object {
        val GET_RESPONSE_JSON = """
            {
              "summaries": [
                {
                  "lineId": "tokaido",
                  "status": "delayed",
                  "delayMinutes": 12,
                  "voteCount": 4,
                  "counts": {"onTime": 1, "delayed": 2, "suspended": 1},
                  "updatedAt": "2026-08-01T00:00:00.000Z"
                }
              ],
              "windowMinutes": 30,
              "cooldownSeconds": 60,
              "persistent": true,
              "votingEnabled": true
            }
        """.trimIndent()

        val POST_RESPONSE_JSON = """
            {
              "summaries": [
                {
                  "lineId": "tokaido",
                  "status": "delayed",
                  "delayMinutes": 12,
                  "voteCount": 4,
                  "counts": {"onTime": 1, "delayed": 2, "suspended": 1},
                  "updatedAt": "2026-08-01T00:00:00.000Z"
                }
              ],
              "windowMinutes": 30,
              "cooldownSeconds": 60,
              "persistent": true,
              "votingEnabled": true,
              "summary": {
                "lineId": "tokaido",
                "status": "delayed",
                "delayMinutes": 12,
                "voteCount": 4,
                "counts": {"onTime": 1, "delayed": 2, "suspended": 1},
                "updatedAt": "2026-08-01T00:00:00.000Z"
              }
            }
        """.trimIndent()
    }
}
