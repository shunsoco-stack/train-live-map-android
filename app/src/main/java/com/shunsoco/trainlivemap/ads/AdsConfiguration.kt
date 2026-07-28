package com.shunsoco.trainlivemap.ads

import com.shunsoco.trainlivemap.BuildConfig

/**
 * AdMob identifiers injected by the Android build.
 *
 * Debug builds use Google's public test identifiers. Release builds stay disabled until both
 * identifiers are supplied through the uncommitted local.properties file.
 */
class AdsConfiguration internal constructor(
    val appId: String,
    val bannerAdUnitId: String,
) {
    val isConfigured: Boolean
        get() = appId.isNotEmpty() && bannerAdUnitId.isNotEmpty()

    companion object {
        fun fromBuildConfig(): AdsConfiguration =
            create(
                appId = BuildConfig.ADMOB_APP_ID,
                bannerAdUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
            )

        internal fun create(
            appId: String,
            bannerAdUnitId: String,
        ): AdsConfiguration =
            AdsConfiguration(
                appId = appId.trim(),
                bannerAdUnitId = bannerAdUnitId.trim(),
            )
    }
}
