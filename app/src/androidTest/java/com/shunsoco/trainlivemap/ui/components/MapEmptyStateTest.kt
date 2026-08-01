package com.shunsoco.trainlivemap.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapEmptyStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noFilterResultsShowsOnlyItsAction() {
        var resetCount = 0
        composeRule.setContent {
            MaterialTheme {
                MapEmptyState(
                    kind = MapEmptyStateKind.NO_FILTER_RESULTS,
                    onChooseLines = {},
                    onResetFilter = { resetCount += 1 },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("該当する列車がありません").assertIsDisplayed()
        composeRule.onNodeWithText("路線を選ぶ").assertDoesNotExist()
        composeRule.onNodeWithText("再読み込み").assertDoesNotExist()
        composeRule.onNodeWithText("すべてに戻す").performClick()
        assertEquals(1, resetCount)
    }
}
