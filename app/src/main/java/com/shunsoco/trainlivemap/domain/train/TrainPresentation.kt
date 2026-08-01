package com.shunsoco.trainlivemap.domain.train

import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import com.shunsoco.trainlivemap.data.model.TrainType

/**
 * The three faces supported by the reusable train marker.
 *
 * This is deliberately independent of color so that status is never conveyed
 * by color alone.
 */
enum class TrainFace {
    NORMAL,
    DELAYED,
    SUSPENDED,
}

fun resolveTrainFace(
    status: TrainStatus,
    delayMinutes: Int,
): TrainFace = when {
    status == TrainStatus.SUSPENDED -> TrainFace.SUSPENDED
    status == TrainStatus.DELAYED || delayMinutes > 0 -> TrainFace.DELAYED
    else -> TrainFace.NORMAL
}

fun TrainLocation.resolveFace(): TrainFace = resolveTrainFace(
    status = status,
    delayMinutes = delayMinutes,
)

/** Visible direction label used below the marker. */
fun directionLabelJa(direction: TrainDirection): String = when (direction) {
    TrainDirection.INBOUND -> "↑ 上り"
    TrainDirection.OUTBOUND -> "↓ 下り"
}

fun statusLabelJa(status: TrainStatus): String = when (status) {
    TrainStatus.RUNNING -> "走行中"
    TrainStatus.STOPPED -> "停車中"
    TrainStatus.DELAYED -> "遅延"
    TrainStatus.SUSPENDED -> "運転見合わせ"
    TrainStatus.UNKNOWN -> "不明"
}

fun trainTypeLabelJa(trainType: TrainType): String = when (trainType) {
    TrainType.LOCAL -> "普通"
    TrainType.RAPID -> "快速"
    TrainType.SPECIAL_RAPID -> "特別快速"
}

fun dataAccuracyLabelJa(dataAccuracy: DataAccuracy): String = when (dataAccuracy) {
    DataAccuracy.ACTUAL -> "取得情報（位置は推定）"
    DataAccuracy.ESTIMATED -> "位置推定"
    DataAccuracy.MOCK -> "モック"
}

/**
 * A concise TalkBack description for a train marker.
 *
 * Train numbers are intentionally excluded from both the visual marker and
 * accessibility text. Direction, status and delay are stated as words rather
 * than relying on color or the arrow glyph.
 */
fun trainContentDescription(train: TrainLocation): String {
    val destination = train.destination
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let { "${it}行き" }
        ?: "行き先不明"
    val delay = if (train.delayMinutes > 0) {
        "${train.delayMinutes}分遅れ"
    } else {
        "遅延なし"
    }

    return listOf(
        train.lineName,
        directionLabelJa(train.direction),
        destination,
        trainTypeLabelJa(train.trainType),
        statusLabelJa(train.status),
        delay,
    ).joinToString(separator = "、")
}
