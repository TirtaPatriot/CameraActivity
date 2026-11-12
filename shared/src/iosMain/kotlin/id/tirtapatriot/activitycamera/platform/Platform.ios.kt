package id.tirtapatriot.activitycamera.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

actual object PlatformInfo {
    actual val name: String = "ios"
    actual val deviceModel: String =
        "${UIDevice.currentDevice.model} (iOS ${UIDevice.currentDevice.systemVersion})"
    actual val videoExtension: String = "mov"
}

actual fun mediaDirectory(): String {
    val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    return "$documents/ActivityCamera"
}
