package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.ErrorResponse
import id.tirtapatriot.activitycamera.model.LoginRequest
import id.tirtapatriot.activitycamera.model.LoginResponse
import id.tirtapatriot.activitycamera.model.UploadResponse
import id.tirtapatriot.activitycamera.model.UserRole
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

private const val DEFAULT_LIST_LIMIT = 100

private val json = Json { ignoreUnknownKeys = true }
private val clientIdPattern = Regex("[A-Za-z0-9-]{8,64}")

/** Everything the routes need, created once in Application.module. */
class ServerDeps(
    val config: ServerConfig,
    val storage: MediaStorage,
    val captures: CaptureDb,
    val users: UserDb,
    val settings: SettingsRepository,
    val tasks: TaskDb,
    val auth: AuthService,
)

private suspend fun RoutingContext.requireUser(deps: ServerDeps): UserRecord? {
    val user = deps.auth.userFromBearer(call)
    if (user == null) call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not signed in"))
    return user
}

private suspend fun RoutingContext.requireAdmin(deps: ServerDeps): UserRecord? {
    val user = requireUser(deps) ?: return null
    if (user.role != UserRole.ADMIN) {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin only"))
        return null
    }
    return user
}

/** JSON API used by the mobile app. */
fun Route.apiRoutes(deps: ServerDeps) {

    // Public: branding + capture policy, needed before sign-in (login screen shows the branding).
    get(ApiContract.CONFIG) {
        call.respond(blocking { deps.settings.appConfig() })
    }

    get(ApiContract.LOGO) {
        val file = blocking { deps.storage.logo() }
        val contentType = blocking { deps.settings.logoContentType() }
        if (file == null || contentType == null) {
            return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("No logo"))
        }
        // The URL carries ?v=<logoVersion>, so it can be cached for a long time.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(blocking { file.readBytes() }, ContentType.parse(contentType))
    }

    post(ApiContract.LOGIN) {
        val request = call.receive<LoginRequest>()
        val result = deps.auth.loginMobile(request.username, request.password, request.deviceName)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid username or password"))
        val (token, user) = result
        call.respond(LoginResponse(token, user.toProfile()))
    }

    post(ApiContract.LOGOUT) {
        deps.auth.bearerToken(call)?.let { deps.auth.revoke(it) }
        call.respond(HttpStatusCode.NoContent)
    }

    get(ApiContract.ME) {
        val user = requireUser(deps) ?: return@get
        call.respond(user.toProfile())
    }

    route(ApiContract.CAPTURES) {

        // Multipart upload: "metadata" (CaptureMetadata JSON) followed by "file".
        post {
            // Signed-in officer, or an anonymous device when the admin allows it.
            val user = deps.auth.userFromBearer(call)
            if (user == null) {
                val policy = blocking { deps.settings.appConfig().capture }
                if (policy.requireSignIn || !policy.allowAnonymousUpload) {
                    return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Sign-in required"))
                }
            }

            var metadata: CaptureMetadata? = null
            var tempFile: File? = null
            var originalName: String? = null
            try {
                call.receiveMultipart(formFieldLimit = deps.config.maxUploadBytes).forEachPart { part ->
                    try {
                        when (part) {
                            is PartData.FormItem ->
                                if (part.name == ApiContract.PART_METADATA) {
                                    metadata = json.decodeFromString(CaptureMetadata.serializer(), part.value)
                                }

                            is PartData.FileItem ->
                                if (part.name == ApiContract.PART_FILE && tempFile == null) {
                                    originalName = part.originalFileName
                                    tempFile = deps.storage.saveTemp(part.provider(), deps.config.maxUploadBytes)
                                }

                            else -> Unit
                        }
                    } finally {
                        part.dispose()
                    }
                }

                // The officer code always comes from the signed-in account, never from the device.
                val meta = metadata?.copy(userCode = user?.userCode)
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Part 'metadata' is required"))
                val file = tempFile
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Part 'file' is required"))
                if (!clientIdPattern.matches(meta.clientId)) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid clientId"))
                }

                // Idempotent: re-uploading the same clientId never creates a duplicate.
                val existing = blocking { deps.captures.findByClientId(meta.clientId) }
                if (existing != null) return@post call.respond(HttpStatusCode.OK, existing)

                val fileSize = file.length()
                val relativePath = deps.storage.commit(file, meta, originalName)
                tempFile = null // moved already, don't delete in finally
                // Keep the task only when it still exists; its name is stored as shown on the device anyway.
                val taskTypeId = meta.taskId?.let { blocking { deps.tasks.findActive(it) }?.id }
                val id = blocking {
                    deps.captures.insert(meta, user?.id, taskTypeId, relativePath, fileSize, originalName)
                }
                call.respond(HttpStatusCode.Created, UploadResponse(id, meta.clientId, relativePath, duplicate = false))
            } finally {
                tempFile?.delete()
            }
        }

        // List (admin): ?from=2026-11-01&to=2026-11-30&userId=3&limit=100
        get {
            requireAdmin(deps) ?: return@get
            val query = call.request.queryParameters
            val from = query["from"]?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            val to = query["to"]?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            val userId = query["userId"]?.toLongOrNull()
            val limit = query["limit"]?.toIntOrNull() ?: DEFAULT_LIST_LIMIT
            call.respond(blocking { deps.captures.list(from, to, userId, limit) })
        }

        // Download the original file from the NAS (admin)
        get("{id}/file") {
            requireAdmin(deps) ?: return@get
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid id"))
            val file = blocking { deps.captures.storagePathOf(id)?.let(deps.storage::resolve) }
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("File not found"))
            call.respondFile(file)
        }
    }
}
