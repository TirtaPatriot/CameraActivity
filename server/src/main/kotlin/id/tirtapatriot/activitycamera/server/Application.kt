package id.tirtapatriot.activitycamera.server

import id.tirtapatriot.activitycamera.model.ErrorResponse
import id.tirtapatriot.activitycamera.model.UserRole
import id.tirtapatriot.activitycamera.server.admin.adminRoutes
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
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
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
    val database = Database(config).also { it.ping() }
    monitor.subscribe(ApplicationStopped) { database.close() }

    val users = UserDb(database.dataSource)
    ensureAdminExists(users, config)

    val tasks = TaskDb(database.dataSource)
    val deps = ServerDeps(
        config = config,
        storage = storage,
        captures = CaptureDb(database.dataSource, config.timeZone),
        users = users,
        settings = SettingsRepository(database.dataSource, tasks),
        tasks = tasks,
        auth = AuthService(users),
        oidc = config.oidc?.let(::OidcService),
    )

    // Behind Traefik/Dokploy: trust X-Forwarded-Proto/Host for the real scheme and host.
    install(XForwardedHeaders)
    install(CallLogging) {
        // never log query strings or bodies; tokens travel in headers
        filter { call -> !call.request.path().startsWith("/health") }
    }
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
        get("/") { call.respondRedirect("/admin") }
        get("/health") { call.respondText("ok") }
        apiRoutes(deps)
        oidcRoutes(deps)
        adminRoutes(deps)
    }
}

/**
 * First start: create the admin account from ADMIN_USERNAME / ADMIN_PASSWORD.
 * Once any active admin exists these variables are ignored (and can be removed).
 */
private fun Application.ensureAdminExists(users: UserDb, config: ServerConfig) {
    if (users.countActiveAdmins() > 0) return
    val username = config.bootstrapAdminUsername?.trim()?.lowercase()
    val password = config.bootstrapAdminPassword
    require(!username.isNullOrBlank() && password != null && password.length >= 8) {
        "No admin account exists yet: set ADMIN_USERNAME and ADMIN_PASSWORD (min. 8 characters) for the first start"
    }
    val existing = users.findByUsername(username)
    if (existing == null) {
        users.create(username, "Administrator", null, UserRole.ADMIN, PasswordHasher.hash(password))
    } else {
        users.update(existing.id, existing.displayName, existing.userCode, UserRole.ADMIN)
        users.setPassword(existing.id, PasswordHasher.hash(password))
        users.setActive(existing.id, true)
    }
    environment.log.info("Created admin account '$username' from ADMIN_USERNAME/ADMIN_PASSWORD")
}
