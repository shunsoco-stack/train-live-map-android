package com.shunsoco.trainlivemap.domain.geo

import com.shunsoco.trainlivemap.data.model.LngLat
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class PolylineIndex(
    val points: List<LngLat>,
    val cumulativeMeters: List<Double>,
    val totalLengthMeters: Double,
)

data class ProjectedPoint(
    val position: LngLat,
    val fraction: Double,
    /** Segment start index in [PolylineIndex.points]. */
    val segmentIndex: Int,
    val distanceMeters: Double,
)

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Clamps a normalized fraction to [0, 1]. Invalid input resolves to zero,
     * keeping downstream map coordinates finite and deterministic.
     */
    fun clampFraction(value: Double): Double = when {
        !value.isFinite() -> 0.0
        value < 0.0 -> 0.0
        value > 1.0 -> 1.0
        else -> value
    }

    fun haversineMeters(
        first: LngLat,
        second: LngLat,
    ): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val haversine = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(firstLatitude) *
            cos(secondLatitude) *
            sin(longitudeDelta / 2.0) *
            sin(longitudeDelta / 2.0)

        return 2.0 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(haversine)))
    }

    fun buildPolylineIndex(points: List<LngLat>): PolylineIndex {
        if (points.isEmpty()) {
            return PolylineIndex(
                points = emptyList(),
                cumulativeMeters = emptyList(),
                totalLengthMeters = 0.0,
            )
        }

        val cumulative = ArrayList<Double>(points.size)
        cumulative += 0.0
        for (index in 1 until points.size) {
            cumulative += cumulative.last() + haversineMeters(
                points[index - 1],
                points[index],
            )
        }
        return PolylineIndex(
            points = points.toList(),
            cumulativeMeters = cumulative,
            totalLengthMeters = cumulative.last(),
        )
    }

    fun pointAtFraction(
        index: PolylineIndex,
        fraction: Double,
    ): LngLat? {
        if (index.points.isEmpty()) return null
        if (index.points.size == 1 || index.totalLengthMeters <= 0.0) {
            return index.points.first()
        }

        val targetMeters = clampFraction(fraction) * index.totalLengthMeters
        for (pointIndex in 1 until index.points.size) {
            if (targetMeters <= index.cumulativeMeters[pointIndex]) {
                val segmentStart = index.cumulativeMeters[pointIndex - 1]
                val segmentLength = index.cumulativeMeters[pointIndex] - segmentStart
                val localProgress = if (segmentLength <= 0.0) {
                    0.0
                } else {
                    (targetMeters - segmentStart) / segmentLength
                }
                return interpolateCoordinates(
                    index.points[pointIndex - 1],
                    index.points[pointIndex],
                    localProgress,
                )
            }
        }
        return index.points.last()
    }

    fun interpolateCoordinates(
        from: LngLat,
        to: LngLat,
        progress: Double,
    ): LngLat {
        val clamped = clampFraction(progress)
        return LngLat(
            longitude = from.longitude + (to.longitude - from.longitude) * clamped,
            latitude = from.latitude + (to.latitude - from.latitude) * clamped,
        )
    }

    fun interpolateFraction(
        fromFraction: Double,
        toFraction: Double,
        progress: Double,
    ): Double {
        val from = clampFraction(fromFraction)
        val to = clampFraction(toFraction)
        val clampedProgress = clampFraction(progress)
        return clampFraction(from + (to - from) * clampedProgress)
    }

    /**
     * Projects [point] onto the closest segment of [index].
     *
     * A local equirectangular plane is used only to find the closest point;
     * the reported distance is then calculated with Haversine. This is stable
     * and sufficiently accurate for railway segments around Kanto.
     */
    fun projectPoint(
        point: LngLat,
        index: PolylineIndex,
    ): ProjectedPoint? {
        if (index.points.isEmpty()) return null
        if (index.points.size == 1) {
            return ProjectedPoint(
                position = index.points.first(),
                fraction = 0.0,
                segmentIndex = 0,
                distanceMeters = haversineMeters(point, index.points.first()),
            )
        }

        var best: ProjectedPoint? = null
        for (segmentIndex in 0 until index.points.lastIndex) {
            val from = index.points[segmentIndex]
            val to = index.points[segmentIndex + 1]
            val segmentProgress = projectionProgress(point, from, to)
            val projected = interpolateCoordinates(from, to, segmentProgress)
            val distance = haversineMeters(point, projected)
            val segmentLength = index.cumulativeMeters[segmentIndex + 1] -
                index.cumulativeMeters[segmentIndex]
            val distanceAlongLine = index.cumulativeMeters[segmentIndex] +
                segmentLength * segmentProgress
            val fraction = if (index.totalLengthMeters <= 0.0) {
                0.0
            } else {
                clampFraction(distanceAlongLine / index.totalLengthMeters)
            }
            val candidate = ProjectedPoint(
                position = projected,
                fraction = fraction,
                segmentIndex = segmentIndex,
                distanceMeters = distance,
            )
            if (best == null || candidate.distanceMeters < best.distanceMeters) {
                best = candidate
            }
        }
        return best
    }

    fun nearestPolyline(
        point: LngLat,
        candidates: List<List<LngLat>>,
    ): PolylineIndex? = candidates
        .asSequence()
        .filter { it.isNotEmpty() }
        .map(::buildPolylineIndex)
        .mapNotNull { index ->
            projectPoint(point, index)?.let { projection -> index to projection.distanceMeters }
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first

    private fun projectionProgress(
        point: LngLat,
        from: LngLat,
        to: LngLat,
    ): Double {
        val referenceLatitudeRadians = Math.toRadians(point.latitude)
        val longitudeScale = cos(referenceLatitudeRadians)
        val pointX = point.longitude * longitudeScale
        val pointY = point.latitude
        val fromX = from.longitude * longitudeScale
        val fromY = from.latitude
        val toX = to.longitude * longitudeScale
        val toY = to.latitude
        val deltaX = toX - fromX
        val deltaY = toY - fromY
        val lengthSquared = deltaX * deltaX + deltaY * deltaY
        if (lengthSquared <= 0.0) return 0.0

        return clampFraction(
            ((pointX - fromX) * deltaX + (pointY - fromY) * deltaY) /
                lengthSquared,
        )
    }
}
