package com.shunsoco.trainlivemap.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunsoco.trainlivemap.testTrain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainSearchOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun prefixResultsAreLimitedToFiveAndSelectionCallsBack() {
        var selectedId: String? = null
        val trains = (0..5).map { index ->
            testTrain(id = "train-$index", trainNumber = "10${index}0G")
        }
        composeRule.setContent {
            MaterialTheme {
                TrainSearchOverlay(
                    trains = trains,
                    selectedTrainId = null,
                    onTrainSelected = { selectedId = it },
                )
            }
        }

        composeRule.onNodeWithTag(TRAIN_SEARCH_FIELD_TAG).performTextInput("10")

        composeRule.onNodeWithText("1000G").assertIsDisplayed()
        composeRule.onNodeWithText("1040G").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("1050G").assertDoesNotExist()
        assertEquals("train-4", selectedId)
    }

    @Test
    fun selectedResultExposesSelectionToTalkBack() {
        val train = testTrain()
        composeRule.setContent {
            MaterialTheme {
                TrainSearchOverlay(
                    trains = listOf(train),
                    selectedTrainId = train.id,
                    onTrainSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag(TRAIN_SEARCH_FIELD_TAG).performTextInput("100")

        composeRule
            .onNodeWithContentDescription("列車番号 1000G、山手線、東京行き")
            .assertIsSelected()
    }

    @Test
    fun imeSearchReportsNoMatchAndClearHasAccessibleName() {
        composeRule.setContent {
            MaterialTheme {
                TrainSearchOverlay(
                    trains = listOf(testTrain()),
                    selectedTrainId = null,
                    onTrainSelected = {},
                )
            }
        }

        val searchField = composeRule.onNodeWithTag(TRAIN_SEARCH_FIELD_TAG)
        searchField.performTextInput("999")
        searchField.performImeAction()

        composeRule.onNodeWithText("一致する列車が見つかりません").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("列車番号の検索をクリア")
            .assertIsDisplayed()
    }
}
