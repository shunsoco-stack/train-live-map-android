package com.shunsoco.trainlivemap.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceStatusPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun estimatedLargeDelayPromptsForOfficialInformationWithoutClaimingSuspension() {
        composeRule.setContent {
            MaterialTheme {
                ServiceStatusPanel(
                    serviceStatus = ServiceStatus(
                        lineId = "saikyo",
                        lineName = "埼京線",
                        severity = ServiceSeverity.MAJOR,
                        message = "列車位置情報では最大42分の大幅な遅れが確認されています。",
                        updatedAt = "2026-07-31T08:45:00Z",
                        dataAccuracy = DataAccuracy.ESTIMATED,
                    ),
                    fromCache = false,
                    isMock = false,
                    fallback = false,
                )
            }
        }

        composeRule.onNodeWithText("最大42分", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("列車位置から推定", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("公式情報も確認", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("運転見合わせ", substring = true).assertDoesNotExist()
    }

    @Test
    fun estimatedMinorDelayAlsoPromptsForOfficialInformation() {
        composeRule.setContent {
            MaterialTheme {
                ServiceStatusPanel(
                    serviceStatus = ServiceStatus(
                        lineId = "saikyo",
                        lineName = "埼京線",
                        severity = ServiceSeverity.MINOR,
                        message = "列車位置情報では最大8分程度の遅れが確認されています。",
                        updatedAt = "2026-07-31T08:45:00Z",
                        dataAccuracy = DataAccuracy.ESTIMATED,
                    ),
                    fromCache = false,
                    isMock = false,
                    fallback = false,
                )
            }
        }

        composeRule.onNodeWithText("最大8分", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("公式情報も確認", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("運転見合わせ", substring = true).assertDoesNotExist()
    }
}
