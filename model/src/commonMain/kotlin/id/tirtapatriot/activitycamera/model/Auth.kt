package id.tirtapatriot.activitycamera.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole { ADMIN, OFFICER }

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceName: String,
)

@Serializable
data class UserProfile(
    val id: Long,
    val username: String,
    val displayName: String,
    /** Officer code / employee ID printed on the stamp. */
    val userCode: String?,
    val role: UserRole,
)

@Serializable
data class LoginResponse(
    /** Opaque bearer token for the Authorization header. */
    val token: String,
    val user: UserProfile,
)
