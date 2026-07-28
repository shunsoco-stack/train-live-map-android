package com.shunsoco.trainlivemap.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlin.math.floor

/**
 * Bottom-bar friendly, full-width anchored adaptive AdMob banner.
 *
 * Nothing is composed when release IDs are empty, consent does not allow ads, or Mobile Ads has
 * not completed initialization. The caller should place this below map/content rather than
 * overlaying it.
 */
@Composable
fun AnchoredAdaptiveBanner(
    adsManager: AdsManager,
    modifier: Modifier = Modifier,
) {
    val adsState by adsManager.state.collectAsStateWithLifecycle()
    val adUnitId = adsManager.bannerAdUnitId
    if (!adsState.isBannerReady || adUnitId == null) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidthDp = floor(maxWidth.value).toInt().coerceAtLeast(1)

        key(adUnitId, availableWidthDp) {
            BannerView(
                adUnitId = adUnitId,
                widthDp = availableWidthDp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "広告" },
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun BannerView(
    adUnitId: String,
    widthDp: Int,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adView =
        remember(adUnitId, widthDp) {
            AdView(context).apply {
                this.adUnitId = adUnitId
                // Keep the map-first screen compact. The legacy-standard
                // anchored adaptive API remains available in GMA 25 and
                // produces a banner-sized slot rather than the newer large
                // (up to 150 dp) video-oriented slot.
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        widthDp,
                    ),
                )
            }
        }

    LaunchedEffect(adView) {
        adView.loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(adView, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> adView.resume()
                    Lifecycle.Event.ON_PAUSE -> adView.pause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            adView.resume()
        } else {
            adView.pause()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier,
    )
}
