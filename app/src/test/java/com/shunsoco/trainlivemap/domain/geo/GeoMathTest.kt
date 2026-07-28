package com.shunsoco.trainlivemap.domain.geo

import com.shunsoco.trainlivemap.data.model.LngLat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun `haversine calculates approximately one degree at equator`() {
        val meters = GeoMath.haversineMeters(
            LngLat(longitude = 0.0, latitude = 0.0),
            LngLat(longitude = 1.0, latitude = 0.0),
        )

        assertEquals(111_195.0, meters, 200.0)
    }

    @Test
    fun `fraction clamp handles range and invalid values`() {
        assertEquals(0.0, GeoMath.clampFraction(-0.2), 0.0)
        assertEquals(0.4, GeoMath.clampFraction(0.4), 0.0)
        assertEquals(1.0, GeoMath.clampFraction(1.2), 0.0)
        assertEquals(0.0, GeoMath.clampFraction(Double.NaN), 0.0)
    }

    @Test
    fun `point at fraction follows polyline length and clamps endpoints`() {
        val index = GeoMath.buildPolylineIndex(
            listOf(
                LngLat(longitude = 139.0, latitude = 35.0),
                LngLat(longitude = 139.5, latitude = 35.0),
                LngLat(longitude = 140.0, latitude = 35.0),
            ),
        )

        assertEquals(
            139.0,
            requireNotNull(GeoMath.pointAtFraction(index, -1.0)).longitude,
            1e-9,
        )
        assertEquals(
            139.5,
            requireNotNull(GeoMath.pointAtFraction(index, 0.5)).longitude,
            1e-6,
        )
        assertEquals(
            140.0,
            requireNotNull(GeoMath.pointAtFraction(index, 2.0)).longitude,
            1e-9,
        )
    }

    @Test
    fun `projection clamps an off-track coordinate to the closest segment`() {
        val index = GeoMath.buildPolylineIndex(
            listOf(
                LngLat(longitude = 139.0, latitude = 35.0),
                LngLat(longitude = 140.0, latitude = 35.0),
            ),
        )
        val projection = GeoMath.projectPoint(
            point = LngLat(longitude = 139.25, latitude = 35.2),
            index = index,
        )

        assertNotNull(projection)
        requireNotNull(projection)
        assertEquals(139.25, projection.position.longitude, 1e-6)
        assertEquals(35.0, projection.position.latitude, 1e-9)
        assertEquals(0.25, projection.fraction, 1e-3)
        assertTrue(projection.distanceMeters > 20_000.0)
    }

    @Test
    fun `interpolation clamps progress to zero through one`() {
        assertEquals(
            0.2,
            GeoMath.interpolateFraction(0.2, 0.8, -1.0),
            0.0,
        )
        assertEquals(
            0.5,
            GeoMath.interpolateFraction(0.2, 0.8, 0.5),
            1e-9,
        )
        assertEquals(
            0.8,
            GeoMath.interpolateFraction(0.2, 0.8, 5.0),
            0.0,
        )
    }
}
