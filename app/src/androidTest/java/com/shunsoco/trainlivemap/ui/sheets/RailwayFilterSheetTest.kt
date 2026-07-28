package com.shunsoco.trainlivemap.ui.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunsoco.trainlivemap.data.local.UserPreferences
import com.shunsoco.trainlivemap.data.model.RailwayCoverage
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.data.model.RailwayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RailwayFilterSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchShowsOnlyMatchingRailway() {
        val preferences = mutableStateOf(UserPreferences())
        showSheet(preferences = preferences)

        composeRule.onNodeWithTag("railway_search").performTextReplacement("山手")

        composeRule.onNodeWithText("山手線").assertIsDisplayed()
        composeRule.onNodeWithText("東海道線").assertDoesNotExist()
    }

    @Test
    fun routeCheckboxUpdatesVisibility() {
        val preferences = mutableStateOf(UserPreferences())
        var callback: Pair<String, Boolean>? = null
        showSheet(
            preferences = preferences,
            onVisibleChanged = { lineId, visible ->
                callback = lineId to visible
                preferences.value = preferences.value.copy(
                    visibleLineIds = if (visible) {
                        preferences.value.visibleLineIds + lineId
                    } else {
                        preferences.value.visibleLineIds - lineId
                    },
                    visibleLineIdsInitialized = true,
                )
            },
        )

        composeRule.onNodeWithTag("route_toggle_tokaido").assertIsOff().performClick()

        composeRule.onNodeWithTag("route_toggle_tokaido").assertIsOn()
        assertEquals("tokaido" to true, callback)
        assertTrue("tokaido" in preferences.value.visibleLineIds)
    }

    @Test
    fun favoriteButtonRegistersRailwayAndUpdatesItsAccessibleAction() {
        val preferences = mutableStateOf(UserPreferences())
        var callback: Pair<String, Boolean>? = null
        showSheet(
            preferences = preferences,
            onFavoriteChanged = { lineId, favorite ->
                callback = lineId to favorite
                preferences.value = preferences.value.copy(
                    favoriteLineIds = if (favorite) {
                        preferences.value.favoriteLineIds + lineId
                    } else {
                        preferences.value.favoriteLineIds - lineId
                    },
                )
            },
        )

        composeRule.onNodeWithTag("favorite_tokaido").performClick()

        composeRule
            .onNodeWithContentDescription("東海道線をお気に入りから解除")
            .assertIsDisplayed()
        assertEquals("tokaido" to true, callback)
        assertTrue("tokaido" in preferences.value.favoriteLineIds)
    }

    @Test
    fun favoritesOnlyChipFiltersRowsToRegisteredFavorites() {
        val preferences = mutableStateOf(
            UserPreferences(favoriteLineIds = setOf("tokaido")),
        )
        var callback: Boolean? = null
        showSheet(
            preferences = preferences,
            onFavoritesOnlyChanged = { enabled ->
                callback = enabled
                preferences.value = preferences.value.copy(favoritesOnly = enabled)
            },
        )
        composeRule.onNodeWithText("東海道線").assertIsDisplayed()
        composeRule.onNodeWithText("山手線").assertIsDisplayed()

        composeRule.onNodeWithTag("favorites_only").performClick()

        composeRule.onNodeWithTag("favorites_only").assertIsSelected()
        composeRule.onNodeWithText("東海道線").assertIsDisplayed()
        composeRule.onNodeWithText("山手線").assertDoesNotExist()
        assertEquals(true, callback)
    }

    private fun showSheet(
        preferences: MutableState<UserPreferences>,
        onVisibleChanged: (String, Boolean) -> Unit = { _, _ -> },
        onFavoriteChanged: (String, Boolean) -> Unit = { _, _ -> },
        onFavoritesOnlyChanged: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                RailwayFilterSheet(
                    options = sampleOptions,
                    preferences = preferences.value,
                    loading = false,
                    onDismiss = {},
                    onVisibleChanged = onVisibleChanged,
                    onFavoriteChanged = onFavoriteChanged,
                    onFavoritesOnlyChanged = onFavoritesOnlyChanged,
                    onVisibleLinesChanged = {},
                )
            }
        }
    }

    private companion object {
        val sampleOptions = listOf(
            RailwayFilterOption(
                id = "tokaido",
                name = "東海道線",
                category = "JR東日本",
                color = "#f68b1e",
                aliases = listOf("東海道", "Tokaido"),
                coverage = RailwayCoverage.REALTIME,
                coverageNote = null,
                kind = RailwayKind.LINE,
                available = true,
            ),
            RailwayFilterOption(
                id = "yamanote",
                name = "山手線",
                category = "JR東日本",
                color = "#9acd32",
                aliases = listOf("山手", "Yamanote"),
                coverage = RailwayCoverage.REALTIME,
                coverageNote = null,
                kind = RailwayKind.LINE,
                available = true,
            ),
        )
    }
}
