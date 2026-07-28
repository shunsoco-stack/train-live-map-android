package com.shunsoco.trainlivemap.ui.map

import com.shunsoco.trainlivemap.data.model.LngLat
import com.shunsoco.trainlivemap.data.model.RailwayMapLine
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.domain.geo.GeoMath
import com.shunsoco.trainlivemap.domain.motion.MotionTransition
import com.shunsoco.trainlivemap.domain.motion.buildMotionTransition
import com.shunsoco.trainlivemap.domain.motion.resolveMotionTarget

/**
 * Holds animation state independently from Compose recomposition.
 *
 * A transition is finite and ends at the latest server-backed target. No
 * velocity prediction is performed after it finishes.
 */
class TrainMotionCoordinator(
    private val transitionDurationMillis: Long = DEFAULT_TRANSITION_MILLIS,
) {
    private data class Entry(
        val snapshotSignature: String,
        val startPosition: LngLat,
        val targetPosition: LngLat,
        val pathTransition: MotionTransition?,
        val startedAtMillis: Long,
    )

    private val entries = mutableMapOf<String, Entry>()

    fun updateTargets(
        trains: List<TrainLocation>,
        railwayLines: List<RailwayMapLine>,
        nowMillis: Long,
    ) {
        val linesById = railwayLines.associateBy(RailwayMapLine::id)
        val activeIds = HashSet<String>(trains.size)

        for (train in trains) {
            activeIds += train.id
            val signature = train.snapshotSignature()
            val previous = entries[train.id]
            if (previous?.snapshotSignature == signature) continue

            val apiPosition = LngLat(train.longitude, train.latitude)
            val linePaths = linesById[train.lineId]?.coordinates.orEmpty()
            val target = resolveMotionTarget(
                direction = train.direction,
                routeSegment = train.routeSegment,
                railwayPolylines = linePaths,
                apiPosition = apiPosition,
            )
            val currentPosition = previous?.positionAt(nowMillis) ?: target.position
            // Prefer the complete railway geometry so a segment change can be
            // joined from the exact previously rendered point without jumping
            // to the nearest point on the new station-to-station segment.
            val pathTransition = transitionOnNearestRailway(
                    currentPosition = currentPosition,
                    targetPosition = target.position,
                    railwayPolylines = linePaths,
                )
                ?: buildMotionTransition(currentPosition, target)

            entries[train.id] = Entry(
                snapshotSignature = signature,
                startPosition = currentPosition,
                targetPosition = target.position,
                pathTransition = pathTransition,
                startedAtMillis = nowMillis,
            )
        }

        entries.keys.retainAll(activeIds)
    }

    fun positions(nowMillis: Long): Map<String, LngLat> =
        entries.mapValues { (_, entry) -> entry.positionAt(nowMillis) }

    fun isAnimating(nowMillis: Long): Boolean = entries.values.any { entry ->
        nowMillis - entry.startedAtMillis < transitionDurationMillis
    }

    private fun Entry.positionAt(nowMillis: Long): LngLat {
        val progress = if (transitionDurationMillis <= 0L) {
            1.0
        } else {
            ((nowMillis - startedAtMillis).toDouble() / transitionDurationMillis)
                .coerceIn(0.0, 1.0)
        }
        val eased = progress * progress * (3.0 - 2.0 * progress)
        return pathTransition?.positionAt(eased)
            ?: GeoMath.interpolateCoordinates(startPosition, targetPosition, eased)
    }

    private fun transitionOnNearestRailway(
        currentPosition: LngLat,
        targetPosition: LngLat,
        railwayPolylines: List<List<LngLat>>,
    ): MotionTransition? {
        val index = GeoMath.nearestPolyline(targetPosition, railwayPolylines) ?: return null
        val from = GeoMath.projectPoint(currentPosition, index) ?: return null
        val to = GeoMath.projectPoint(targetPosition, index) ?: return null
        return MotionTransition(
            index = index,
            fromFraction = from.fraction,
            toFraction = to.fraction,
        )
    }

    private fun TrainLocation.snapshotSignature(): String = buildString {
        append(lastUpdatedAt)
        append('|')
        append(latitude)
        append('|')
        append(longitude)
        append('|')
        append(routeSegment?.fromFraction)
        append('|')
        append(routeSegment?.toFraction)
        append('|')
        append(routeSegment?.coordinates.hashCode())
    }

    companion object {
        private const val DEFAULT_TRANSITION_MILLIS = 5_500L
    }
}
