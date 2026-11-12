package id.tirtapatriot.activitycamera.location

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitude: Double?,
    /** The OS reports this location as coming from a mock/simulated provider. Allowed, but recorded. */
    val isMock: Boolean = false,
)

/** GPS location, continuously updated while the camera screen is active. */
interface LocationProvider {
    val fix: StateFlow<GeoFix?>
    fun start()
    fun stop()
}

@Composable
expect fun rememberLocationProvider(): LocationProvider
