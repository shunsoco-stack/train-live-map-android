package com.shunsoco.trainlivemap.domain.train

import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.data.model.TrainStatus

enum class TrainStatusFilter(
    val labelJa: String,
) {
    ALL("すべて"),
    RUNNING("走行中"),
    STOPPED("停車中"),
    DELAYED("遅延"),
    SUSPENDED("運転見合わせ"),
}

/**
 * Matches a train against a user-selected status chip.
 *
 * Delayed trains can still be running, matching the API's definition of
 * `delayed` (a running train with a delay). Therefore RUNNING and DELAYED may
 * overlap. SUSPENDED always takes precedence over a positive delay value.
 */
fun matchesStatusFilter(
    train: TrainLocation,
    filter: TrainStatusFilter,
): Boolean = matchesStatusFilter(
    status = train.status,
    delayMinutes = train.delayMinutes,
    filter = filter,
)

fun matchesStatusFilter(
    status: TrainStatus,
    delayMinutes: Int,
    filter: TrainStatusFilter,
): Boolean = when (filter) {
    TrainStatusFilter.ALL -> true
    TrainStatusFilter.RUNNING ->
        status == TrainStatus.RUNNING || status == TrainStatus.DELAYED
    TrainStatusFilter.STOPPED -> status == TrainStatus.STOPPED
    TrainStatusFilter.DELAYED ->
        status != TrainStatus.SUSPENDED &&
            (status == TrainStatus.DELAYED || delayMinutes > 0)
    TrainStatusFilter.SUSPENDED -> status == TrainStatus.SUSPENDED
}

fun filterTrainsByStatus(
    trains: List<TrainLocation>,
    filter: TrainStatusFilter,
): List<TrainLocation> = trains.filter { matchesStatusFilter(it, filter) }
