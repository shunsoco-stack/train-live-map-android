package com.shunsoco.trainlivemap.domain.motion

import com.shunsoco.trainlivemap.data.model.LngLat
import com.shunsoco.trainlivemap.data.model.RouteSegmentEstimate
import com.shunsoco.trainlivemap.data.model.TrainDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrainMotionTest {
    private val railway = listOf(
        listOf(
            LngLat(longitude = 139.0, latitude = 35.0),
            LngLat(longitude = 140.0, latitude = 35.0),
        ),
    )
    private val apiPosition = LngLat(longitude = 139.5, latitude = 35.1)

    @Test
    fun `segment coordinates retain explicit from to order regardless of direction`() {
        val path = resolveMotionPath(
            direction = TrainDirection.OUTBOUND,
            routeSegment = RouteSegmentEstimate(
                fromFraction = 0.8,
                toFraction = 0.2,
                coordinates = railway.first(),
            ),
            railwayPolylines = railway,
            apiPosition = apiPosition,
        )

        assertNotNull(path)
        requireNotNull(path)
        assertEquals(MotionPathSource.ROUTE_SEGMENT_COORDINATES, path.source)
        assertEquals(0.8, path.fromFraction, 0.0)
        assertEquals(0.2, path.toFraction, 0.0)
        assertEquals(139.8, requireNotNull(path.positionAt(0.0)).longitude, 1e-6)
        assertEquals(139.2, requireNotNull(path.positionAt(1.0)).longitude, 1e-6)
    }

    @Test
    fun `full railway fractions increase for outbound`() {
        val path = resolveMotionPath(
            direction = TrainDirection.OUTBOUND,
            routeSegment = RouteSegmentEstimate(
                fromFraction = 0.8,
                toFraction = 0.2,
            ),
            railwayPolylines = railway,
            apiPosition = apiPosition,
        )

        requireNotNull(path)
        assertEquals(MotionPathSource.RAILWAY_POLYLINE, path.source)
        assertEquals(0.2, path.fromFraction, 0.0)
        assertEquals(0.8, path.toFraction, 0.0)
    }

    @Test
    fun `full railway fractions decrease for inbound`() {
        val path = resolveMotionPath(
            direction = TrainDirection.INBOUND,
            routeSegment = RouteSegmentEstimate(
                fromFraction = 0.2,
                toFraction = 0.8,
            ),
            railwayPolylines = railway,
            apiPosition = apiPosition,
        )

        requireNotNull(path)
        assertEquals(0.8, path.fromFraction, 0.0)
        assertEquals(0.2, path.toFraction, 0.0)
    }

    @Test
    fun `target progress is clamped and cannot leave the segment`() {
        val target = resolveMotionTarget(
            direction = TrainDirection.OUTBOUND,
            routeSegment = RouteSegmentEstimate(
                fromFraction = 0.2,
                toFraction = 0.8,
                coordinates = railway.first(),
            ),
            railwayPolylines = railway,
            apiPosition = apiPosition,
            segmentProgress = 4.0,
        )

        assertEquals(MotionTargetSource.ROUTE_SEGMENT, target.source)
        assertEquals(0.8, target.fraction ?: error("missing fraction"), 0.0)
        assertEquals(139.8, target.position.longitude, 1e-6)
    }

    @Test
    fun `default target follows the API coordinate projected inside the segment`() {
        val target = resolveMotionTarget(
            direction = TrainDirection.OUTBOUND,
            routeSegment = RouteSegmentEstimate(
                fromFraction = 0.2,
                toFraction = 0.8,
                coordinates = railway.first(),
            ),
            railwayPolylines = railway,
            apiPosition = LngLat(longitude = 139.65, latitude = 35.2),
        )

        assertEquals(MotionTargetSource.ROUTE_SEGMENT, target.source)
        assertEquals(0.65, target.fraction ?: error("missing fraction"), 1e-6)
        assertEquals(139.65, target.position.longitude, 1e-6)
        assertEquals(35.0, target.position.latitude, 1e-9)
    }

    @Test
    fun `projected API target is clamped to the observed segment`() {
        val target = resolveMotionTarget(
            direction = TrainDirection.OUTBOUND,
            routeSegment = RouteSegmentEstimate(
                fromFraction = 0.2,
                toFraction = 0.6,
                coordinates = railway.first(),
            ),
            railwayPolylines = railway,
            apiPosition = LngLat(longitude = 139.95, latitude = 35.0),
        )

        assertEquals(0.6, target.fraction ?: error("missing fraction"), 0.0)
        assertEquals(139.6, target.position.longitude, 1e-6)
    }

    @Test
    fun `missing segment projects API coordinates onto railway`() {
        val target = resolveMotionTarget(
            direction = TrainDirection.OUTBOUND,
            routeSegment = null,
            railwayPolylines = railway,
            apiPosition = apiPosition,
        )

        assertEquals(MotionTargetSource.PROJECTED_API_POSITION, target.source)
        assertEquals(139.5, target.position.longitude, 1e-6)
        assertEquals(35.0, target.position.latitude, 1e-9)
    }

    @Test
    fun `missing segment and geometry falls back to API coordinate`() {
        val target = resolveMotionTarget(
            direction = TrainDirection.OUTBOUND,
            routeSegment = null,
            railwayPolylines = emptyList(),
            apiPosition = apiPosition,
        )

        assertEquals(MotionTargetSource.API_POSITION, target.source)
        assertEquals(apiPosition, target.position)
    }

    @Test
    fun `transition clamps current point and stops at target without extrapolation`() {
        val target = resolveMotionTarget(
            direction = TrainDirection.OUTBOUND,
            routeSegment = RouteSegmentEstimate(
                fromFraction = 0.2,
                toFraction = 0.8,
                coordinates = railway.first(),
            ),
            railwayPolylines = railway,
            apiPosition = apiPosition,
            segmentProgress = 0.75,
        )
        val transition = buildMotionTransition(
            currentPosition = LngLat(longitude = 139.1, latitude = 35.5),
            target = target,
        )

        requireNotNull(transition)
        assertEquals(139.1, requireNotNull(transition.positionAt(0.0)).longitude, 1e-6)
        val targetPosition = requireNotNull(transition.positionAt(1.0))
        val afterAnimation = requireNotNull(transition.positionAt(10.0))
        assertEquals(targetPosition.longitude, afterAnimation.longitude, 0.0)
        assertEquals(targetPosition.latitude, afterAnimation.latitude, 0.0)
        assertEquals(35.0, afterAnimation.latitude, 1e-9)
    }
}
