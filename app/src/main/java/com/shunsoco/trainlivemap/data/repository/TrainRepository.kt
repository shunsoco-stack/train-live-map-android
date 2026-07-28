package com.shunsoco.trainlivemap.data.repository

import com.shunsoco.trainlivemap.data.local.SnapshotStore
import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse
import com.shunsoco.trainlivemap.data.remote.ApiClientFactory
import com.shunsoco.trainlivemap.data.remote.RetrofitTrainRemoteDataSource
import com.shunsoco.trainlivemap.data.remote.TrainLiveMapApi
import com.shunsoco.trainlivemap.data.remote.TrainRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

data class LoadResult<T>(
    val data: T?,
    val fromCache: Boolean,
    val error: Throwable?,
) {
    val isSuccess: Boolean
        get() = error == null && data != null

    val isStale: Boolean
        get() = fromCache
}

interface TrainRepository {
    suspend fun refreshTrains(): LoadResult<TrainsApiResponse>

    suspend fun refreshServiceStatus(): LoadResult<ServiceStatusApiResponse>

    suspend fun refreshRailways(): LoadResult<RailwaysApiResponse>
}

/**
 * Network-first repository. Each successful endpoint response replaces only
 * its corresponding persisted snapshot. If fetching fails, the last
 * decodable snapshot is returned with the original network error so UI can
 * keep rendering data while clearly labelling it stale/offline.
 */
class DefaultTrainRepository(
    private val remoteDataSource: TrainRemoteDataSource,
    private val snapshotStore: SnapshotStore,
    private val json: Json = ApiClientFactory.json,
) : TrainRepository {
    constructor(
        api: TrainLiveMapApi,
        snapshotStore: SnapshotStore,
        json: Json = ApiClientFactory.json,
    ) : this(
        remoteDataSource = RetrofitTrainRemoteDataSource(api),
        snapshotStore = snapshotStore,
        json = json,
    )

    override suspend fun refreshTrains(): LoadResult<TrainsApiResponse> = refresh(
        serializer = TrainsApiResponse.serializer(),
        fetch = remoteDataSource::fetchTrains,
        readCache = snapshotStore::readTrainsJson,
        writeCache = snapshotStore::writeTrainsJson,
    )

    override suspend fun refreshServiceStatus(): LoadResult<ServiceStatusApiResponse> = refresh(
        serializer = ServiceStatusApiResponse.serializer(),
        fetch = remoteDataSource::fetchServiceStatus,
        readCache = snapshotStore::readServiceStatusJson,
        writeCache = snapshotStore::writeServiceStatusJson,
    )

    override suspend fun refreshRailways(): LoadResult<RailwaysApiResponse> = refresh(
        serializer = RailwaysApiResponse.serializer(),
        fetch = remoteDataSource::fetchRailways,
        readCache = snapshotStore::readRailwaysJson,
        writeCache = snapshotStore::writeRailwaysJson,
    )

    private suspend fun <T> refresh(
        serializer: KSerializer<T>,
        fetch: suspend () -> T,
        readCache: suspend () -> String?,
        writeCache: suspend (String) -> Unit,
    ): LoadResult<T> {
        return try {
            val response = fetch()
            persistBestEffort(
                writeCache = writeCache,
                value = json.encodeToString(serializer, response),
            )
            LoadResult(
                data = response,
                fromCache = false,
                error = null,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (networkError: Throwable) {
            val cached = decodeCacheBestEffort(
                serializer = serializer,
                readCache = readCache,
            )
            LoadResult(
                data = cached,
                fromCache = cached != null,
                error = networkError,
            )
        }
    }

    private suspend fun persistBestEffort(
        writeCache: suspend (String) -> Unit,
        value: String,
    ) {
        try {
            writeCache(value)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // A live response remains usable even when local persistence fails.
        }
    }

    private suspend fun <T> decodeCacheBestEffort(
        serializer: KSerializer<T>,
        readCache: suspend () -> String?,
    ): T? {
        return try {
            val cachedJson = readCache()
                ?.takeIf(String::isNotBlank)
                ?: return null
            json.decodeFromString(serializer, cachedJson)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }
}
