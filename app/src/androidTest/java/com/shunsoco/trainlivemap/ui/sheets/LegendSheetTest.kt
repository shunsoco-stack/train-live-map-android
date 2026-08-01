package com.shunsoco.trainlivemap.ui.sheets

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
class LegendSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun explainsStatusDirectionEstimationAndLegalLinks() {
        var privacyClicks = 0
        var termsClicks = 0
        composeRule.setContent {
            MaterialTheme {
                LegendSheet(
                    onDismiss = {},
                    onOpenPrivacyPolicy = { privacyClicks += 1 },
                    onOpenTerms = { termsClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("走行中").assertIsDisplayed()
        composeRule.onNodeWithText("↑ 上り").assertExists()
        composeRule.onNodeWithText("↓ 下り").assertExists()
        composeRule.onNodeWithText(
            "ODPTの駅間情報をもとにした推定位置です。GPSで測った正確な現在地ではなく、ゆっくりした動きも推定アニメーションです。",
        ).assertExists()
        composeRule.onNodeWithText(
            "現在地ボタンで取得した位置は、地図を移動するためだけに端末内で使い、バックエンドへ送信しません。",
        ).assertExists()

        composeRule.onNodeWithText("プライバシーポリシー").performClick()
        composeRule.onNodeWithText("利用規約・免責").performClick()
        assertEquals(1, privacyClicks)
        assertEquals(1, termsClicks)
    }
}
