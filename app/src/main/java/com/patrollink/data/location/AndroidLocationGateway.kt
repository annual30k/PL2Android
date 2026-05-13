package com.patrollink.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.LocationGateway
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidLocationGateway(
    context: Context,
    private val fallback: GpsLocation
) : LocationGateway {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GpsLocation = suspendCancellableCoroutine { continuation ->
        val cancellationTokenSource = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resumeIfActive(location.toGpsLocation())
                } else {
                    resumeLastKnownLocation(continuation)
                }
            }
            .addOnFailureListener {
                resumeLastKnownLocation(continuation)
            }
    }

    @SuppressLint("MissingPermission")
    private fun resumeLastKnownLocation(continuation: CancellableContinuation<GpsLocation>) {
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                continuation.resumeIfActive(location?.toGpsLocation() ?: fallback)
            }
            .addOnFailureListener {
                continuation.resumeIfActive(fallback)
            }
    }

    private fun Location.toGpsLocation(): GpsLocation =
        GpsLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy.takeIf { it > 0f } ?: fallback.accuracyMeters,
            address = fallback.address
        )

    private fun CancellableContinuation<GpsLocation>.resumeIfActive(location: GpsLocation) {
        if (isActive) resume(location)
    }
}
