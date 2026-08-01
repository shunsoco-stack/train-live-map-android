package com.shunsoco.trainlivemap.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.domain.train.dataAccuracyLabelJa
import com.shunsoco.trainlivemap.domain.train.directionLabelJa
import com.shunsoco.trainlivemap.domain.train.statusLabelJa
import com.shunsoco.trainlivemap.domain.train.trainTypeLabelJa
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainDetailSheet(
    train: TrainLocation,
    nowMillis: Long,
    onDismiss: () -> Unit,
) {
    val lineColor = runCatching { Color(train.lineColor.toColorInt()) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = train.lineName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${directionLabelJa(train.direction)}・${train.destination}行き",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier.semantics {
                        contentDescription = "状態、${statusLabelJa(train.status)}"
                    },
                    color = lineColor,
                    shape = CircleShape,
                ) {
                    Text(
                        text = statusLabelJa(train.status),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (lineColor.luminance() >= 0.42f) {
                            Color(0xFF24130D)
                        } else {
                            Color.White
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            DetailRow("路線", train.lineName)
            DetailRow("方向", directionLabelJa(train.direction))
            DetailRow("行き先", train.destination)
            DetailRow("種別", trainTypeLabelJa(train.trainType))
            DetailRow("状態", statusLabelJa(train.status))
            DetailRow(
                "遅延",
                if (train.delayMinutes > 0) "${train.delayMinutes}分" else "遅延なし",
            )
            DetailRow("速度", "${train.speedKmh.toInt()} km/h（推定を含む）")
            DetailRow("最終更新", formatTimestamp(train.lastUpdatedAt))
            DetailRow("データ精度", dataAccuracyLabelJa(train.dataAccuracy))
            stoppedDuration(train, nowMillis)?.let { DetailRow("停車時間", it) }
            Text(
                text = "※ アイコンの位置と動きはGPS実測ではなく、駅間情報をもとにした推定です。実際の列車位置とは異なる場合があります。",
                modifier = Modifier.padding(top = 14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private fun stoppedDuration(
    train: TrainLocation,
    nowMillis: Long,
): String? {
    if (train.status != TrainStatus.STOPPED && train.status != TrainStatus.SUSPENDED) return null
    val stoppedAt = parseTimestamp(train.stoppedSince) ?: return null
    val seconds = Duration.between(stoppedAt, Instant.ofEpochMilli(nowMillis))
        .seconds
        .coerceAtLeast(0L)
    return when {
        seconds < 60 -> "${seconds}秒"
        seconds < 3_600 -> "${seconds / 60}分"
        else -> "${seconds / 3_600}時間${(seconds % 3_600) / 60}分"
    }
}

fun formatTimestamp(value: String?): String {
    val instant = parseTimestamp(value) ?: return "—"
    return DateTimeFormatter.ofPattern("M/d HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun parseTimestamp(value: String?): Instant? {
    value ?: return null
    return runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull()
}
