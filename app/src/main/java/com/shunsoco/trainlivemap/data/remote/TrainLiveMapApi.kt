package com.shunsoco.trainlivemap.data.remote

import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitResponse
import com.shunsoco.trainlivemap.data.model.CommunityReportsApiResponse
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface TrainLiveMapApi {
    @GET("api/trains")
    suspend fun getTrains(
        @Query("lines") lines: String? = null,
    ): TrainsApiResponse

    @GET("api/service-status")
    suspend fun getServiceStatus(
        @Query("lines") lines: String? = null,
    ): ServiceStatusApiResponse

    @GET("api/railways")
    suspend fun getRailways(): RailwaysApiResponse

    @GET("api/community-reports")
    suspend fun getCommunityReports(): CommunityReportsApiResponse

    @Headers("Content-Type: application/json")
    @POST("api/community-reports")
    suspend fun submitCommunityReport(
        @Header("X-Community-Reporter") reporterId: String,
        @Body request: CommunityReportSubmitRequest,
    ): CommunityReportSubmitResponse
}
