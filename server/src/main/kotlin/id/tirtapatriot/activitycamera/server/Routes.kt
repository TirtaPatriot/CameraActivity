package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.ApiContract
import id.tirtapatriot.activitycamera.model.CaptureMetadata
import id.tirtapatriot.activitycamera.model.ErrorResponse
import id.tirtapatriot.activitycamera.model.UploadResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

private const val DEFAULT_LIST_LIMIT = 100

private val json = Json { ignoreUnknownKeys = true }
private val clientIdPattern = Regex("[A-Za-z0-9-]{8,64}")

private suspend fun RoutingContext.isAuthorized(config: ServerConfig): Boolean {
    if (call.request.headers[ApiContract.HEADER_API_KEY] == config.apiKey) return true
    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid API key"))
    return false
}

fun Route.captureRoutes(config: ServerConfig, db: CaptureDb, storage: MediaStorage) {
    route(ApiContract.CAPTURES) {

        // Multipart upload: "metadata" (CaptureMetadata JSON) followed by "file".
        post {
            if (!isAuthorized(config)) return@post

            var metadata: CaptureMetadata? = null
            var tempFile: File? = null
            var originalName: String? = null
            try {
                call.receiveMultipart(formFieldLimit = config.maxUploadBytes).forEachPart { part ->
                    try {
                        when (part) {
                            is PartData.FormItem ->
                                if (part.name == ApiContract.PART_METADATA) {
                                    metadata = json.decodeFromString(CaptureMetadata.serializer(), part.value)
                                }

                            is PartData.FileItem ->
                                if (part.name == ApiContract.PART_FILE && tempFile == null) {
                                    originalName = part.originalFileName
                                    tempFile = storage.saveTemp(part.provider(), config.maxUploadBytes)
                                }

                            else -> Unit
                        }
                    } finally {
                        part.dispose()
                    }
                }

                val meta = metadata
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Part 'metadata' is required"))
                val file = tempFile
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Part 'file' is required"))
                if (!clientIdPattern.matches(meta.clientId)) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid clientId"))
                }

                // Idempotent: re-uploading the same clientId never creates a duplicate.
                val existing = withContext(Dispatchers.IO) { db.findByClientId(meta.clientId) }
                if (existing != null) return@post call.respond(HttpStatusCode.OK, existing)

                val fileSize = file.length()
                val relativePath = storage.commit(file, meta, originalName)
                tempFile = null // moved already, don't delete in finally
                val id = withContext(Dispatchers.IO) { db.insert(meta, relativePath, fileSize, originalName) }
                call.respond(HttpStatusCode.Created, UploadResponse(id, meta.clientId, relativePath, duplicate = false))
            } finally {
                tempFile?.delete()
            }
        }

        // List: ?from=2026-11-01&to=2026-11-30&userCode=123&limit=100
        get {
            if (!isAuthorized(config)) return@get
            val query = call.request.queryParameters
            val from = query["from"]?.let(LocalDate::parse)
            val to = query["to"]?.let(LocalDate::parse)
            val limit = query["limit"]?.toIntOrNull() ?: DEFAULT_LIST_LIMIT
            val rows = withContext(Dispatchers.IO) { db.list(from, to, query["userCode"], limit) }
            call.respond(rows)
        }

        // Download the original file from the NAS
        get("{id}/file") {
            if (!isAuthorized(config)) return@get
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid id"))
            val path = withContext(Dispatchers.IO) { db.storagePathOf(id) }
            val file = path?.let(storage::resolve)
                ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("File not found"))
            call.respondFile(file)
        }
    }
}
