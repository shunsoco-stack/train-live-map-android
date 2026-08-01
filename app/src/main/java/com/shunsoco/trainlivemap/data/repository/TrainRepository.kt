package com.shunsoco.trainlivemap.data.repository

import com.shunsoco.trainlivemap.data.local.SnapshotStore
import com.shunsoco.trainlivemap.data.model.RailwaysApiResponse
import com.shunsoco.trainlivemap.data.model.ServiceStatusApiResponse
import com.shunsoco.trainlivemap.data.model.TrainsApiResponse
import com.shunsoco.trainlivemap.data.remote.ApiClientFactory
import com.shunsoco.trainlivemap.data.remote.ApiFailure
import com.shunsoco.trainlivemap.data.remote.RetrofitTrainRemoteDataSource
import com.shunsoco.trainlivemap.data.remote.TrainLiveMapApi
import com.shunsoco.trainlivemap.data.remote.TrainRemoteDataSource
import com.shunsoco.trainlivemap.data.remote.classifyApiFailure
import com.shunsoco.trainlivemap.data.remote.normalizeLinesQuery
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class LoadResult<T>(
    val data: T?,
    val fromCache: Boolean,
    val error: Throwable?,
    val apiFailure: ApiFailure? = error?.let(::classifyApiFailure),
) {
    val isSuccess: Boolean
        get() = error == null && data != null

    val isStale: Boolean
        get() = fromCache
}

interface TrainRepository {
    suspend fun refreshTrains(): LoadResult<TrainsApiResponse>

    suspend fun refreshTrains(lineIds: Set<String>): LoadResult<TrainsApiResponse> =
        refreshTrains()

    suspend fun refreshServiceStatus(): LoadResult<ServiceStatusApiResponse>

    suspend fun refreshServiceStatus(
        lineIds: Set<String>,
    ): LoadResult<ServiceStatusApiResponse> = refreshServiceStatus()

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
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : TrainRepository {
    constructor(
        api: TrainLiveMapApi,
        snapshotStore: SnapshotStore,
        json: Json = ApiClientFactory.json,
        clockMillis: () -> Long = System::currentTimeMillis,
    ) : this(
        remoteDataSource = RetrofitTrainRemoteDataSource(api),
        snapshotStore = snapshotStore,
        json = json,
        clockMillis = clockMillis,
    )

    override suspend fun refreshTrains(): LoadResult<TrainsApiResponse> = refresh(
        serializer = TrainsApiResponse.serializer(),
        fetch = { remoteDataSource.fetchTrains() },
        readCache = snapshotStore::readTrainsJson,
        writeCache = snapshotStore::writeTrainsJson,
        cacheScope = null,
        useScopedCache = true,
    )

    override suspend fun refreshTrains(
        lineIds: Set<String>,
    ): LoadResult<TrainsApiResponse> = refresh(
        serializer = TrainsApiResponse.serializer(),
        fetch = { remoteDataSource.fetchTrains(lineIds) },
        readCache = snapshotStore::readTrainsJson,
        writeCache = snapshotStore::writeTrainsJson,
        cacheScope = normalizeLinesQuery(lineIds),
        useScopedCache = true,
    )

    override suspend fun refreshServiceStatus(): LoadResult<ServiceStatusApiResponse> = refresh(
        serializer = ServiceStatusApiResponse.serializer(),
        fetch = { remoteDataSource.fetchServiceStatus() },
        readCache = snapshotStore::readServiceStatusJson,
        writeCache = snapshotStore::writeServiceStatusJson,
        cacheScope = null,
        useScopedCache = true,
    )

    override suspend fun refreshServiceStatus(
        lineIds: Set<String>,
    ): LoadResult<ServiceStatusApiResponse> = refresh(
        serializer = ServiceStatusApiResponse.serializer(),
        fetch = { remoteDataSource.fetchServiceStatus(lineIds) },
        readCache = snapshotStore::readServiceStatusJson,
        writeCache = snapshotStore::writeServiceStatusJson,
        cacheScope = normalizeLinesQuery(lineIds),
        useScopedCache = true,
    )

    override suspend fun refreshRailways(): LoadResult<RailwaysApiResponse> = refresh(
        serializer = RailwaysApiResponse.serializer(),
        fetch = remoteDataSource::fetchRailways,
        readCache = snapshotStore::readRailwaysJson,
        writeCache = snapshotStore::writeRailwaysJson,
        cacheScope = null,
        useScopedCache = false,
    )

    private suspend fun <T> refresh(
        serializer: KSerializer<T>,
        fetch: suspend () -> T,
        readCache: suspend () -> String?,
        writeCache: suspend (String) -> Unit,
        cacheScope: String?,
        useScopedCache: Boolean,
    ): LoadResult<T> {
        return try {
            val response = fetch()
            persistBestEffort(
                writeCache = writeCache,
                value = encodeCacheValue(
                    serializer = serializer,
                    response = response,
                    cacheScope = cacheScope,
                    useScopedCache = useScopedCache,
                ),
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
                expectedScope = cacheScope,
                useScopedCache = useScopedCache,
            )
            LoadResult(
                data = cached,
                fromCache = cached != null,
                error = networkError,
                apiFailure = classifyApiFailure(networkError, clockMillis()),
            )
        }
    }

    private fun <T> encodeCacheValue(
        serializer: KSerializer<T>,
        response: T,
        cacheScope: String?,
        useScopedCache: Boolean,
    ): String {
        val payload = json.encodeToString(serializer, response)
        return if (useScopedCache) {
            json.encodeToString(
                ScopedSnapshot.serializer(),
                ScopedSnapshot(
                    linesQuery = cacheScope,
                    payload = payload,
                ),
            )
        } else {
            payload
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
        expectedScope: String?,
        useScopedCache: Boolean,
    ): T? {
        return try {
            val cachedJson = readCache()
                ?.takeIf(String::isNotBlank)
                ?: return null
            if (!useScopedCache) {
                return json.decodeFromString(serializer, cachedJson)
            }

            val scoped = runCatching {
                json.decodeFromString(ScopedSnapshot.serializer(), cachedJson)
            }.getOrNull()
            if (scoped != null) {
                if (scoped.version != SCOPED_SNAPSHOT_VERSION) return null
                if (scoped.linesQuery != expectedScope) return null
                return json.decodeFromString(serializer, scoped.payload)
            }

            // Legacy snapshots predate query scoping and are safe only for the
            // compatibility request that omits `lines` entirely. In
            // particular, an explicitly empty selection has scope "" and must
            // never receive this all-lines cache.
            if (expectedScope != null) return null
            json.decodeFromString(serializer, cachedJson)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }
}

@Serializable
internal data class ScopedSnapshot(
    val version: Int = SCOPED_SNAPSHOT_VERSION,
    val linesQuery: String?,
    val payload: String,
)

private const val SCOPED_SNAPSHOT_VERSION = 1
