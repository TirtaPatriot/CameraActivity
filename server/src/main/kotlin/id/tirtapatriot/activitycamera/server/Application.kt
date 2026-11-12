package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.time.format.DateTimeParseException

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: ServerConfig) {
    val storage = MediaStorage(config.storageRoot).also { it.checkWritable() }
    val db = CaptureDb(config).also { it.ping() }
    monitor.subscribe(ApplicationStopped) { db.close() }

    install(CallLogging)
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<PayloadTooLargeException> { call, cause ->
            call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse(cause.message ?: "File too large"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<DateTimeParseException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Date must be formatted as yyyy-MM-dd"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    routing {
        get("/health") { call.respondText("ok") }
        captureRoutes(config, db, storage)
    }
}
