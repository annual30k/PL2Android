package com.patrollink.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.patrollink.domain.GpsLocation
import com.patrollink.domain.LocationGateway
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidLocationGateway(
    context: Context,
    private val fallback: GpsLocation
) : LocationGateway {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GpsLocation = suspendCancellableCoroutine { continuation ->
        fusedClient.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(
                    if (location != null) {
                        GpsLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy.takeIf { it > 0f } ?: fallback.accuracyMeters,
                            address = fallback.address
                        )
                    } else {
                        fallback
                    }
                )
            }
            .addOnFailureListener {
                continuation.resume(fallback)
            }
    }
}
