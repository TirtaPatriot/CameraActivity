@file:OptIn(ExperimentalForeignApi::class)

package id.tirtapatriot.activitycamera.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject

private class IosLocationProvider : LocationProvider {
    private val manager = CLLocationManager()
    private val _fix = MutableStateFlow<GeoFix?>(null)
    override val fix: StateFlow<GeoFix?> = _fix.asStateFlow()

    // CLLocationManager.delegate is weak, so keep a strong reference here
    private val delegate = LocationDelegate { _fix.value = it }

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 1.0
    }

    override fun start() {
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    override fun stop() {
        manager.stopUpdatingLocation()
    }

    private class LocationDelegate(private val onFix: (GeoFix) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            val (latitude, longitude) = location.coordinate.useContents { latitude to longitude }
            onFix(
                GeoFix(
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = location.horizontalAccuracy.takeIf { it >= 0 }?.toFloat(),
                    altitude = location.altitude,
                    // iOS 15+: set when the location comes from a simulator or a spoofing accessory/tool.
                    isMock = location.sourceInformation?.isSimulatedBySoftware == true,
                ),
            )
        }
    }
}

@Composable
actual fun rememberLocationProvider(): LocationProvider = remember { IosLocationProvider() }
