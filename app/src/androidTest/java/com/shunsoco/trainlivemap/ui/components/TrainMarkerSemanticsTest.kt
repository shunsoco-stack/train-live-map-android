package com.shunsoco.trainlivemap.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunsoco.trainlivemap.testTrain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainMarkerSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesAccuracyAndSelectedState() {
        val train = testTrain()
        composeRule.setContent {
            MaterialTheme {
                TrainMarker(
                    train = train,
                    selected = true,
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(
                "山手線、↑ 上り、東京行き、普通、走行中、遅延なし、位置推定",
            )
            .assertIsSelected()
    }

    @Test
    fun unselectedMarkerExposesUnselectedState() {
        val train = testTrain()
        composeRule.setContent {
            MaterialTheme {
                TrainMarker(
                    train = train,
                    selected = false,
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(
                "山手線、↑ 上り、東京行き、普通、走行中、遅延なし、位置推定",
            )
            .assertIsNotSelected()
    }
}
