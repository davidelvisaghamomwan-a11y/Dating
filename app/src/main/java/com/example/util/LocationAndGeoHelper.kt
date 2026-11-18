package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class ResolvedDeviceLocation(
    val exactLatitude: Double,
    val exactLongitude: Double,
    val coarseLatitude: Double,
    val coarseLongitude: Double,
    val cityLabel: String,
    val isLiveGps: Boolean
)

object LocationAndGeoHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarseGranted || fineGranted
    }

    fun roundToCoarse(coordinate: Double): Double {
        return round(coordinate * 100.0) / 100.0
    }

    fun haversineDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    fun formatDistanceLabel(distanceKm: Double): String {
        return when {
            distanceKm < 1.0 -> "< 1 km away"
            distanceKm < 10.0 -> String.format(Locale.US, "%.1f km away", distanceKm)
            else -> "${distanceKm.toInt()} km away"
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): ResolvedDeviceLocation? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val priority = if (fineGranted) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val currentLocation: Location? = suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc)
                    } else {
                        client.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    client.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last) }
                        .addOnFailureListener { cont.resume(null) }
                }
        }

        val location = currentLocation ?: return null
        val city = reverseGeocodeCity(context, location.latitude, location.longitude)
        return ResolvedDeviceLocation(
            exactLatitude = location.latitude,
            exactLongitude = location.longitude,
            coarseLatitude = roundToCoarse(location.latitude),
            coarseLongitude = roundToCoarse(location.longitude),
            cityLabel = city,
            isLiveGps = true
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeCity(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) {
            return@withContext formatCoordFallback(latitude, longitude)
        }
        try {
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1) { list ->
                        cont.resume(list)
                    }
                }
            } else {
                geocoder.getFromLocation(latitude, longitude, 1)
            }
            val first = addresses?.firstOrNull()
            val locality = first?.locality ?: first?.subAdminArea ?: first?.adminArea
            val region = first?.adminArea ?: first?.countryCode
            when {
                !locality.isNullOrBlank() && !region.isNullOrBlank() && locality != region ->
                    "$locality, $region"
                !locality.isNullOrBlank() -> locality
                !region.isNullOrBlank() -> region
                else -> formatCoordFallback(latitude, longitude)
            }
        } catch (_: Exception) {
            formatCoordFallback(latitude, longitude)
        }
    }

    private fun formatCoordFallback(lat: Double, lng: Double): String {
        return String.format(Locale.US, "Area %.2f, %.2f", roundToCoarse(lat), roundToCoarse(lng))
    }
}
