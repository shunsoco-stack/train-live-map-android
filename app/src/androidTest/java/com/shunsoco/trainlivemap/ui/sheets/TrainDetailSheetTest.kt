package com.shunsoco.trainlivemap.ui.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.testTrain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainDetailSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gpsEstimationNoticeIsAlwaysShownForActualCoordinates() {
        composeRule.setContent {
            MaterialTheme {
                TrainDetailSheet(
                    train = testTrain(dataAccuracy = DataAccuracy.ACTUAL),
                    nowMillis = 0L,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(ACCURACY_NOTICE)
            .assertExists()
    }

    private companion object {
        const val ACCURACY_NOTICE =
            "※ アイコンの位置と動きはGPS実測ではなく、駅間情報をもとにした推定です。実際の列車位置とは異なる場合があります。"
    }
}
