package id.tirtapatriot.activitycamera.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24×24 grid, 2 px rounded stroke; tinted by Icon(). */
object AppIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Flash by lazy { icon("flash", "M13 2 4 14h7l-1 8 9-12h-7l1-8z") }
    val FlashAuto by lazy { icon("flash_auto", "M10.5 2 3.5 12.5h5.5l-1 8.5 6.5-10H9l1.5-9z", "M15.5 22l2.75-7.5L21 22", "M16.4 19.6h3.7") }
    val FlashOff by lazy { icon("flash_off", "M13 2 4 14h7l-1 8 9-12h-7l1-8z", "M3 3l18 18") }
    val Flip by lazy { icon("flip", "M3 12a9 9 0 0 1 15.5-6.2L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-15.5 6.2L3 16", "M3 21v-5h5") }
    val Grid by lazy { icon("grid", "M4 4h16v16H4z", "M9.33 4v16", "M14.67 4v16", "M4 9.33h16", "M4 14.67h16") }
    val Timer by lazy { icon("timer", "M21 13a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 9v4l2 2", "M10 2h4") }
    val User by lazy { icon("user", "M20 21a8 8 0 0 0-16 0", "M17 8a5 5 0 1 1-10 0 5 5 0 0 1 10 0z") }
    val Pin by lazy { icon("pin", "M12 22s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z", "M14.5 10a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z") }
    val Stamp by lazy { icon("stamp", "M3 6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M7 15h10", "M7 11h6") }
    val Share by lazy { icon("share", "M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7", "M16 6l-4-4-4 4", "M12 2v13") }
    val Back by lazy { icon("back", "M15 18l-6-6 6-6") }
    val ChevronRight by lazy { icon("chevron_right", "M9 18l6-6-6-6") }
    val Close by lazy { icon("close", "M18 6 6 18", "M6 6l12 12") }
    val Check by lazy { icon("check", "M20 6 9 17l-5-5") }
    val CloudUp by lazy { icon("cloud_up", "M17.5 19H7a5 5 0 1 1 1.1-9.9A6 6 0 0 1 19.5 11a4 4 0 0 1-2 8z", "M12 17v-5", "M9.5 14.5 12 12l2.5 2.5") }
    val CloudDone by lazy { icon("cloud_done", "M17.5 19H7a5 5 0 1 1 1.1-9.9A6 6 0 0 1 19.5 11a4 4 0 0 1-2 8z", "M9.5 14.5l2 2 3.5-3.5") }
    val CloudOff by lazy { icon("cloud_off", "M17.5 19H7a5 5 0 0 1-1.6-9.7", "M9 6.3A6 6 0 0 1 19.5 11a4 4 0 0 1 1.3 6.6", "M3 3l18 18") }
    val Task by lazy { icon("task", "M9 3h6v3H9z", "M15 4.5h2a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6.5a2 2 0 0 1 2-2h2", "M9 12h6", "M9 16h4") }
    val Video by lazy { icon("video", "M3 7a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M16 10l5-3v10l-5-3") }
    val Images by lazy { icon("images", "M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M21 15l-5-5L5 21", "M10.5 8.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0z") }
    val Alert by lazy { icon("alert", "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z", "M12 9v4", "M12 17h.01") }
    val Globe by lazy { icon("globe", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z", "M2 12h20", "M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20z") }
    val Server by lazy { icon("server", "M3 4h18v6H3z", "M3 14h18v6H3z", "M7 7h.01", "M7 17h.01") }
    val Logout by lazy { icon("logout", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9") }
    val Qr by lazy { icon("qr", "M3 3h7v7H3z", "M14 3h7v7h-7z", "M3 14h7v7H3z", "M14 14h3v3h-3z", "M21 17v4h-4") }
    val Sync by lazy { icon("sync", "M21 12a9 9 0 1 1-3-6.7L21 8", "M21 3v5h-5") }
    val Clock by lazy { icon("clock", "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0z", "M12 6v6l4 2") }
    val Settings by lazy {
        icon("settings", "M3 7h10", "M17 7h4", "M17 7a2 2 0 1 1-4 0 2 2 0 0 1 4 0z", "M3 17h4", "M11 17h10", "M11 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0z")
    }
    val Note by lazy { icon("note", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z") }
    val Plus by lazy { icon("plus", "M12 5v14", "M5 12h14") }
    val Play by lazy { icon("play", "M7 4v16l13-8z") }

    // Task type icons, keyed like the server's TaskDb.ICONS
    val Meter by lazy { icon("meter", "M4.9 19a9 9 0 1 1 14.2 0", "M12 14l4-4", "M13 14a1 1 0 1 1-2 0 1 1 0 0 1 2 0z") }
    val Inspect by lazy { icon("inspect", "M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0z", "M21 21l-4.3-4.3") }
    val Replace by lazy { icon("replace", "M7 4 3 8l4 4", "M3 8h14", "M17 20l4-4-4-4", "M21 16H7") }
    val Leak by lazy { icon("leak", "M12 22a7 7 0 0 0 7-7c0-4-7-13-7-13S5 11 5 15a7 7 0 0 0 7 7z") }
    val Seal by lazy { icon("seal", "M5 11h14v10H5z", "M8 11V7a4 4 0 0 1 8 0v4") }
    val Other by lazy { icon("other", "M6 12h.01", "M12 12h.01", "M18 12h.01") }

    fun task(key: String?): ImageVector = when (key) {
        "meter" -> Meter
        "inspect" -> Inspect
        "replace" -> Replace
        "leak" -> Leak
        "seal" -> Seal
        else -> Other
    }
}
