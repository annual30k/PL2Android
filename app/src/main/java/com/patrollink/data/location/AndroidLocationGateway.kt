package com.patrollink.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.LocationGateway
import kotlin.math.abs
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidLocationGateway(
    context: Context,
    private val fallback: GpsLocation
) : LocationGateway {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GpsLocation = suspendCancellableCoroutine { continuation ->
        if (!appContext.hasLocationPermission()) {
            continuation.resumeIfActive(fallback)
            return@suspendCancellableCoroutine
        }
        val cancellationTokenSource = CancellationTokenSource()
        var platformCleanup: (() -> Unit)? = null
        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
            platformCleanup?.invoke()
        }

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null && location.hasUsableCoordinate()) {
                    continuation.resumeIfActive(location.toGpsLocation())
                } else {
                    resumeLastKnownLocation(continuation) { cleanup -> platformCleanup = cleanup }
                }
            }
            .addOnFailureListener {
                resumeLastKnownLocation(continuation) { cleanup -> platformCleanup = cleanup }
            }
    }

    @SuppressLint("MissingPermission")
    private fun resumeLastKnownLocation(
        continuation: CancellableContinuation<GpsLocation>,
        registerPlatformCleanup: (() -> Unit) -> Unit
    ) {
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null && location.hasUsableCoordinate()) {
                    continuation.resumeIfActive(location.toGpsLocation())
                } else {
                    resumeFreshPlatformLocation(continuation, registerPlatformCleanup)
                }
            }
            .addOnFailureListener {
                resumeFreshPlatformLocation(continuation, registerPlatformCleanup)
            }
    }

    @SuppressLint("MissingPermission")
    private fun resumeFreshPlatformLocation(
        continuation: CancellableContinuation<GpsLocation>,
        registerPlatformCleanup: (() -> Unit) -> Unit
    ) {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            continuation.resumeIfActive(fallback)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        lateinit var listener: LocationListener
        var timeoutRunnable: Runnable? = null
        val finish: (Location?) -> Unit = { location ->
            runCatching { locationManager.removeUpdates(listener) }
            timeoutRunnable?.let(handler::removeCallbacks)
            if (location != null && location.hasUsableCoordinate()) {
                continuation.resumeIfActive(location.toGpsLocation())
            } else {
                continuation.resumeIfActive(fallback)
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = finish(location)
            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        registerPlatformCleanup {
            runCatching { locationManager.removeUpdates(listener) }
            timeoutRunnable?.let(handler::removeCallbacks)
        }
        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
            }
        }
        val timeout = Runnable {
            val lastKnown = providers
                .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
                .firstOrNull { it.hasUsableCoordinate() }
            finish(lastKnown)
        }
        timeoutRunnable = timeout
        handler.postDelayed(timeout, FreshLocationTimeoutMillis)
    }

    private fun Location.toGpsLocation(): GpsLocation =
        GpsLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy.takeIf { it > 0f } ?: fallback.accuracyMeters,
            address = fallback.address
        )

    private fun Location.hasUsableCoordinate(): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(abs(latitude) < 0.000001 && abs(longitude) < 0.000001)

    private fun Context.hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun CancellableContinuation<GpsLocation>.resumeIfActive(location: GpsLocation) {
        if (isActive) resume(location)
    }

    private companion object {
        const val FreshLocationTimeoutMillis = 12_000L
    }
}
