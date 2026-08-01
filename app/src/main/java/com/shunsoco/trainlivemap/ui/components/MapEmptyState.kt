package com.shunsoco.trainlivemap.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class MapEmptyStateKind {
    NO_LINES,
    NO_TRAINS,
    NO_FILTER_RESULTS,
}

/** Returns one mutually exclusive empty-state reason in display priority order. */
fun resolveMapEmptyState(
    loading: Boolean,
    visibleLineCount: Int,
    trainsOnVisibleLinesCount: Int,
    filteredTrainCount: Int,
): MapEmptyStateKind? {
    require(visibleLineCount >= 0)
    require(trainsOnVisibleLinesCount >= 0)
    require(filteredTrainCount >= 0)
    if (loading) return null
    return when {
        visibleLineCount == 0 -> MapEmptyStateKind.NO_LINES
        trainsOnVisibleLinesCount == 0 -> MapEmptyStateKind.NO_TRAINS
        filteredTrainCount == 0 -> MapEmptyStateKind.NO_FILTER_RESULTS
        else -> null
    }
}

@Composable
fun MapEmptyState(
    kind: MapEmptyStateKind,
    onChooseLines: () -> Unit,
    onResetFilter: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    supportingMessage: String? = null,
) {
    val content = when (kind) {
        MapEmptyStateKind.NO_LINES -> EmptyStateContent(
            symbol = "≡",
            title = "表示する路線が選ばれていません",
            body = "路線を1つ以上選ぶと、列車位置を表示します。",
            action = "路線を選ぶ",
            onAction = onChooseLines,
        )

        MapEmptyStateKind.NO_TRAINS -> EmptyStateContent(
            symbol = "↻",
            title = "列車情報がありません",
            body = "現在、選択した路線の列車位置を取得できませんでした。",
            action = "再読み込み",
            onAction = onRetry,
        )

        MapEmptyStateKind.NO_FILTER_RESULTS -> EmptyStateContent(
            symbol = "▣",
            title = "該当する列車がありません",
            body = "別の運行状態を選ぶか、すべての列車に戻してください。",
            action = "すべてに戻す",
            onAction = onResetFilter,
        )
    }

    Surface(
        modifier = modifier
            .widthIn(max = 320.dp)
            .padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = content.symbol,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = content.title,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = content.body,
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!supportingMessage.isNullOrBlank()) {
                Text(
                    text = supportingMessage,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = content.onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(content.action)
            }
        }
    }
}

private data class EmptyStateContent(
    val symbol: String,
    val title: String,
    val body: String,
    val action: String,
    val onAction: () -> Unit,
)
