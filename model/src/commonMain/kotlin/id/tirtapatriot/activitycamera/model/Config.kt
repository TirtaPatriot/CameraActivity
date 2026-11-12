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

/**
 * Rules set by the admin. Everything defaults to permissive: officers can use the camera
 * without an account or a task, and the admin tightens the rules once the habit has formed.
 */
@Serializable
data class CapturePolicy(
    val allowPhoto: Boolean = true,
    val allowVideo: Boolean = true,
    /** Captures may be uploaded without signing in (recorded per device). */
    val allowAnonymousUpload: Boolean = true,
    /** When true the app asks for sign-in before captures are uploaded. */
    val requireSignIn: Boolean = false,
    /** When true a task must be picked before taking a photo. */
    val requireTask: Boolean = false,
    /** Officers may sign in with a username + password (accounts made in the panel). Admins always can, in the panel. */
    val allowPasswordSignIn: Boolean = true,
    val requireLocation: Boolean = false,
    val recordAudio: Boolean = true,
    val maxVideoSeconds: Int = 120,
    /** Default UI language for new installs: "id" or "en". */
    val defaultLanguage: String = "id",
)

/** A kind of field work officers can pick (e.g. "Meter inspection"). Managed in the admin panel. */
@Serializable
data class TaskType(
    val id: Long,
    val name: String,
    /** Icon key understood by the app: meter, inspect, replace, leak, seal, other. */
    val icon: String = "other",
)

/** Public configuration served by GET /api/v1/config. */
@Serializable
data class AppConfig(
    val branding: Branding = Branding(),
    val capture: CapturePolicy = CapturePolicy(),
    val tasks: List<TaskType> = emptyList(),
    val auth: AuthOptions = AuthOptions(),
)

/** How officers can sign in; filled by the server from its configuration. */
@Serializable
data class AuthOptions(
    /** Single sign-on through the organization's OIDC provider is configured on the server. */
    val oidcEnabled: Boolean = false,
    /** Label for the SSO button, e.g. "Masuk dengan SSO Tirta Patriot"; null = app default. */
    val oidcLabel: String? = null,
    val passwordEnabled: Boolean = true,
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
