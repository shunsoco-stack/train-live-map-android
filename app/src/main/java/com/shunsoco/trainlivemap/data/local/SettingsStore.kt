package com.shunsoco.trainlivemap.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class UserPreferences(
    val favoriteLineIds: Set<String> = emptySet(),
    val visibleLineIds: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    /**
     * Distinguishes a fresh install from the valid user choice "hide every
     * line". After railways load, callers can initialize visibility once with
     * the currently available API options.
     */
    val visibleLineIdsInitialized: Boolean = false,
)

interface SettingsStore {
    val preferences: Flow<UserPreferences>

    suspend fun setFavorite(
        lineId: String,
        favorite: Boolean,
    )

    suspend fun setVisible(
        lineId: String,
        visible: Boolean,
    )

    suspend fun setVisibleLines(lineIds: Set<String>)

    suspend fun initializeVisibleLines(lineIds: Set<String>) {
        if (!preferences.first().visibleLineIdsInitialized) {
            setVisibleLines(lineIds)
        }
    }

    suspend fun setFavoritesOnly(enabled: Boolean)
}
