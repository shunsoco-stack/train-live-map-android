package com.shunsoco.trainlivemap.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shunsoco.trainlivemap.ui.theme.DelayRed
import com.shunsoco.trainlivemap.ui.theme.SuspendedBlue
import com.shunsoco.trainlivemap.ui.theme.TokaidoOrange

/** Explains map symbols without relying on color alone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendSheet(
    onDismiss: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "地図の見かた",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        contentDescription = "地図の凡例を閉じる"
                    },
                ) {
                    Text("閉じる")
                }
            }

            LegendHeading("列車の状態")
            LegendStatusRow("☺", "走行中", "笑顔の列車", TokaidoOrange)
            LegendStatusRow("‖", "停車中（注意）", "短時間停車している列車", Color(0xFFEAB308))
            LegendStatusRow("■", "停車中（長時間）", "長く停車している列車", Color(0xFF9A3412))
            LegendStatusRow("+N分", "遅延", "困り顔と遅延分数の吹き出し", DelayRed)
            LegendStatusRow("涙", "運転見合わせ", "悲しい顔、青い涙、見合わせバッジ", SuspendedBlue)
            LegendStatusRow("?", "不明", "現在の状態を確認できない列車", Color(0xFF6B7280))

            LegendHeading("進行方向")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DirectionLegend("↑ 上り", Color(0xFF1E3A8A), Modifier.weight(1f))
                DirectionLegend("↓ 下り", Color(0xFF7C2D12), Modifier.weight(1f))
            }
            Text(
                text = "環状路線などでは、APIの案内に応じて内回り／外回り、北行／南行として読み替えます。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            LegendHeading("アイコンの位置")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "ODPTの駅間情報をもとにした推定位置です。GPSで測った正確な現在地ではなく、ゆっくりした動きも推定アニメーションです。",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "現在地ボタンで取得した位置は、地図を移動するためだけに端末内で使い、バックエンドへ送信しません。",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider(modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("プライバシーポリシー")
                }
                TextButton(
                    onClick = onOpenTerms,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("利用規約・免責")
                }
            }
        }
    }
}

@Composable
private fun LegendHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(top = 18.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun LegendStatusRow(
    symbol: String,
    label: String,
    description: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            color = color,
            shape = CircleShape,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = symbol,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DirectionLegend(
    label: String,
    color: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = CircleShape,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
