package id.tirtapatriot.activitycamera.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private class AndroidLocationProvider(private val context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val _fix = MutableStateFlow<GeoFix?>(null)
    override val fix: StateFlow<GeoFix?> = _fix.asStateFlow()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { _fix.value = it.toGeoFix() }
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun start() {
        if (!hasPermission()) return
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null && _fix.value == null) _fix.value = location.toGeoFix()
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun stop() {
        client.removeLocationUpdates(callback)
    }
}

private fun Location.toGeoFix() = GeoFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    altitude = if (hasAltitude()) altitude else null,
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider,
)

@Composable
actual fun rememberLocationProvider(): LocationProvider {
    val context = LocalContext.current
    return remember { AndroidLocationProvider(context.applicationContext) }
}
