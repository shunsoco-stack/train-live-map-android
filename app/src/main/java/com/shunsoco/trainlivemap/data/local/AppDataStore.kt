package com.shunsoco.trainlivemap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trainLiveMapPreferences by preferencesDataStore(
    name = "train_live_map",
)

/**
 * Single Preferences DataStore for lightweight user settings and the three
 * latest successful API snapshots.
 */
class AppDataStore private constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore, SnapshotStore {
    override val preferences: Flow<UserPreferences> = safePreferences.map { values ->
        UserPreferences(
            favoriteLineIds = values[Keys.FAVORITE_LINE_IDS].orEmpty(),
            visibleLineIds = values[Keys.VISIBLE_LINE_IDS].orEmpty(),
            favoritesOnly = values[Keys.FAVORITES_ONLY] ?: false,
            visibleLineIdsInitialized = values[Keys.VISIBLE_LINE_IDS_INITIALIZED] ?: false,
        )
    }

    override suspend fun setFavorite(
        lineId: String,
        favorite: Boolean,
    ) {
        require(lineId.isNotBlank()) { "lineId must not be blank" }
        dataStore.edit { values ->
            values[Keys.FAVORITE_LINE_IDS] = values[Keys.FAVORITE_LINE_IDS]
                .orEmpty()
                .updated(lineId, favorite)
        }
    }

    override suspend fun setVisible(
        lineId: String,
        visible: Boolean,
    ) {
        require(lineId.isNotBlank()) { "lineId must not be blank" }
        dataStore.edit { values ->
            values[Keys.VISIBLE_LINE_IDS] = values[Keys.VISIBLE_LINE_IDS]
                .orEmpty()
                .updated(lineId, visible)
            values[Keys.VISIBLE_LINE_IDS_INITIALIZED] = true
        }
    }

    override suspend fun setVisibleLines(lineIds: Set<String>) {
        dataStore.edit { values ->
            values[Keys.VISIBLE_LINE_IDS] = lineIds.withoutBlankIds()
            values[Keys.VISIBLE_LINE_IDS_INITIALIZED] = true
        }
    }

    override suspend fun initializeVisibleLines(lineIds: Set<String>) {
        dataStore.edit { values ->
            if (values[Keys.VISIBLE_LINE_IDS_INITIALIZED] != true) {
                values[Keys.VISIBLE_LINE_IDS] = lineIds.withoutBlankIds()
                values[Keys.VISIBLE_LINE_IDS_INITIALIZED] = true
            }
        }
    }

    override suspend fun setFavoritesOnly(enabled: Boolean) {
        dataStore.edit { values ->
            values[Keys.FAVORITES_ONLY] = enabled
        }
    }

    override suspend fun readTrainsJson(): String? =
        safePreferences.first()[Keys.TRAINS_JSON]

    override suspend fun writeTrainsJson(json: String) {
        writeSnapshot(Keys.TRAINS_JSON, json)
    }

    override suspend fun readServiceStatusJson(): String? =
        safePreferences.first()[Keys.SERVICE_STATUS_JSON]

    override suspend fun writeServiceStatusJson(json: String) {
        writeSnapshot(Keys.SERVICE_STATUS_JSON, json)
    }

    override suspend fun readRailwaysJson(): String? =
        safePreferences.first()[Keys.RAILWAYS_JSON]

    override suspend fun writeRailwaysJson(json: String) {
        writeSnapshot(Keys.RAILWAYS_JSON, json)
    }

    private val safePreferences: Flow<Preferences>
        get() = dataStore.data.catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

    private suspend fun writeSnapshot(
        key: Preferences.Key<String>,
        json: String,
    ) {
        require(json.isNotBlank()) { "Snapshot JSON must not be blank" }
        dataStore.edit { values ->
            values[key] = json
        }
    }

    companion object {
        fun create(context: Context): AppDataStore = AppDataStore(
            context.applicationContext.trainLiveMapPreferences,
        )

        /**
         * Public for deterministic local tests and alternate DataStore
         * provisioning; application code normally uses [create].
         */
        fun from(dataStore: DataStore<Preferences>): AppDataStore = AppDataStore(dataStore)
    }

    private object Keys {
        val FAVORITE_LINE_IDS = stringSetPreferencesKey("favorite_line_ids")
        val VISIBLE_LINE_IDS = stringSetPreferencesKey("visible_line_ids")
        val FAVORITES_ONLY = booleanPreferencesKey("favorites_only")
        val VISIBLE_LINE_IDS_INITIALIZED =
            booleanPreferencesKey("visible_line_ids_initialized")
        val TRAINS_JSON = stringPreferencesKey("snapshot_trains_json")
        val SERVICE_STATUS_JSON = stringPreferencesKey("snapshot_service_status_json")
        val RAILWAYS_JSON = stringPreferencesKey("snapshot_railways_json")
    }
}

private fun Set<String>.updated(
    value: String,
    included: Boolean,
): Set<String> = toMutableSet().apply {
    if (included) add(value) else remove(value)
}.toSet()

private fun Set<String>.withoutBlankIds(): Set<String> =
    asSequence()
        .filter(String::isNotBlank)
        .toSet()
