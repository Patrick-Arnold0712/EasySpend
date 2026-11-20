package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

data class CapturedLocationContext(
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Lightweight, GMS-free Android location context resolver using system [LocationManager] + [Geocoder].
 * Uses coarse/network location by default (fast indoors, low battery, Play-Policy compliant)
 * and gracefully returns null if permission is not granted or providers are unavailable.
 */
object LocationContextResolver {

    private const val TAG = "LocationContextResolver"
    private const val FRESH_LOCATION_MAX_AGE_MS = 10 * 60 * 1000L
    private const val LIVE_FIX_TIMEOUT_MS = 1_800L
    private const val GEOCODE_TIMEOUT_MS = 1_800L

    fun hasLocationPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    /**
     * Captures a quick location label (e.g. "广州·天河区·中山大学东校园") to improve bill classification.
     * Never throws; returns null when location permission is denied or unavailable.
     */
    suspend fun captureCurrentLocation(context: Context): CapturedLocationContext? = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!hasLocationPermission(appCtx)) return@withContext null

        val lm = appCtx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val location = findBestRecentLastKnown(lm) ?: requestQuickCurrentLocation(appCtx, lm)
            ?: return@withContext null

        val label = reverseGeocodeLabel(appCtx, location.latitude, location.longitude)
            .ifBlank { formatCompactCoordinates(location.latitude, location.longitude) }

        CapturedLocationContext(
            label = label,
            latitude = location.latitude,
            longitude = location.longitude,
            capturedAtMillis = System.currentTimeMillis()
        )
    }

    @SuppressLint("MissingPermission")
    private fun findBestRecentLastKnown(lm: LocationManager): Location? {
        val now = System.currentTimeMillis()
        val candidateProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }

        val candidates = candidateProviders.mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }
        if (candidates.isEmpty()) return null

        val fresh = candidates.filter { loc ->
            val age = abs(now - loc.time)
            loc.time > 0L && age <= FRESH_LOCATION_MAX_AGE_MS
        }
        if (fresh.isNotEmpty()) {
            return fresh.minWithOrNull(
                compareBy<Location> { abs(now - it.time) / 60_000L }
                    .thenBy { if (it.hasAccuracy()) it.accuracy else 500f }
            )
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestQuickCurrentLocation(
        context: Context,
        lm: LocationManager
    ): Location? {
        val preferredProvider = pickAvailableProvider(lm) ?: return fallbackAnyLastKnown(lm)

        val live = withTimeoutOrNull(LIVE_FIX_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { runCatching { signal.cancel() } }
                    runCatching {
                        lm.getCurrentLocation(
                            preferredProvider,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                suspendCancellableCoroutine<Location?> { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { lm.removeUpdates(this) }
                            if (cont.isActive) cont.resume(location)
                        }
                    }
                    cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
                    runCatching {
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(preferredProvider, listener, Looper.getMainLooper())
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        return live ?: fallbackAnyLastKnown(lm)
    }

    @SuppressLint("MissingPermission")
    private fun fallbackAnyLastKnown(lm: LocationManager): Location? {
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        return providers.mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    private fun pickAvailableProvider(lm: LocationManager): String? {
        val order = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return order.firstOrNull { provider ->
            runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private suspend fun reverseGeocodeLabel(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String {
        if (!Geocoder.isPresent()) return ""
        return withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            try {
                val geocoder = Geocoder(context, Locale.CHINA)
                val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 3) { list ->
                            if (cont.isActive) cont.resume(list)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 3).orEmpty()
                }
                formatBestAddressLabel(addresses)
            } catch (e: Exception) {
                Log.d(TAG, "Reverse geocode unavailable: ${e.message}")
                ""
            }
        }.orEmpty()
    }

    internal fun formatBestAddressLabel(addresses: List<Address>): String {
        val addr = addresses.firstOrNull() ?: return ""
        val parts = linkedSetOf<String>()

        val city = addr.locality?.trim()?.removeSuffix("市").orEmpty()
        val district = addr.subLocality?.trim().orEmpty()
        val thoroughfare = addr.thoroughfare?.trim().orEmpty()
        val feature = addr.featureName?.trim().orEmpty()
        val premises = addr.premises?.trim().orEmpty()

        if (city.isNotBlank()) parts.add(city)
        if (district.isNotBlank() && district != city) parts.add(district)

        val specificPlace = listOf(premises, feature, thoroughfare)
            .firstOrNull { candidate ->
                candidate.isNotBlank() &&
                    !candidate.all { it.isDigit() || it == '-' || it == '号' } &&
                    candidate != city &&
                    candidate != district
            }.orEmpty()

        if (specificPlace.isNotBlank()) {
            parts.add(specificPlace)
        } else if (thoroughfare.isNotBlank()) {
            parts.add(thoroughfare)
        }

        if (parts.isEmpty()) {
            val line0 = addr.getAddressLine(0)?.trim().orEmpty()
            return line0.take(28)
        }
        return parts.joinToString("·").take(32)
    }

    private fun formatCompactCoordinates(latitude: Double, longitude: Double): String {
        val ns = if (latitude >= 0) "N" else "S"
        val ew = if (longitude >= 0) "E" else "W"
        return String.format(Locale.US, "定位 %.2f°%s,%.2f°%s", abs(latitude), ns, abs(longitude), ew)
    }
}
