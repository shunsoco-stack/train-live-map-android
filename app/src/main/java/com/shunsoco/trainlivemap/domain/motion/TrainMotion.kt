package com.shunsoco.trainlivemap.domain.motion

import com.shunsoco.trainlivemap.data.model.LngLat
import com.shunsoco.trainlivemap.data.model.RouteSegmentEstimate
import com.shunsoco.trainlivemap.data.model.TrainDirection
import com.shunsoco.trainlivemap.domain.geo.GeoMath
import com.shunsoco.trainlivemap.domain.geo.PolylineIndex

enum class MotionPathSource {
    /** The API supplied an ordered, segment-local polyline. */
    ROUTE_SEGMENT_COORDINATES,

    /** Fractions are evaluated on the matching full railway polyline. */
    RAILWAY_POLYLINE,
}

data class MotionPath(
    val index: PolylineIndex,
    val fromFraction: Double,
    val toFraction: Double,
    val source: MotionPathSource,
) {
    fun fractionAt(progress: Double): Double = GeoMath.interpolateFraction(
        fromFraction = fromFraction,
        toFraction = toFraction,
        progress = progress,
    )

    fun positionAt(progress: Double): LngLat? = GeoMath.pointAtFraction(
        index = index,
        fraction = fractionAt(progress),
    )
}

enum class MotionTargetSource {
    ROUTE_SEGMENT,
    PROJECTED_API_POSITION,
    API_POSITION,
}

data class MotionTarget(
    val position: LngLat,
    val source: MotionTargetSource,
    val path: MotionPath? = null,
    val fraction: Double? = null,
)

/**
 * Resolves an oriented path for an estimated station-to-station segment.
 *
 * When coordinates are supplied they are authoritative and their explicit
 * from -> to ordering is retained. When only fractions are supplied, the full
 * line's coordinate order is an implementation detail, so travel direction
 * determines the orientation: outbound increases and inbound decreases.
 */
fun resolveMotionPath(
    direction: TrainDirection,
    routeSegment: RouteSegmentEstimate?,
    railwayPolylines: List<List<LngLat>>,
    apiPosition: LngLat,
): MotionPath? {
    routeSegment ?: return null

    val segmentCoordinates = routeSegment.coordinates
        ?.takeIf { it.size >= 2 }
    if (segmentCoordinates != null) {
        return MotionPath(
            index = GeoMath.buildPolylineIndex(segmentCoordinates),
            fromFraction = GeoMath.clampFraction(routeSegment.fromFraction),
            toFraction = GeoMath.clampFraction(routeSegment.toFraction),
            source = MotionPathSource.ROUTE_SEGMENT_COORDINATES,
        )
    }

    val lineIndex = GeoMath.nearestPolyline(
        point = apiPosition,
        candidates = railwayPolylines,
    ) ?: return null
    val lowerFraction = minOf(
        GeoMath.clampFraction(routeSegment.fromFraction),
        GeoMath.clampFraction(routeSegment.toFraction),
    )
    val upperFraction = maxOf(
        GeoMath.clampFraction(routeSegment.fromFraction),
        GeoMath.clampFraction(routeSegment.toFraction),
    )
    val (fromFraction, toFraction) = when (direction) {
        TrainDirection.OUTBOUND -> lowerFraction to upperFraction
        TrainDirection.INBOUND -> upperFraction to lowerFraction
    }

    return MotionPath(
        index = lineIndex,
        fromFraction = fromFraction,
        toFraction = toFraction,
        source = MotionPathSource.RAILWAY_POLYLINE,
    )
}

/**
 * Finds the target for a newly received API snapshot.
 *
 * When [segmentProgress] is supplied it is clamped to 0..1. Production callers
 * normally omit it: the server coordinate is then projected onto the observed
 * segment and constrained between its endpoints. This lets each new snapshot
 * move the marker without inventing motion after updates stop. If
 * route-segment data is unavailable, the API latitude/longitude is projected
 * onto the nearest railway. With no usable railway geometry, the API
 * coordinate is returned unchanged.
 */
fun resolveMotionTarget(
    direction: TrainDirection,
    routeSegment: RouteSegmentEstimate?,
    railwayPolylines: List<List<LngLat>>,
    apiPosition: LngLat,
    segmentProgress: Double? = null,
): MotionTarget {
    val path = resolveMotionPath(
        direction = direction,
        routeSegment = routeSegment,
        railwayPolylines = railwayPolylines,
        apiPosition = apiPosition,
    )
    if (path != null) {
        val projectedFraction = GeoMath.projectPoint(apiPosition, path.index)?.fraction
        val lowerBound = minOf(path.fromFraction, path.toFraction)
        val upperBound = maxOf(path.fromFraction, path.toFraction)
        val fraction = if (segmentProgress != null) {
            path.fractionAt(segmentProgress)
        } else {
            projectedFraction
                ?.coerceIn(lowerBound, upperBound)
                ?: path.fractionAt(0.5)
        }
        val position = GeoMath.pointAtFraction(path.index, fraction)
        if (position != null) {
            return MotionTarget(
                position = position,
                source = MotionTargetSource.ROUTE_SEGMENT,
                path = path,
                fraction = fraction,
            )
        }
    }

    val lineIndex = GeoMath.nearestPolyline(
        point = apiPosition,
        candidates = railwayPolylines,
    )
    val projection = lineIndex?.let { GeoMath.projectPoint(apiPosition, it) }
    return if (projection != null) {
        MotionTarget(
            position = projection.position,
            source = MotionTargetSource.PROJECTED_API_POSITION,
            fraction = projection.fraction,
        )
    } else {
        MotionTarget(
            position = apiPosition,
            source = MotionTargetSource.API_POSITION,
        )
    }
}

/**
 * A finite transition from the currently rendered position to a new target.
 *
 * Both endpoints are clamped to the same polyline. Calling [positionAt] with
 * progress greater than one returns the target and never predicts movement
 * beyond the latest snapshot.
 */
data class MotionTransition(
    val index: PolylineIndex,
    val fromFraction: Double,
    val toFraction: Double,
) {
    fun positionAt(progress: Double): LngLat? = GeoMath.pointAtFraction(
        index = index,
        fraction = GeoMath.interpolateFraction(
            fromFraction = fromFraction,
            toFraction = toFraction,
            progress = progress,
        ),
    )
}

fun buildMotionTransition(
    currentPosition: LngLat,
    target: MotionTarget,
): MotionTransition? {
    val path = target.path ?: return null
    val currentProjection = GeoMath.projectPoint(currentPosition, path.index) ?: return null
    val targetFraction = target.fraction
        ?: GeoMath.projectPoint(target.position, path.index)?.fraction
        ?: return null
    return MotionTransition(
        index = path.index,
        fromFraction = currentProjection.fraction,
        toFraction = GeoMath.clampFraction(targetFraction),
    )
}
