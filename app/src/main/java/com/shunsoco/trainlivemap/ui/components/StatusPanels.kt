package com.shunsoco.trainlivemap.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shunsoco.trainlivemap.ads.PrivacyOptionsRequirement
import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.ProviderSource
import com.shunsoco.trainlivemap.data.model.RailwaySource
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.domain.train.TrainStatusFilter
import com.shunsoco.trainlivemap.ui.MainUiState
import com.shunsoco.trainlivemap.ui.sheets.formatTimestamp
import com.shunsoco.trainlivemap.ui.theme.DelayRed
import com.shunsoco.trainlivemap.ui.theme.RailBrown
import com.shunsoco.trainlivemap.ui.theme.RailCream
import com.shunsoco.trainlivemap.ui.theme.RailMuted
import com.shunsoco.trainlivemap.ui.theme.TokaidoOrange

@Composable
fun AppHeader(
    state: MainUiState,
    visibleTrainCount: Int,
    privacyRequirement: PrivacyOptionsRequirement,
    onRefresh: () -> Unit,
    onPrivacyOptions: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RailBrown.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Train Live Map",
                        color = RailCream,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "関東のJR列車 ${visibleTrainCount}本を表示",
                        color = RailMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (privacyRequirement == PrivacyOptionsRequirement.REQUIRED) {
                    TextButton(
                        onClick = onPrivacyOptions,
                        colors = ButtonDefaults.textButtonColors(contentColor = RailCream),
                    ) {
                        Text("プライバシー")
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "データを再取得" },
                ) {
                    Text(
                        text = if (
                            state.trainsLoading ||
                            state.serviceLoading ||
                            state.railwaysLoading
                        ) {
                            "…"
                        } else {
                            "↻"
                        },
                        color = TokaidoOrange,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sourceLabel(state),
                    color = if (state.isMock || state.fallback) {
                        Color(0xFFFFD166)
                    } else {
                        Color(0xFF9BE8B0)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "最終更新 ${formatTimestamp(state.dataUpdatedAt)}",
                    color = RailMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.nextRefreshSeconds?.let { seconds ->
                    Text(
                        text = "次回 ${seconds}秒",
                        color = RailMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = "列車位置は駅間情報などからの位置推定を含みます",
                color = Color(0xFFFFD5B5),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
fun DataHealthNotice(
    state: MainUiState,
    onRetry: () -> Unit,
) {
    val labels = buildList {
        if (state.isOffline) add("オフライン")
        if (state.isStale) add("データが古い")
        if (state.isMock) add("モックデータ")
        if (state.fallback) add("フォールバック")
        if (state.serviceIsMock) add("運行情報モック")
        if (state.serviceFallback) add("運行情報フォールバック")
        if (state.railwaySource == RailwaySource.FALLBACK) add("路線情報フォールバック")
    }
    if (
        labels.isEmpty() &&
        state.notice.isNullOrBlank() &&
        state.serviceNotice.isNullOrBlank() &&
        state.trainError == null &&
        state.serviceError == null &&
        state.railwaysError == null
    ) {
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (state.isOffline || state.fallback) {
            Color(0xFF7F1D1D).copy(alpha = 0.95f)
        } else {
            Color(0xFF6B3D00).copy(alpha = 0.95f)
        },
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (labels.isNotEmpty()) {
                    Text(
                        text = labels.joinToString("・"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = state.notice
                        ?: state.serviceNotice
                        ?: state.trainError
                        ?: state.serviceError
                        ?: state.railwaysError
                        ?: "最後に成功したデータを表示しています",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.dataUpdatedAt != null) {
                    Text(
                        text = "表示データ更新: ${formatTimestamp(state.dataUpdatedAt)}",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text("再試行", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ServiceStatusPanel(
    serviceStatus: ServiceStatus?,
    fromCache: Boolean,
    isMock: Boolean,
    fallback: Boolean,
) {
    serviceStatus ?: return
    val (background, symbol) = when (serviceStatus.severity) {
        ServiceSeverity.NORMAL -> Color(0xFF14532D) to "●"
        ServiceSeverity.MINOR -> Color(0xFF854D0E) to "!"
        // Major can mean a large inferred delay; do not imply suspension with a cross.
        ServiceSeverity.MAJOR -> Color(0xFF7F1D1D) to "!"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = background.copy(alpha = 0.95f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = symbol,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${serviceStatus.lineName}　${serviceStatus.message}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "運行情報 ${formatTimestamp(serviceStatus.updatedAt)}" +
                        buildString {
                            if (fromCache) append("・保存データ")
                            if (isMock) append("・モック")
                            if (fallback) append("・フォールバック")
                            if (serviceStatus.dataAccuracy == DataAccuracy.ESTIMATED) {
                                append("・列車位置から推定・公式情報も確認")
                            }
                        },
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun TrainStatusFilterBar(
    selected: TrainStatusFilter,
    counts: Map<TrainStatusFilter, Int>,
    onSelected: (TrainStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TrainStatusFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = {
                    Text("${filter.labelJa} ${counts[filter] ?: 0}")
                },
            )
        }
    }
}

@Composable
fun RailwayFloatingButton(
    selectedCount: Int,
    favoritesOnly: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier
            .semantics {
                contentDescription = buildString {
                    append("表示路線を選ぶ。")
                    append(selectedCount)
                    append("路線を表示中")
                    if (favoritesOnly) append("。お気に入りのみ")
                }
            },
        label = {
            Text(
                text = "路線 $selectedCount" + if (favoritesOnly) " ★" else "",
                fontWeight = FontWeight.Bold,
            )
        },
        leadingIcon = { Text("≡", fontWeight = FontWeight.Black) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = RailBrown.copy(alpha = 0.96f),
            labelColor = RailCream,
            leadingIconContentColor = TokaidoOrange,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = TokaidoOrange,
        ),
        shape = RoundedCornerShape(24.dp),
    )
}

private fun sourceLabel(state: MainUiState): String = when {
    state.isMock -> "取得元: モック"
    state.trainSource == ProviderSource.ODPT -> "取得元: ODPT API"
    else -> "取得元: 確認中"
}
