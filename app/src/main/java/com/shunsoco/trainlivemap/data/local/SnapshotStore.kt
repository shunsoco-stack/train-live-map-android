package com.shunsoco.trainlivemap.data.local

/**
 * Raw successful response snapshots.
 *
 * Keeping JSON rather than a second set of cache entities makes the persisted
 * schema identical to the backend contract. It also lets the same configured
 * kotlinx.serialization [kotlinx.serialization.json.Json] instance decode
 * network and cached data.
 */
interface SnapshotStore {
    suspend fun readTrainsJson(): String?

    suspend fun writeTrainsJson(json: String)

    suspend fun readServiceStatusJson(): String?

    suspend fun writeServiceStatusJson(json: String)

    suspend fun readRailwaysJson(): String?

    suspend fun writeRailwaysJson(json: String)
}
