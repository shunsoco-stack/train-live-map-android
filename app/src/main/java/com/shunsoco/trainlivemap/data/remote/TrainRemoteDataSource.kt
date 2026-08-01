package com.shunsoco.trainlivemap.data.remote

import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse

interface TrainRemoteDataSource {
    suspend fun fetchTrains(): TrainsApiResponse

    /**
     * Compatibility overload for callers that have not selected a line set
     * yet. Implementations predating scoped requests can keep implementing the
     * no-argument method while new callers pass the selected IDs here.
     */
    suspend fun fetchTrains(lineIds: Set<String>): TrainsApiResponse = fetchTrains()

    suspend fun fetchServiceStatus(): ServiceStatusApiResponse

    suspend fun fetchServiceStatus(lineIds: Set<String>): ServiceStatusApiResponse =
        fetchServiceStatus()

    suspend fun fetchRailways(): RailwaysApiResponse
}

class RetrofitTrainRemoteDataSource(
    private val api: TrainLiveMapApi,
) : TrainRemoteDataSource {
    override suspend fun fetchTrains(): TrainsApiResponse = api.getTrains()

    override suspend fun fetchTrains(lineIds: Set<String>): TrainsApiResponse =
        api.getTrains(normalizeLinesQuery(lineIds))

    override suspend fun fetchServiceStatus(): ServiceStatusApiResponse =
        api.getServiceStatus()

    override suspend fun fetchServiceStatus(lineIds: Set<String>): ServiceStatusApiResponse =
        api.getServiceStatus(normalizeLinesQuery(lineIds))

    override suspend fun fetchRailways(): RailwaysApiResponse = api.getRailways()
}
