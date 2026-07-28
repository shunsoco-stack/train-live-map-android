package com.shunsoco.trainlivemap.data.remote

import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse

interface TrainRemoteDataSource {
    suspend fun fetchTrains(): TrainsApiResponse

    suspend fun fetchServiceStatus(): ServiceStatusApiResponse

    suspend fun fetchRailways(): RailwaysApiResponse
}

class RetrofitTrainRemoteDataSource(
    private val api: TrainLiveMapApi,
) : TrainRemoteDataSource {
    override suspend fun fetchTrains(): TrainsApiResponse = api.getTrains()

    override suspend fun fetchServiceStatus(): ServiceStatusApiResponse =
        api.getServiceStatus()

    override suspend fun fetchRailways(): RailwaysApiResponse = api.getRailways()
}
