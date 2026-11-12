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
    /** Activity description typed by the officer. */
    val note: String? = null,
    /** Officer code / employee ID of the signed-in account (the server re-checks it). */
    val userCode: String? = null,
    val deviceModel: String,
    val platform: String,
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
    val note: String?,
    val userCode: String?,
    val userDisplayName: String?,
    val storagePath: String,
    val fileSize: Long,
    val uploadedAtEpochMs: Long,
)

@Serializable
data class ErrorResponse(val error: String)

object ApiContract {
    const val CONFIG = "/api/v1/config"
    const val LOGO = "/api/v1/branding/logo"
    const val LOGIN = "/api/v1/auth/login"
    const val LOGOUT = "/api/v1/auth/logout"
    const val ME = "/api/v1/me"
    const val CAPTURES = "/api/v1/captures"
    const val PART_METADATA = "metadata"
    const val PART_FILE = "file"
}
