package com.shunsoco.trainlivemap.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunsoco.trainlivemap.domain.train.TrainStatusFilter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainStatusFilterBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingStatusChipChangesSelection() {
        val selected = mutableStateOf(TrainStatusFilter.ALL)
        val counts = mapOf(
            TrainStatusFilter.ALL to 5,
            TrainStatusFilter.RUNNING to 2,
            TrainStatusFilter.STOPPED to 1,
            TrainStatusFilter.DELAYED to 1,
            TrainStatusFilter.SUSPENDED to 0,
        )

        composeRule.setContent {
            MaterialTheme {
                TrainStatusFilterBar(
                    selected = selected.value,
                    counts = counts,
                    onSelected = { selected.value = it },
                )
            }
        }

        composeRule.onNodeWithText("すべて 5").assertIsSelected()
        composeRule.onNodeWithText("走行中 2").performClick()
        composeRule.onNodeWithText("走行中 2").assertIsSelected()
    }

    @Test
    fun zeroCountStatusIsDisabled() {
        composeRule.setContent {
            MaterialTheme {
                TrainStatusFilterBar(
                    selected = TrainStatusFilter.ALL,
                    counts = TrainStatusFilter.entries.associateWith { filter ->
                        if (filter == TrainStatusFilter.SUSPENDED) 0 else 2
                    },
                    onSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("運転見合わせ 0").assertIsNotEnabled()
    }
}
