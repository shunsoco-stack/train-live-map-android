package com.shunsoco.trainlivemap.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsConfigurationTest {
    @Test
    fun `both identifiers enable ads`() {
        val configuration =
            AdsConfiguration.create(
                appId = "ca-app-pub-test~app",
                bannerAdUnitId = "ca-app-pub-test/banner",
            )

        assertTrue(configuration.isConfigured)
    }

    @Test
    fun `missing either identifier disables entire ad surface`() {
        assertFalse(AdsConfiguration.create("", "banner").isConfigured)
        assertFalse(AdsConfiguration.create("app", "").isConfigured)
        assertFalse(AdsConfiguration.create(" ", " ").isConfigured)
    }

    @Test
    fun `identifiers are trimmed before use`() {
        val configuration = AdsConfiguration.create(" app-id ", " banner-id ")

        assertEquals("app-id", configuration.appId)
        assertEquals("banner-id", configuration.bannerAdUnitId)
    }
}
