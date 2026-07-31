package com.shunsoco.trainlivemap.domain.service

import com.shunsoco.trainlivemap.data.model.DataAccuracy
import com.shunsoco.trainlivemap.data.model.ServiceSeverity
import com.shunsoco.trainlivemap.data.model.ServiceStatus
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus
import java.time.OffsetDateTime

private const val TRAIN_STATUS_FALLBACK_MAX_AGE_MILLIS = 2 * 60 * 1_000L
private const val TRAIN_STATUS_FUTURE_TOLERANCE_MILLIS = 30_000L
private const val MAJOR_MAX_DELAY_MINUTES = 30
private const val MAJOR_RATIO_DELAY_MINUTES = 15
private const val MAJOR_DELAYED_RATIO = 0.5

private val pendingResumePattern = Regex(
    "運転再開(?:の)?(?:見込み|見通し|予定)|" +
        "運転を再開する(?:見込み|見通し|予定)|" +
        "運転再開は[^。]{0,24}(?:見込|予定)|" +
        "再開(?:の)?(?:見込み|見通し|予定)|" +
        "再開していません|再開できていません|再開には時間を要",
)
private val completedResumePattern = Regex(
    "運転再開(?:しました|済み)|" +
        "運転を再開(?:しました|した|し[、，。])|" +
        "運転が再開(?:しました|されました)",
)
private val disruptionAfterResumePattern = Regex("遅れ|遅延|運休|中止")
private val activeSuspensionPattern = Regex("運転見合わせ|運転を見合|見合わせています|抑止")
private val minorDisruptionPattern = Regex("遅れ|遅延|運休|直通運転を中止|一部列車|運転変更")

/**
 * ODPT の文章を Web 版と同じ優先順で分類する。
 *
 * 「見合わせていたが運転再開」「再開したが再度見合わせ」のような文章では、
 * 最後に現れる状態変化を採用し、過去と現在を取り違えない。
 */
fun classifyServiceStatusSeverity(text: String): ServiceSeverity {
    val latestContext = buildList {
        pendingResumePattern.findAll(text).lastOrNull()?.let { match ->
            add(ServiceTextEvent(ServiceTextContext.PENDING_RESUME, match))
        }
        completedResumePattern.findAll(text).lastOrNull()?.let { match ->
            add(ServiceTextEvent(ServiceTextContext.COMPLETED_RESUME, match))
        }
        activeSuspensionPattern.findAll(text).lastOrNull()?.let { match ->
            add(ServiceTextEvent(ServiceTextContext.ACTIVE_SUSPENSION, match))
        }
    }.maxByOrNull { event -> event.match.range.first }

    return when (latestContext?.context) {
        ServiceTextContext.PENDING_RESUME,
        ServiceTextContext.ACTIVE_SUSPENSION,
        -> ServiceSeverity.MAJOR

        ServiceTextContext.COMPLETED_RESUME -> {
            val afterResume = text.substring(latestContext.match.range.last + 1)
            if (disruptionAfterResumePattern.containsMatchIn(afterResume)) {
                ServiceSeverity.MINOR
            } else {
                ServiceSeverity.NORMAL
            }
        }

        null -> if (minorDisruptionPattern.containsMatchIn(text)) {
            ServiceSeverity.MINOR
        } else {
            ServiceSeverity.NORMAL
        }
    }
}

private enum class ServiceTextContext {
    PENDING_RESUME,
    COMPLETED_RESUME,
    ACTIVE_SUSPENSION,
}

private data class ServiceTextEvent(
    val context: ServiceTextContext,
    val match: MatchResult,
)

/**
 * 公式運行情報が平常でも、同一路線の新しい列車位置に遅延が明示されていれば
 * 推定の運行情報へ補完する。公式の非平常情報は常にそのまま優先する。
 */
fun serviceStatusWithTrainDelayFallback(
    serviceStatus: ServiceStatus,
    trains: List<TrainLocation>,
    nowMillis: Long = System.currentTimeMillis(),
): ServiceStatus {
    // Treat the API status as one authoritative ODPT-derived unit. In
    // particular, never downgrade an explicit minor/major severity by trying
    // to reinterpret its prose on-device.
    if (serviceStatus.severity != ServiceSeverity.NORMAL) return serviceStatus

    val recentLineTrains = trains.filter { train ->
        if (train.lineId != serviceStatus.lineId) return@filter false
        val updatedAtMillis = train.lastUpdatedAt.toEpochMillisOrNull() ?: return@filter false
        updatedAtMillis <= nowMillis + TRAIN_STATUS_FUTURE_TOLERANCE_MILLIS &&
            nowMillis - updatedAtMillis <= TRAIN_STATUS_FALLBACK_MAX_AGE_MILLIS
    }
    if (recentLineTrains.isEmpty()) return serviceStatus

    val delayedTrains = recentLineTrains.filter { train ->
        train.status == TrainStatus.DELAYED || train.delayMinutes > 0
    }
    if (delayedTrains.isEmpty()) return serviceStatus

    val maxDelayMinutes = delayedTrains.maxOf { train ->
        train.delayMinutes.coerceAtLeast(0)
    }
    val majorDelayedRatio = recentLineTrains.count { train ->
        train.delayMinutes >= MAJOR_RATIO_DELAY_MINUTES
    }.toDouble() / recentLineTrains.size
    val isMajor = maxDelayMinutes >= MAJOR_MAX_DELAY_MINUTES ||
        majorDelayedRatio >= MAJOR_DELAYED_RATIO
    val latestDelayedTrain = delayedTrains.maxBy { train ->
        // All delayed trains passed the valid timestamp filter above.
        requireNotNull(train.lastUpdatedAt.toEpochMillisOrNull())
    }

    return serviceStatus.copy(
        severity = if (isMajor) ServiceSeverity.MAJOR else ServiceSeverity.MINOR,
        message = if (isMajor) {
            "列車位置情報では最大${maxDelayMinutes}分の大幅な遅れが確認されています。" +
                "公式の運行情報もあわせてご確認ください。"
        } else {
            "列車位置情報では最大${maxDelayMinutes}分程度の遅れが確認されています。"
        },
        updatedAt = latestDelayedTrain.lastUpdatedAt,
        dataAccuracy = DataAccuracy.ESTIMATED,
    )
}

private fun String.toEpochMillisOrNull(): Long? = runCatching {
    OffsetDateTime.parse(this).toInstant().toEpochMilli()
}.getOrNull()
