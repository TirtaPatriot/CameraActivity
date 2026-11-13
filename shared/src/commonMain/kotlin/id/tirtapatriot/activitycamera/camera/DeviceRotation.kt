package id.tirtapatriot.activitycamera.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * How the phone is physically held, from the motion sensor, even though the UI stays portrait:
 * 0 = portrait, 90 = left side up (turned clockwise), 180 = upside down, 270 = right side up.
 */
@Composable
expect fun rememberDeviceRotation(): Int

/** Snaps a sensor angle (0–359) to 0/90/180/270 with hysteresis, so it doesn't flicker near 45°. */
fun snapRotation(degrees: Int, current: Int): Int {
    val diff = abs(((degrees - current) % 360 + 540) % 360 - 180)
    if (diff <= 60) return current
    return (((degrees + 45) / 90) * 90) % 360
}

/** Rotation for icons so they stay upright; animates the short way round. */
@Composable
fun rememberUprightRotation(deviceRotation: Int): State<Float> {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(deviceRotation) {
        var delta = (-deviceRotation - target) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        target += delta
    }
    return animateFloatAsState(target, tween(250), label = "upright")
}

/** Degrees the camera icons are turned (see [rememberUprightRotation]). */
val LocalIconRotation = compositionLocalOf { 0f }
