package id.tirtapatriot.activitycamera.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaType { PHOTO, VIDEO }

/**
 * Metadata of a single capture (photo/video). Created on the device at capture time
 * and sent as-is to the server as the "metadata" multipart field.
 */
@Serializable
data class CaptureMetadata(
    /** UUID generated on the device — idempotency key so re-uploads never duplicate. */
    val clientId: String,
    val mediaType: MediaType,
    val takenAtEpochMs: Long,
    /** Device IANA time zone, e.g. "Asia/Jakarta". */
    val timezone: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    /** True when the OS reported the location as coming from a mock/simulated provider. */
    val isMockLocation: Boolean = false,
    /** Optional free text typed by the officer. */
    val note: String? = null,
    /** Optional task picked by the officer (null = no task). */
    val taskId: Long? = null,
    val taskName: String? = null,
    /** Officer code of the signed-in account; the server re-checks it. Null when anonymous. */
    val userCode: String? = null,
    /** Random id generated once per app install; identifies anonymous uploads. */
    val deviceId: String? = null,
    val deviceModel: String,
    val platform: String,
    /** When the officer shared the capture (WhatsApp, group, …) before it was uploaded. */
    val sharedAtEpochMs: Long? = null,
)

@Serializable
data class UploadResponse(
    val serverId: Long,
    val clientId: String,
    val storagePath: String,
    /** true when this clientId was already received before (re-upload). */
    val duplicate: Boolean,
)

@Serializable
data class CaptureSummary(
    val id: Long,
    val clientId: String,
    val mediaType: MediaType,
    val takenAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val isMockLocation: Boolean,
    val note: String?,
    val taskName: String?,
    val userCode: String?,
    val userDisplayName: String?,
    val deviceId: String?,
    val deviceModel: String?,
    val storagePath: String,
    val fileSize: Long,
    val sharedAtEpochMs: Long?,
    val uploadedAtEpochMs: Long,
)

/** Sent when a capture is shared after it was already uploaded. */
@Serializable
data class ShareReport(val sharedAtEpochMs: Long)

/** Sent when the note of an already uploaded capture is changed on the phone. */
@Serializable
data class NoteReport(val note: String?)

@Serializable
data class ErrorResponse(val error: String)

object ApiContract {
    const val CONFIG = "/api/v1/config"
    const val LOGO = "/api/v1/branding/logo"
    const val LOGIN = "/api/v1/auth/login"
    const val LOGOUT = "/api/v1/auth/logout"

    /** Opened in the phone's browser: starts single sign-on (OIDC) through the server. */
    const val OIDC_START = "/auth/oidc/start"

    /** Callback registered at the identity provider (Casdoor, Keycloak, …). */
    const val OIDC_CALLBACK = "/auth/oidc/callback"

    /** POST [OidcTokenRequest]: exchanges the one-time code from the browser for a bearer token. */
    const val OIDC_TOKEN = "/api/v1/auth/oidc/token"

    /** Where the server sends the browser back to the app after single sign-on. */
    const val OIDC_APP_SCHEME = "id.tirtapatriot.activitycamera"
    const val OIDC_APP_REDIRECT = "$OIDC_APP_SCHEME://oidc"
    const val ME = "/api/v1/me"
    const val CAPTURES = "/api/v1/captures"
    const val PART_METADATA = "metadata"
    const val PART_FILE = "file"

    /** POST [ShareReport]: records that an uploaded capture was shared from the phone. */
    fun sharedPath(clientId: String) = "$CAPTURES/$clientId/shared"

    /** POST [NoteReport]: replaces the note of an uploaded capture. */
    fun notePath(clientId: String) = "$CAPTURES/$clientId/note"
}
