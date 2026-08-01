package com.shunsoco.trainlivemap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID

private val Context.communityReporterIdentityPreferences by preferencesDataStore(
    name = "community_reporter_identity",
)

private val communityReporterIdPattern = Regex("^[A-Za-z0-9_-]{12,100}$")

fun isValidCommunityReporterId(value: String): Boolean =
    communityReporterIdPattern.matches(value)

/** Device-local identifier used only in the anonymous voting header. */
fun interface VoterIdStore {
    suspend fun getOrCreateVoterId(): String
}

/**
 * A separate Preferences DataStore keeps the voting identifier outside
 * `train_live_map.preferences_pb`, the only preferences file included by the
 * app's backup and device-transfer rules.
 */
class CommunityReporterIdDataStore private constructor(
    private val dataStore: DataStore<Preferences>,
    private val idGenerator: () -> String,
) : VoterIdStore {
    override suspend fun getOrCreateVoterId(): String {
        var resolved: String? = null
        dataStore.edit { preferences ->
            val stored = preferences[REPORTER_ID_KEY]
                ?.takeIf(::isValidCommunityReporterId)
            resolved = stored ?: idGenerator().also { generated ->
                require(isValidCommunityReporterId(generated)) {
                    "Generated community reporter ID is invalid"
                }
                preferences[REPORTER_ID_KEY] = generated
            }
        }
        return checkNotNull(resolved)
    }

    companion object {
        fun create(context: Context): CommunityReporterIdDataStore =
            CommunityReporterIdDataStore(
                dataStore = context.applicationContext.communityReporterIdentityPreferences,
                idGenerator = { UUID.randomUUID().toString() },
            )

        fun from(
            dataStore: DataStore<Preferences>,
            idGenerator: () -> String = { UUID.randomUUID().toString() },
        ): CommunityReporterIdDataStore = CommunityReporterIdDataStore(
            dataStore = dataStore,
            idGenerator = idGenerator,
        )

        internal val REPORTER_ID_KEY = stringPreferencesKey("community_reporter_id")
    }
}
