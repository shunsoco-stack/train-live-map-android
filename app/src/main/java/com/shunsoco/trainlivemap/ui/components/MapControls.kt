package com.shunsoco.trainlivemap.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CurrentLocationButton(
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = if (loading) {
                    "現在地を取得中"
                } else {
                    "現在地へ地図を移動"
                }
            },
    ) {
        Text(
            text = if (loading) "…" else "◎",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun LegendButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapControlButton(
        symbol = "?",
        description = "列車状態、進行方向、推定位置の凡例を開く",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun CommunityReportsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapControlButton(
        symbol = "声",
        description = "みんなの運行情報を開く",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MapControlButton(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
    }
}
