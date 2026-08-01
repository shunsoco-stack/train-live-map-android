package com.shunsoco.trainlivemap.ui.sheets

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shunsoco.trainlivemap.data.model.CommunityReportCounts
import com.shunsoco.trainlivemap.data.model.CommunityReportStatus
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.data.model.CommunityReportSummary
import com.shunsoco.trainlivemap.data.model.CommunityReportsApiResponse
import com.shunsoco.trainlivemap.data.model.RailwayCoverage
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.data.model.RailwayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityReportsSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun delayedVoteUsesSelectedValidMinutesAndNoExtraData() {
        var submitted: CommunityReportSubmitRequest? = null
        var submitCalls = 0
        showSheet(
            onSubmit = {
                submitCalls += 1
                submitted = it
            },
        )

        composeRule.onNodeWithTag("community_status_delayed").performClick()
        composeRule.onNodeWithTag("community_status_delayed").assertIsSelected()
        composeRule.onNodeWithTag("community_delay_120").performScrollTo().performClick()
        composeRule.onNodeWithTag("community_delay_120").assertIsSelected()
        composeRule.onNodeWithTag("community_submit").performScrollTo().performClick()

        assertEquals("tokaido", submitted?.lineId)
        assertEquals(CommunityReportStatus.DELAYED, submitted?.status)
        assertEquals(120, submitted?.delayMinutes)
        assertEquals(1, submitCalls)
    }

    @Test
    fun onTimeVoteAlwaysSendsNullDelay() {
        var submitted: CommunityReportSubmitRequest? = null
        showSheet(onSubmit = { submitted = it })

        composeRule.onNodeWithTag("community_submit").performClick()

        assertEquals(CommunityReportStatus.ON_TIME, submitted?.status)
        assertNull(submitted?.delayMinutes)
    }

    @Test
    fun cooldownShowsRemainingTimeAndDisablesSubmit() {
        showSheet(retryRemainingSeconds = 42)

        composeRule
            .onNodeWithText("同じ路線には42秒後に再投票できます。")
            .assertExists()
        composeRule.onNodeWithTag("community_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun votingDisabledDisablesChoicesAndSubmitWhileKeepingSummaryVisible() {
        showSheet(reports = sampleReports.copy(votingEnabled = false))

        composeRule.onNodeWithText("約15分遅れ").assertIsDisplayed()
        composeRule.onNodeWithTag("community_status_on_time").assertIsNotEnabled()
        composeRule.onNodeWithTag("community_submit").assertIsNotEnabled()
        composeRule.onNodeWithText(
            "共有投票の保存先を準備中です。閲覧はできますが、現在は新しい投票を受け付けていません。",
        ).assertIsDisplayed()
    }

    private fun showSheet(
        reports: CommunityReportsApiResponse? = sampleReports,
        retryRemainingSeconds: Int? = null,
        onSubmit: (CommunityReportSubmitRequest) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                CommunityReportsSheet(
                    options = sampleOptions,
                    reports = reports,
                    loading = false,
                    submitting = false,
                    error = null,
                    retryRemainingSeconds = retryRemainingSeconds,
                    onRefresh = {},
                    onSubmit = onSubmit,
                    onDismiss = {},
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
                aliases = listOf("東海道"),
                coverage = RailwayCoverage.REALTIME,
                coverageNote = null,
                kind = RailwayKind.LINE,
                available = true,
            ),
        )
        val sampleReports = CommunityReportsApiResponse(
            summaries = listOf(
                CommunityReportSummary(
                    lineId = "tokaido",
                    status = CommunityReportStatus.DELAYED,
                    delayMinutes = 15,
                    voteCount = 4,
                    counts = CommunityReportCounts(
                        onTime = 1,
                        delayed = 3,
                        suspended = 0,
                    ),
                    updatedAt = "2026-08-01T12:00:00Z",
                ),
            ),
            windowMinutes = 30,
            cooldownSeconds = 60,
            persistent = true,
            votingEnabled = true,
        )
    }
}
