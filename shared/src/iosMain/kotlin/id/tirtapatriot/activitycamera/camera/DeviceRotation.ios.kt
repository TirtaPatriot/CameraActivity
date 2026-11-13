@file:OptIn(ExperimentalForeignApi::class)

package id.tirtapatriot.activitycamera.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** From the accelerometer (works with the screen rotation lock on, like the system Camera app). */
@Composable
actual fun rememberDeviceRotation(): Int {
    var rotation by remember { mutableIntStateOf(0) }
    val motion = remember { CMMotionManager() }
    DisposableEffect(motion) {
        if (motion.accelerometerAvailable) {
            motion.accelerometerUpdateInterval = 0.2
            motion.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
                data?.acceleration?.useContents {
                    // Ignore when lying flat: gravity is mostly along z.
                    if (sqrt(x * x + y * y) < 0.5) return@useContents
                    // Gravity: portrait y = -1 → 0°, right side down x = +1 → 90°, upside down → 180°, x = -1 → 270°.
                    val degrees = ((atan2(x, -y) * 180.0 / PI).toInt() + 360) % 360
                    val snapped = snapRotation(degrees, rotation)
                    if (snapped != rotation) rotation = snapped
                }
            }
        }
        onDispose { motion.stopAccelerometerUpdates() }
    }
    return rotation
}
