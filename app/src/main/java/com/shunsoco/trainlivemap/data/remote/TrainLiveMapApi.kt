package com.shunsoco.trainlivemap.data.remote

import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse
import retrofit2.http.GET

interface TrainLiveMapApi {
    @GET("api/trains")
    suspend fun getTrains(): TrainsApiResponse

    @GET("api/service-status")
    suspend fun getServiceStatus(): ServiceStatusApiResponse

    @GET("api/railways")
    suspend fun getRailways(): RailwaysApiResponse
}
