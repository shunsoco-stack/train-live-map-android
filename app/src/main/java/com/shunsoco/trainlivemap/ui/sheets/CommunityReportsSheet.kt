package com.shunsoco.trainlivemap.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.shunsoco.trainlivemap.data.model.CommunityReportStatus
import com.shunsoco.trainlivemap.data.model.CommunityReportSubmitRequest
import com.shunsoco.trainlivemap.data.model.CommunityReportSummary
import com.shunsoco.trainlivemap.data.model.CommunityReportsApiResponse
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.ui.theme.TokaidoOrange

private val DelayMinuteOptions = listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120)

/**
 * Web版「みんなの運行情報」と同じ匿名投票を扱うシート。
 *
 * [onSubmit] へ渡す値は路線、状態、遅延分だけで、列車番号や端末の位置情報は
 * このコンポーネントでは保持も送信もしない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityReportsSheet(
    options: List<RailwayFilterOption>,
    reports: CommunityReportsApiResponse?,
    loading: Boolean,
    submitting: Boolean,
    error: String?,
    message: String? = null,
    retryRemainingSeconds: Int?,
    onRefresh: () -> Unit,
    onSubmit: (CommunityReportSubmitRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    val availableOptions = remember(options) {
        options.filter(RailwayFilterOption::available)
    }
    var selectedLineId by rememberSaveable {
        mutableStateOf(availableOptions.firstOrNull()?.id.orEmpty())
    }
    var selectedStatusName by rememberSaveable {
        mutableStateOf(CommunityReportStatus.ON_TIME.name)
    }
    var selectedDelayMinutes by rememberSaveable { mutableIntStateOf(5) }
    var lineMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(availableOptions, selectedLineId) {
        if (availableOptions.none { it.id == selectedLineId }) {
            selectedLineId = availableOptions.firstOrNull()?.id.orEmpty()
        }
    }

    val selectedStatus = runCatching {
        CommunityReportStatus.valueOf(selectedStatusName)
    }.getOrDefault(CommunityReportStatus.ON_TIME)
    val selectedLine = availableOptions.firstOrNull { it.id == selectedLineId }
    val summary = reports?.summaries?.firstOrNull { it.lineId == selectedLineId }
    val retrySeconds = retryRemainingSeconds?.coerceAtLeast(0) ?: 0
    val votingEnabled = reports?.votingEnabled == true
    val selectionEnabled = votingEnabled && !submitting
    val canSubmit =
        selectedLine != null &&
            votingEnabled &&
            !submitting &&
            retrySeconds == 0 &&
            (selectedStatus != CommunityReportStatus.DELAYED || selectedDelayMinutes in 1..120)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
                .testTag("community_reports_sheet"),
        ) {
            CommunitySheetHeader(
                windowMinutes = reports?.windowMinutes ?: 30,
                loading = loading,
                onRefresh = onRefresh,
                onDismiss = onDismiss,
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = Color(0xFFFFB300).copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = "⚠ 利用者による参考情報で、公式の運行情報ではありません。" +
                        "安全に関わる判断は鉄道会社の案内を確認してください。",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = "投票する路線",
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 6.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { lineMenuExpanded = true },
                    enabled = availableOptions.isNotEmpty() && !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("community_line_selector")
                        .semantics {
                            contentDescription = selectedLine?.let {
                                "投票する路線、${it.name}、選択肢を開く"
                            } ?: "投票できる路線がありません"
                            if (availableOptions.isEmpty() || submitting) disabled()
                        },
                ) {
                    if (selectedLine != null) {
                        LineColorDot(selectedLine)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = selectedLine?.name ?: "利用できる路線がありません",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("▼")
                }
                DropdownMenu(
                    expanded = lineMenuExpanded,
                    onDismissRequest = { lineMenuExpanded = false },
                ) {
                    availableOptions.forEach { option ->
                        val isSelected = option.id == selectedLineId
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LineColorDot(option)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = option.name,
                                        fontWeight = if (isSelected) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    )
                                }
                            },
                            onClick = {
                                selectedLineId = option.id
                                lineMenuExpanded = false
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("community_line_${option.id}")
                                .semantics {
                                    selected = isSelected
                                    stateDescription = if (isSelected) "選択済み" else "未選択"
                                    contentDescription = "投票する路線、${option.name}"
                                },
                        )
                    }
                }
            }

            CommunitySummaryCard(
                line = selectedLine,
                summary = summary,
                loading = loading && reports == null,
            )

            if (!votingEnabled && reports != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics {
                            contentDescription = "共有投票は現在利用できません"
                            liveRegion = LiveRegionMode.Polite
                        },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = "共有投票の保存先を準備中です。閲覧はできますが、" +
                            "現在は新しい投票を受け付けていません。",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                text = "今の状況",
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 6.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChoice(
                    status = CommunityReportStatus.ON_TIME,
                    label = "平常",
                    symbol = "○",
                    selectedStatus = selectedStatus,
                    enabled = selectionEnabled,
                    onSelected = { selectedStatusName = it.name },
                    modifier = Modifier.weight(1f),
                )
                StatusChoice(
                    status = CommunityReportStatus.DELAYED,
                    label = "遅延",
                    symbol = "◷",
                    selectedStatus = selectedStatus,
                    enabled = selectionEnabled,
                    onSelected = { selectedStatusName = it.name },
                    modifier = Modifier.weight(1f),
                )
                StatusChoice(
                    status = CommunityReportStatus.SUSPENDED,
                    label = "見合わせ",
                    symbol = "!",
                    selectedStatus = selectedStatus,
                    enabled = selectionEnabled,
                    onSelected = { selectedStatusName = it.name },
                    modifier = Modifier.weight(1f),
                )
            }

            if (selectedStatus == CommunityReportStatus.DELAYED) {
                Text(
                    text = "何分くらい遅れていますか？",
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 4.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DelayMinuteOptions.forEach { minutes ->
                        val selected = minutes == selectedDelayMinutes
                        FilterChip(
                            selected = selected,
                            onClick = { selectedDelayMinutes = minutes },
                            enabled = selectionEnabled,
                            label = { Text("${minutes}分") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("community_delay_$minutes")
                                .semantics {
                                    this.selected = selected
                                    stateDescription = if (selected) "選択済み" else "未選択"
                                    contentDescription = "遅延時間、${minutes}分"
                                    if (!selectionEnabled) disabled()
                                },
                        )
                    }
                }
                Text(
                    text = "1〜120分の範囲で近い時間を選んでください。",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!message.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = message
                        },
                    color = Color(0xFF14532D),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Assertive
                            contentDescription = "エラー、$error"
                        },
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (retrySeconds > 0) {
                Text(
                    text = "同じ路線には${retrySeconds}秒後に再投票できます。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "再投票まであと${retrySeconds}秒"
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    val lineId = selectedLine?.id ?: return@Button
                    onSubmit(
                        CommunityReportSubmitRequest(
                            lineId = lineId,
                            status = selectedStatus,
                            delayMinutes = selectedDelayMinutes
                                .takeIf { selectedStatus == CommunityReportStatus.DELAYED },
                        ),
                    )
                },
                enabled = canSubmit,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("community_submit")
                    .semantics {
                        contentDescription = submitContentDescription(
                            lineName = selectedLine?.name,
                            status = selectedStatus,
                            delayMinutes = selectedDelayMinutes,
                            submitting = submitting,
                            retrySeconds = retrySeconds,
                            votingEnabled = votingEnabled,
                        )
                        if (!canSubmit) disabled()
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7DD3FC),
                    contentColor = Color(0xFF082F49),
                ),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        submitting -> "投票中…"
                        retrySeconds > 0 -> "${retrySeconds}秒後に再投票"
                        else -> "この内容で投票"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = "同じ路線は${reports?.cooldownSeconds ?: 60}秒後に更新できます。" +
                    "投稿は${reports?.windowMinutes ?: 30}分で集計から外れます。" +
                    if (reports?.persistent == false && votingEnabled) {
                        " 現在は開発用の一時保存です。"
                    } else {
                        ""
                    },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CommunitySheetHeader(
    windowMinutes: Int,
    loading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "直近${windowMinutes}分の利用者投稿",
                color = Color(0xFF38BDF8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "みんなの運行情報",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(
            onClick = onRefresh,
            enabled = !loading,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("community_refresh")
                .semantics {
                    contentDescription = if (loading) {
                        "みんなの運行情報を更新中"
                    } else {
                        "みんなの運行情報を更新"
                    }
                    if (loading) disabled()
                },
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("更新")
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "みんなの運行情報を閉じる" },
        ) {
            Text("閉じる")
        }
    }
}

@Composable
private fun CommunitySummaryCard(
    line: RailwayFilterOption?,
    summary: CommunityReportSummary?,
    loading: Boolean,
) {
    val statusText = when {
        loading -> "集計を確認中…"
        summary == null -> "まだ投稿はありません"
        summary.status == CommunityReportStatus.SUSPENDED -> "運転見合わせの報告"
        summary.status == CommunityReportStatus.DELAYED ->
            "約${summary.delayMinutes ?: "?"}分遅れ"
        else -> "平常の報告"
    }
    val summaryDescription = buildString {
        append(line?.name ?: "路線未選択")
        append("、")
        append(statusText)
        if (summary != null) {
            append("、${summary.voteCount}票")
            append("、平常${summary.counts.onTime}件")
            append("、遅延${summary.counts.delayed}件")
            append("、見合わせ${summary.counts.suspended}件")
        }
    }
    val containerColor = when (summary?.status) {
        CommunityReportStatus.SUSPENDED -> Color(0xFF7F1D1D).copy(alpha = 0.32f)
        CommunityReportStatus.DELAYED -> Color(0xFF78350F).copy(alpha = 0.30f)
        CommunityReportStatus.ON_TIME -> Color(0xFF065F46).copy(alpha = 0.28f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = summaryDescription
            },
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (line != null) {
                    LineColorDot(line)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = line?.name ?: "路線を選んでください",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    Text(
                        text = "${summary.voteCount}票",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = statusText,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (summary != null) {
                Text(
                    text = "平常 ${summary.counts.onTime}・遅延 ${summary.counts.delayed}・" +
                        "見合わせ ${summary.counts.suspended}",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusChoice(
    status: CommunityReportStatus,
    label: String,
    symbol: String,
    selectedStatus: CommunityReportStatus,
    enabled: Boolean,
    onSelected: (CommunityReportStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = status == selectedStatus
    FilterChip(
        selected = isSelected,
        onClick = { onSelected(status) },
        enabled = enabled,
        label = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(symbol, fontWeight = FontWeight.Black)
                Text(label)
            }
        },
        modifier = modifier
            .heightIn(min = 56.dp)
            .testTag("community_status_${status.name.lowercase()}")
            .semantics {
                selected = isSelected
                stateDescription = if (isSelected) "選択済み" else "未選択"
                contentDescription = "今の状況、$label"
                if (!enabled) disabled()
            },
    )
}

@Composable
private fun LineColorDot(option: RailwayFilterOption) {
    val color = remember(option.color) {
        runCatching { Color(option.color.toColorInt()) }.getOrDefault(TokaidoOrange)
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape),
    )
}

private fun submitContentDescription(
    lineName: String?,
    status: CommunityReportStatus,
    delayMinutes: Int,
    submitting: Boolean,
    retrySeconds: Int,
    votingEnabled: Boolean,
): String = when {
    submitting -> "投票を送信中"
    retrySeconds > 0 -> "再投票まであと${retrySeconds}秒、送信できません"
    !votingEnabled -> "共有投票は現在利用できません"
    lineName == null -> "投票できる路線がありません"
    status == CommunityReportStatus.DELAYED ->
        "$lineName、遅延${delayMinutes}分を投票"
    status == CommunityReportStatus.SUSPENDED -> "$lineName、運転見合わせを投票"
    else -> "$lineName、平常を投票"
}
