package com.shunsoco.trainlivemap.data.remote

import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitResponse
import com.shunsoco.trainlivemap.data.model.CommunityReportsApiResponse
import com.shunsoco.trainlivemap.data.local.isValidCommunityReporterId

interface CommunityReportRemoteDataSource {
    suspend fun fetchReports(): CommunityReportsApiResponse

    suspend fun submitReport(
        reporterId: String,
        request: CommunityReportSubmitRequest,
    ): CommunityReportSubmitResponse
}

class RetrofitCommunityReportRemoteDataSource(
    private val api: TrainLiveMapApi,
) : CommunityReportRemoteDataSource {
    override suspend fun fetchReports(): CommunityReportsApiResponse =
        api.getCommunityReports()

    override suspend fun submitReport(
        reporterId: String,
        request: CommunityReportSubmitRequest,
    ): CommunityReportSubmitResponse {
        require(isValidCommunityReporterId(reporterId)) {
            "Community reporter ID must be 12..100 ASCII letters, digits, '_' or '-'"
        }
        return api.submitCommunityReport(
            reporterId = reporterId,
            request = request,
        )
    }
}
