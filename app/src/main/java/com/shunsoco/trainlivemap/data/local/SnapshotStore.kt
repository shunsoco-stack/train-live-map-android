package com.shunsoco.trainlivemap.data.local

/**
 * Serialized successful response snapshots.
 *
 * Railway JSON remains the backend payload. Train and service-status JSON is
 * wrapped by the repository with its normalized `lines` query so a snapshot
 * can never be returned for a different selection. The same configured
 * kotlinx.serialization [kotlinx.serialization.json.Json] instance decodes
 * both the envelope and payload.
 */
interface SnapshotStore {
    suspend fun readTrainsJson(): String?

    suspend fun writeTrainsJson(json: String)

    suspend fun readServiceStatusJson(): String?

    suspend fun writeServiceStatusJson(json: String)

    suspend fun readRailwaysJson(): String?

    suspend fun writeRailwaysJson(json: String)
}
