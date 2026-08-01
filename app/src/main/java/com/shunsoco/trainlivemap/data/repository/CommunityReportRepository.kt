package com.shunsoco.trainlivemap.data.repository

import com.shunsoco.trainlivemap.data.local.VoterIdStore
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitResponse
import com.shunsoco.trainlivemap.data.model.CommunityReportsApiResponse
import com.shunsoco.trainlivemap.data.remote.ApiFailure
import com.shunsoco.trainlivemap.data.remote.CommunityReportRemoteDataSource
import com.shunsoco.trainlivemap.data.remote.classifyApiFailure
import kotlinx.coroutines.CancellationException

data class ApiCallResult<T>(
    val data: T?,
    val error: Throwable?,
    val apiFailure: ApiFailure? = error?.let(::classifyApiFailure),
) {
    val isSuccess: Boolean
        get() = data != null && error == null
}

interface CommunityReportRepository {
    suspend fun getReports(): ApiCallResult<CommunityReportsApiResponse>

    /** Submits exactly once. Retry decisions belong to the UI/cooldown policy. */
    suspend fun submitReport(
        request: CommunityReportSubmitRequest,
    ): ApiCallResult<CommunityReportSubmitResponse>
}

class DefaultCommunityReportRepository(
    private val remoteDataSource: CommunityReportRemoteDataSource,
    private val voterIdStore: VoterIdStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : CommunityReportRepository {
    override suspend fun getReports(): ApiCallResult<CommunityReportsApiResponse> =
        callOnce(remoteDataSource::fetchReports)

    override suspend fun submitReport(
        request: CommunityReportSubmitRequest,
    ): ApiCallResult<CommunityReportSubmitResponse> = callOnce {
        remoteDataSource.submitReport(
            reporterId = voterIdStore.getOrCreateVoterId(),
            request = request,
        )
    }

    private suspend fun <T> callOnce(block: suspend () -> T): ApiCallResult<T> = try {
        ApiCallResult(
            data = block(),
            error = null,
            apiFailure = null,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        ApiCallResult(
            data = null,
            error = error,
            apiFailure = classifyApiFailure(error, clockMillis()),
        )
    }
}
