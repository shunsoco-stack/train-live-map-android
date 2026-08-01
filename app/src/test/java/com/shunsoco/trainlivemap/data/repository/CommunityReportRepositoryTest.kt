package com.shunsoco.trainlivemap.data.repository

import com.shunsoco.trainlivemap.data.local.VoterIdStore
import com.shunsoco.trainlivemap.data.model.CommunityReportCounts
import com.shunsoco.trainlivemap.data.model.CommunityReportStatus
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitResponse
import com.shunsoco.trainlivemap.data.model.CommunityReportSummary
import com.shunsoco.trainlivemap.data.model.CommunityReportsApiResponse
import com.shunsoco.trainlivemap.data.remote.ApiFailure
import com.shunsoco.trainlivemap.data.remote.CommunityReportRemoteDataSource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityReportRepositoryTest {
    @Test
    fun `GET returns reports without creating a voter ID`() = runTest {
        val remote = FakeCommunityRemoteDataSource()
        var voterIdReads = 0
        val repository = DefaultCommunityReportRepository(
            remoteDataSource = remote,
            voterIdStore = VoterIdStore {
                voterIdReads += 1
                REPORTER_ID
            },
        )

        val result = repository.getReports()

        assertTrue(result.isSuccess)
        assertEquals(remote.getResponse, result.data)
        assertEquals(1, remote.fetchCount)
        assertEquals(0, voterIdReads)
    }

    @Test
    fun `POST uses persisted reporter ID and submits exactly once`() = runTest {
        val remote = FakeCommunityRemoteDataSource()
        val repository = DefaultCommunityReportRepository(
            remoteDataSource = remote,
            voterIdStore = VoterIdStore { REPORTER_ID },
        )
        val request = CommunityReportSubmitRequest(
            lineId = "tokaido",
            status = CommunityReportStatus.DELAYED,
            delayMinutes = 10,
        )

        val result = repository.submitReport(request)

        assertTrue(result.isSuccess)
        assertEquals(1, remote.submitCount)
        assertEquals(REPORTER_ID, remote.lastReporterId)
        assertEquals(request, remote.lastRequest)
    }

    @Test
    fun `POST failure is classified and never retried automatically`() = runTest {
        val failure = IOException("offline")
        val remote = FakeCommunityRemoteDataSource().apply {
            submitFailure = failure
        }
        val repository = DefaultCommunityReportRepository(
            remoteDataSource = remote,
            voterIdStore = VoterIdStore { REPORTER_ID },
        )

        val result = repository.submitReport(
            CommunityReportSubmitRequest(
                lineId = "tokaido",
                status = CommunityReportStatus.ON_TIME,
                delayMinutes = null,
            ),
        )

        assertNull(result.data)
        assertSame(failure, result.error)
        assertTrue(result.apiFailure is ApiFailure.Network)
        assertEquals(1, remote.submitCount)
    }

    private class FakeCommunityRemoteDataSource : CommunityReportRemoteDataSource {
        val getResponse = CommunityReportsApiResponse(
            summaries = listOf(SUMMARY),
            windowMinutes = 30,
            cooldownSeconds = 60,
            persistent = true,
            votingEnabled = true,
        )
        var fetchCount = 0
        var submitCount = 0
        var submitFailure: Throwable? = null
        var lastReporterId: String? = null
        var lastRequest: CommunityReportSubmitRequest? = null

        override suspend fun fetchReports(): CommunityReportsApiResponse {
            fetchCount += 1
            return getResponse
        }

        override suspend fun submitReport(
            reporterId: String,
            request: CommunityReportSubmitRequest,
        ): CommunityReportSubmitResponse {
            submitCount += 1
            lastReporterId = reporterId
            lastRequest = request
            submitFailure?.let { throw it }
            return CommunityReportSubmitResponse(
                summaries = listOf(SUMMARY),
                windowMinutes = 30,
                cooldownSeconds = 60,
                persistent = true,
                votingEnabled = true,
                summary = SUMMARY,
            )
        }
    }

    private companion object {
        const val REPORTER_ID = "reporter_1234567890"
        val SUMMARY = CommunityReportSummary(
            lineId = "tokaido",
            status = CommunityReportStatus.DELAYED,
            delayMinutes = 10,
            voteCount = 2,
            counts = CommunityReportCounts(
                onTime = 0,
                delayed = 2,
                suspended = 0,
            ),
            updatedAt = "2026-08-01T00:00:00Z",
        )
    }
}
