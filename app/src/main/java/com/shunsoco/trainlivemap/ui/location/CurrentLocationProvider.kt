package com.shunsoco.trainlivemap.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.shunsoco.trainlivemap.data.model.LngLat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

fun interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): LngLat?
}

/**
 * Performs a one-shot approximate-location lookup for a user-initiated camera
 * movement. The returned coordinate never enters a repository, API model, or
 * persisted state.
 */
class AndroidCurrentLocationProvider(
    context: Context,
) : CurrentLocationProvider {
    private val applicationContext = context.applicationContext

    override suspend fun getCurrentLocation(): LngLat? {
        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val manager = applicationContext.getSystemService(LocationManager::class.java)
            ?: return null
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            manager.awaitCurrentNetworkLocation()
        } ?: manager.lastKnownNetworkLocation()
        return location?.let { LngLat(longitude = it.longitude, latitude = it.latitude) }
    }

    private suspend fun LocationManager.awaitCurrentNetworkLocation(): Location? {
        if (
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        if (!runCatching { isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    this,
                    LocationManager.NETWORK_PROVIDER,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(applicationContext),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun LocationManager.lastKnownNetworkLocation(): Location? =
        runCatching { getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 10_000L
    }
}
