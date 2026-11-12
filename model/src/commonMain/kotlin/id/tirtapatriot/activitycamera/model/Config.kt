package id.tirtapatriot.activitycamera.model

import kotlinx.serialization.Serializable

/** Text in every supported UI language. */
@Serializable
data class LocalizedText(
    val id: String,
    val en: String,
) {
    fun forLanguage(code: String): String = if (code == "en") en else id
}

/** Organization identity shown inside the app (launcher name/icon are fixed at build time). */
@Serializable
data class Branding(
    val organizationName: String = "Activity Camera",
    val appTitle: LocalizedText = LocalizedText(id = "Kamera Kegiatan", en = "Activity Camera"),
    /** Hex color, e.g. "#1565C0". */
    val primaryColor: String = "#1565C0",
    /** Changes whenever a new logo is uploaded; null means no logo. */
    val logoVersion: Long? = null,
)

/** Rules the app enforces when capturing. */
@Serializable
data class CapturePolicy(
    val allowPhoto: Boolean = true,
    val allowVideo: Boolean = true,
    val requireLocation: Boolean = false,
    val requireNote: Boolean = false,
    val recordAudio: Boolean = true,
    val maxVideoSeconds: Int = 120,
    val stampShowOrganization: Boolean = true,
    val stampShowCoordinates: Boolean = true,
    val stampShowOfficer: Boolean = true,
    /** Default UI language for new installs: "id" or "en". */
    val defaultLanguage: String = "id",
)

/** Public configuration served by GET /api/v1/config. */
@Serializable
data class AppConfig(
    val branding: Branding = Branding(),
    val capture: CapturePolicy = CapturePolicy(),
)

/** Content of the connect QR code shown in the admin panel. */
@Serializable
data class ConnectPayload(
    val type: String = TYPE,
    val serverUrl: String,
) {
    companion object {
        const val TYPE = "activity-camera/connect"
    }
}
