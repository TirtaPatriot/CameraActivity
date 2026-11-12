package id.tirtapatriot.activitycamera.server.admin

import id.tirtapatriot.activitycamera.model.Branding
import id.tirtapatriot.activitycamera.model.CapturePolicy
import id.tirtapatriot.activitycamera.model.ConnectPayload
import id.tirtapatriot.activitycamera.model.LocalizedText
import id.tirtapatriot.activitycamera.model.MediaType
import id.tirtapatriot.activitycamera.model.UserRole
import id.tirtapatriot.activitycamera.server.AuthService
import id.tirtapatriot.activitycamera.server.PasswordHasher
import id.tirtapatriot.activitycamera.server.ServerDeps
import id.tirtapatriot.activitycamera.server.TaskDb
import id.tirtapatriot.activitycamera.server.UserRecord
import id.tirtapatriot.activitycamera.server.blocking
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.html.ButtonType
import kotlinx.html.FormEncType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.checkBoxInput
import kotlinx.html.code
import kotlinx.html.dateInput
import kotlinx.html.div
import kotlinx.html.fileInput
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.numberInput
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.passwordInput
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PANEL_COOKIE = "ac_panel"
private const val LANG_COOKIE = "ac_lang"
private const val MAX_LOGO_BYTES = 1024 * 1024

private val usernamePattern = Regex("^[a-z0-9._-]{3,50}$")
private val colorPattern = Regex("^#[0-9A-Fa-f]{6}$")
private val json = Json { encodeDefaults = true }

private fun ApplicationCall.strings() = PanelStrings(request.cookies[LANG_COOKIE] ?: "id")

private fun ApplicationCall.isHttps() = request.origin.scheme == "https"

/** Base URL the phones should use, e.g. https://camera.example.org */
private fun ApplicationCall.publicBaseUrl(deps: ServerDeps): String {
    deps.config.publicUrl?.let { return it }
    val origin = request.origin
    val defaultPort = (origin.scheme == "https" && origin.serverPort == 443) || (origin.scheme == "http" && origin.serverPort == 80)
    return buildString {
        append(origin.scheme).append("://").append(origin.serverHost)
        if (!defaultPort) append(':').append(origin.serverPort)
    }
}

/** Signed-in admin, or null after redirecting to the login page. */
private suspend fun RoutingContext.panelAdmin(deps: ServerDeps): UserRecord? {
    val user = deps.auth.userFromPanelToken(call.request.cookies[PANEL_COOKIE])
    if (user == null) call.respondRedirect("/admin/login")
    return user
}

private suspend fun ServerDeps.branding(): Branding = blocking { settings.appConfig().branding }

fun Route.adminRoutes(deps: ServerDeps) {
    route("/admin") {

        get("/lang/{code}") {
            val code = call.parameters["code"].takeIf { it == "en" } ?: "id"
            call.response.cookies.append(
                Cookie(name = LANG_COOKIE, value = code, path = "/admin", maxAge = 365 * 24 * 3600, httpOnly = true),
            )
            call.respondRedirect(call.request.headers["Referer"]?.takeIf { "/admin" in it } ?: "/admin")
        }

        // ---- Sign in / out -------------------------------------------------
        get("/login") {
            call.loginPage(deps, error = false)
        }

        post("/login") {
            val form = call.receiveParameters()
            val token = deps.auth.loginPanel(form["username"].orEmpty(), form["password"].orEmpty())
            if (token == null) {
                call.loginPage(deps, error = true)
                return@post
            }
            call.response.cookies.append(
                Cookie(
                    name = PANEL_COOKIE,
                    value = token,
                    path = "/admin",
                    maxAge = AuthService.PANEL_SESSION.seconds.toInt(),
                    httpOnly = true,
                    secure = call.isHttps(),
                    extensions = mapOf("SameSite" to "Strict"),
                ),
            )
            call.respondRedirect("/admin")
        }

        post("/logout") {
            call.request.cookies[PANEL_COOKIE]?.let { deps.auth.revoke(it) }
            call.response.cookies.appendExpired(PANEL_COOKIE, path = "/admin")
            call.respondRedirect("/admin/login")
        }

        // ---- Captures ------------------------------------------------------
        get {
            panelAdmin(deps) ?: return@get
            val s = call.strings()
            val query = call.request.queryParameters
            val from = query["from"]?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val to = query["to"]?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val userId = query["userId"]?.toLongOrNull()
            val users = blocking { deps.users.list() }
            val rows = blocking { deps.captures.list(from, to, userId, limit = 200) }
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(deps.config.timeZone)

            call.respondPanel(s, deps.branding(), s.capturesTitle, PanelSection.CAPTURES) {
                form(action = "/admin", method = FormMethod.get, classes = "card row") {
                    label { +s.from; dateInput(name = "from") { value = from?.toString().orEmpty() } }
                    label { +s.to; dateInput(name = "to") { value = to?.toString().orEmpty() } }
                    label {
                        +s.officer
                        select {
                            name = "userId"
                            option { value = ""; +s.allOfficers }
                            users.forEach { user ->
                                option {
                                    value = user.id.toString()
                                    selected = user.id == userId
                                    +user.displayName
                                }
                            }
                        }
                    }
                    button(type = ButtonType.submit) { +s.filter }
                }
                if (rows.isEmpty()) {
                    p("muted") { +s.noCaptures }
                    return@respondPanel
                }
                div("table-wrap") {
                    table {
                        thead {
                            tr {
                                th { +s.time }; th { +s.officer }; th { +s.type }; th { +s.task }; th { +s.note }
                                th { +s.location }; th { +s.size }; th { +s.file }
                            }
                        }
                        tbody {
                            rows.forEach { row ->
                                tr {
                                    td { +formatter.format(Instant.ofEpochMilli(row.takenAtEpochMs)) }
                                    td {
                                        val name = row.userDisplayName
                                        if (name != null) {
                                            +name
                                            row.userCode?.let { span("muted") { +" ($it)" } }
                                        } else {
                                            span("badge off") { +s.anonymous }
                                            span("muted") { +" ${row.deviceModel.orEmpty()} · ${row.deviceId?.take(8).orEmpty()}" }
                                        }
                                    }
                                    td {
                                        +(if (row.mediaType == MediaType.PHOTO) s.photo else s.video)
                                        if (row.sharedAtEpochMs != null) { +" "; span("badge") { +s.shared } }
                                    }
                                    td { +(row.taskName ?: "—") }
                                    td { +(row.note ?: "") }
                                    td {
                                        val lat = row.latitude
                                        val lon = row.longitude
                                        if (lat != null && lon != null) {
                                            a(href = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=18/$lat/$lon", target = "_blank") {
                                                +s.map
                                            }
                                            if (row.isMockLocation) { +" "; span("badge warn") { +s.mockLocation } }
                                        } else {
                                            +"—"
                                        }
                                    }
                                    td { +formatSize(row.fileSize) }
                                    td { a(href = "/admin/captures/${row.id}/file", target = "_blank") { +s.open } }
                                }
                            }
                        }
                    }
                }
            }
        }

        get("/captures/{id}/file") {
            panelAdmin(deps) ?: return@get
            val id = call.parameters["id"]?.toLongOrNull()
            val file = id?.let { blocking { deps.captures.storagePathOf(it)?.let(deps.storage::resolve) } }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondFile(file)
        }

        // ---- Officers ------------------------------------------------------
        get("/users") {
            panelAdmin(deps) ?: return@get
            call.usersPage(deps, message = call.request.queryParameters["msg"], isError = false)
        }

        post("/users") {
            panelAdmin(deps) ?: return@post
            val s = call.strings()
            val form = call.receiveParameters()
            val username = form["username"].orEmpty().trim().lowercase()
            val displayName = form["displayName"].orEmpty().trim()
            val password = form["password"].orEmpty()
            val error = when {
                !usernamePattern.matches(username) -> s.errUsername
                displayName.isEmpty() -> s.errDisplayName
                password.length < 8 -> s.errPassword
                blocking { deps.users.findByUsername(username) } != null -> s.errDuplicate
                else -> null
            }
            if (error != null) return@post call.usersPage(deps, error, isError = true)
            val hash = blocking { PasswordHasher.hash(password) }
            blocking {
                deps.users.create(username, displayName, form.userCode(), form.role(), hash)
            }
            call.respondRedirect("/admin/users?msg=saved")
        }

        post("/users/{id}") {
            val admin = panelAdmin(deps) ?: return@post
            val s = call.strings()
            val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val displayName = form["displayName"].orEmpty().trim()
            val role = form.role()
            when {
                displayName.isEmpty() -> return@post call.usersPage(deps, s.errDisplayName, isError = true)
                id == admin.id && role != UserRole.ADMIN -> return@post call.usersPage(deps, s.errSelf, isError = true)
            }
            blocking { deps.users.update(id, displayName, form.userCode(), role) }
            call.respondRedirect("/admin/users?msg=saved")
        }

        post("/users/{id}/active") {
            val admin = panelAdmin(deps) ?: return@post
            val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
            val active = call.receiveParameters()["active"] == "true"
            if (id == admin.id && !active) return@post call.usersPage(deps, call.strings().errSelf, isError = true)
            blocking { deps.users.setActive(id, active) }
            call.respondRedirect("/admin/users?msg=saved")
        }

        post("/users/{id}/password") {
            panelAdmin(deps) ?: return@post
            val s = call.strings()
            val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
            val password = call.receiveParameters()["password"].orEmpty()
            if (password.length < 8) return@post call.usersPage(deps, s.errPassword, isError = true)
            val hash = blocking { PasswordHasher.hash(password) }
            blocking {
                deps.users.setPassword(id, hash)
                deps.users.revokeAllTokens(id)
            }
            call.respondRedirect("/admin/users?msg=password")
        }

        // ---- Branding ------------------------------------------------------
        get("/branding") {
            panelAdmin(deps) ?: return@get
            call.brandingPage(deps, call.request.queryParameters["msg"]?.let { call.strings().saved }, isError = false)
        }

        post("/branding") {
            panelAdmin(deps) ?: return@post
            val s = call.strings()
            val form = call.receiveParameters()
            val color = form["primaryColor"].orEmpty()
            if (!colorPattern.matches(color)) return@post call.brandingPage(deps, s.errColor, isError = true)
            val current = deps.branding()
            val updated = current.copy(
                organizationName = form["organizationName"].orEmpty().trim().ifEmpty { current.organizationName },
                appTitle = LocalizedText(
                    id = form["appTitleId"].orEmpty().trim().ifEmpty { current.appTitle.id },
                    en = form["appTitleEn"].orEmpty().trim().ifEmpty { current.appTitle.en },
                ),
                primaryColor = color.uppercase(),
            )
            blocking { deps.settings.saveBranding(updated) }
            call.respondRedirect("/admin/branding?msg=saved")
        }

        post("/branding/logo") {
            panelAdmin(deps) ?: return@post
            val s = call.strings()
            var bytes: ByteArray? = null
            var contentType: String? = null
            call.receiveMultipart(formFieldLimit = MAX_LOGO_BYTES + 1L).forEachPart { part ->
                try {
                    if (part is PartData.FileItem && part.name == "logo") {
                        contentType = part.contentType?.withoutParameters()?.toString()
                        bytes = blocking { part.provider().toInputStream().use { it.readNBytes(MAX_LOGO_BYTES + 1) } }
                    }
                } finally {
                    part.dispose()
                }
            }
            val data = bytes
            val type = contentType
            if (data == null || data.isEmpty() || data.size > MAX_LOGO_BYTES || type !in setOf("image/png", "image/jpeg")) {
                return@post call.brandingPage(deps, s.errLogo, isError = true)
            }
            blocking {
                deps.storage.saveLogo(data)
                deps.settings.setLogo(type)
            }
            call.respondRedirect("/admin/branding?msg=saved")
        }

        post("/branding/logo/delete") {
            panelAdmin(deps) ?: return@post
            blocking {
                deps.storage.deleteLogo()
                deps.settings.setLogo(null)
            }
            call.respondRedirect("/admin/branding?msg=saved")
        }

        // ---- Task types ----------------------------------------------------
        get("/tasks") {
            panelAdmin(deps) ?: return@get
            call.tasksPage(deps, call.request.queryParameters["msg"]?.let { call.strings().saved }, isError = false)
        }

        post("/tasks") {
            panelAdmin(deps) ?: return@post
            val form = call.receiveParameters()
            val name = form["name"].orEmpty().trim()
            if (name.isEmpty() || name.length > 100) return@post call.tasksPage(deps, call.strings().errTaskName, isError = true)
            blocking {
                deps.tasks.create(name, form.taskIcon(), form["sortOrder"]?.toIntOrNull() ?: 0)
                deps.settings.invalidate()
            }
            call.respondRedirect("/admin/tasks?msg=saved")
        }

        post("/tasks/{id}") {
            panelAdmin(deps) ?: return@post
            val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
            val form = call.receiveParameters()
            val name = form["name"].orEmpty().trim()
            if (name.isEmpty() || name.length > 100) return@post call.tasksPage(deps, call.strings().errTaskName, isError = true)
            blocking {
                deps.tasks.update(id, name, form.taskIcon(), form["sortOrder"]?.toIntOrNull() ?: 0)
                deps.settings.invalidate()
            }
            call.respondRedirect("/admin/tasks?msg=saved")
        }

        post("/tasks/{id}/active") {
            panelAdmin(deps) ?: return@post
            val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.NotFound)
            val active = call.receiveParameters()["active"] == "true"
            blocking {
                deps.tasks.setActive(id, active)
                deps.settings.invalidate()
            }
            call.respondRedirect("/admin/tasks?msg=saved")
        }

        // ---- Capture rules -------------------------------------------------
        get("/policy") {
            panelAdmin(deps) ?: return@get
            call.policyPage(deps, call.request.queryParameters["msg"]?.let { call.strings().saved }, isError = false)
        }

        post("/policy") {
            panelAdmin(deps) ?: return@post
            val form = call.receiveParameters()
            val policy = CapturePolicy(
                allowPhoto = form.flag("allowPhoto"),
                allowVideo = form.flag("allowVideo"),
                allowAnonymousUpload = form.flag("allowAnonymousUpload"),
                requireSignIn = form.flag("requireSignIn"),
                requireTask = form.flag("requireTask"),
                requireLocation = form.flag("requireLocation"),
                recordAudio = form.flag("recordAudio"),
                maxVideoSeconds = form["maxVideoSeconds"]?.toIntOrNull() ?: -1,
                defaultLanguage = form["defaultLanguage"].takeIf { it == "en" } ?: "id",
            )
            if ((!policy.allowPhoto && !policy.allowVideo) || policy.maxVideoSeconds !in 5..3600) {
                return@post call.policyPage(deps, call.strings().errPolicy, isError = true)
            }
            blocking { deps.settings.saveCapturePolicy(policy) }
            call.respondRedirect("/admin/policy?msg=saved")
        }

        // ---- Connect -------------------------------------------------------
        get("/connect") {
            panelAdmin(deps) ?: return@get
            val s = call.strings()
            val baseUrl = call.publicBaseUrl(deps)
            val qr = QrSvg.render(json.encodeToString(ConnectPayload.serializer(), ConnectPayload(serverUrl = baseUrl)))
            call.respondPanel(s, deps.branding(), s.connectTitle, PanelSection.CONNECT) {
                div("card") {
                    p { +s.connectIntro }
                    div("qr") { unsafe { raw(qr) } }
                    p { +"${s.serverAddress}: "; code { +baseUrl } }
                }
            }
        }
    }
}

// ---- Pages that are re-rendered with validation messages ------------------

private suspend fun ApplicationCall.loginPage(deps: ServerDeps, error: Boolean) {
    val s = strings()
    respondPanel(
        s, deps.branding(), s.loginTitle, section = null, signedIn = false,
        status = if (error) HttpStatusCode.Unauthorized else HttpStatusCode.OK,
    ) {
        div("card login") {
            if (error) flash(s.loginFailed, isError = true)
            form(action = "/admin/login", method = FormMethod.post) {
                label { +s.username; textInput(name = "username") { required = true; attributes["autocomplete"] = "username" } }
                label { +s.password; passwordInput(name = "password") { required = true; attributes["autocomplete"] = "current-password" } }
                button(type = ButtonType.submit) { +s.signIn }
            }
        }
    }
}

private suspend fun ApplicationCall.usersPage(deps: ServerDeps, message: String?, isError: Boolean) {
    val s = strings()
    val users = blocking { deps.users.list() }
    val text = when (message) {
        "saved" -> s.saved
        "password" -> s.passwordChanged
        else -> message
    }
    respondPanel(s, deps.branding(), s.usersTitle, PanelSection.USERS, status = if (isError) HttpStatusCode.BadRequest else HttpStatusCode.OK) {
        flash(text, isError)
        div("table-wrap") {
            table {
                thead {
                    tr { th { +s.username }; th { +"${s.displayName} · ${s.userCode} · ${s.role}" }; th { +s.status }; th { +s.newPassword } }
                }
                tbody {
                    users.forEach { user ->
                        tr {
                            td { code { +user.username } }
                            td {
                                form(action = "/admin/users/${user.id}", method = FormMethod.post) {
                                    textInput(name = "displayName") { value = user.displayName; required = true }
                                    textInput(name = "userCode") { value = user.userCode.orEmpty(); placeholder = s.userCode }
                                    roleSelect(s, user.role)
                                    button(type = ButtonType.submit, classes = "secondary") { +s.save }
                                }
                            }
                            td {
                                span(if (user.isActive) "badge" else "badge off") { +(if (user.isActive) s.active else s.inactive) }
                                form(action = "/admin/users/${user.id}/active", method = FormMethod.post) {
                                    input(type = InputType.hidden, name = "active") { value = (!user.isActive).toString() }
                                    button(type = ButtonType.submit, classes = if (user.isActive) "danger" else "secondary") {
                                        +(if (user.isActive) s.deactivate else s.activate)
                                    }
                                }
                            }
                            td {
                                form(action = "/admin/users/${user.id}/password", method = FormMethod.post) {
                                    passwordInput(name = "password") { required = true; attributes["minlength"] = "8"; attributes["autocomplete"] = "new-password" }
                                    button(type = ButtonType.submit, classes = "secondary") { +s.setPassword }
                                }
                            }
                        }
                    }
                }
            }
        }
        h2 { +s.addUser }
        form(action = "/admin/users", method = FormMethod.post, classes = "card") {
            label { +s.username; textInput(name = "username") { required = true; attributes["autocomplete"] = "off" } }
            label { +s.displayName; textInput(name = "displayName") { required = true } }
            label { +s.userCode; textInput(name = "userCode") }
            label { +s.role; roleSelect(s, UserRole.OFFICER) }
            label { +s.password; passwordInput(name = "password") { required = true; attributes["minlength"] = "8"; attributes["autocomplete"] = "new-password" } }
            button(type = ButtonType.submit) { +s.create }
        }
    }
}

private fun kotlinx.html.FlowOrInteractiveOrPhrasingContent.roleSelect(s: PanelStrings, current: UserRole) {
    select {
        name = "role"
        option { value = UserRole.OFFICER.name; selected = current == UserRole.OFFICER; +s.roleOfficer }
        option { value = UserRole.ADMIN.name; selected = current == UserRole.ADMIN; +s.roleAdmin }
    }
}

private suspend fun ApplicationCall.brandingPage(deps: ServerDeps, message: String?, isError: Boolean) {
    val s = strings()
    val branding = deps.branding()
    val hasLogo = branding.logoVersion != null
    respondPanel(s, branding, s.brandingTitle, PanelSection.BRANDING, status = if (isError) HttpStatusCode.BadRequest else HttpStatusCode.OK) {
        flash(message, isError)
        p("muted") { +s.brandingIntro }
        form(action = "/admin/branding", method = FormMethod.post, classes = "card") {
            label { +s.organizationName; textInput(name = "organizationName") { value = branding.organizationName; required = true } }
            label { +s.appTitleId; textInput(name = "appTitleId") { value = branding.appTitle.id; required = true } }
            label { +s.appTitleEn; textInput(name = "appTitleEn") { value = branding.appTitle.en; required = true } }
            label { +s.primaryColor; input(type = InputType.color, name = "primaryColor") { value = branding.primaryColor } }
            button(type = ButtonType.submit) { +s.save }
        }
        h2 { +s.logo }
        div("card") {
            if (hasLogo) {
                img(src = "/api/v1/branding/logo?v=${branding.logoVersion}", alt = s.logo, classes = "logo-preview")
            } else {
                p("muted") { +s.noLogo }
            }
            form(action = "/admin/branding/logo", method = FormMethod.post, encType = FormEncType.multipartFormData, classes = "row") {
                label {
                    +s.logoHint
                    fileInput(name = "logo") { accept = "image/png,image/jpeg"; required = true }
                }
                button(type = ButtonType.submit) { +s.uploadLogo }
            }
            if (hasLogo) {
                form(action = "/admin/branding/logo/delete", method = FormMethod.post) {
                    button(type = ButtonType.submit, classes = "danger") { +s.removeLogo }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.tasksPage(deps: ServerDeps, message: String?, isError: Boolean) {
    val s = strings()
    val tasks = blocking { deps.tasks.listAll() }
    respondPanel(s, deps.branding(), s.tasksTitle, PanelSection.TASKS, status = if (isError) HttpStatusCode.BadRequest else HttpStatusCode.OK) {
        flash(message, isError)
        p("muted") { +s.tasksIntro }
        if (tasks.isEmpty()) {
            p("muted") { +s.noTasks }
        } else {
            div("table-wrap") {
                table {
                    thead { tr { th { +"${s.taskName} · ${s.icon} · ${s.order}" }; th { +s.status } } }
                    tbody {
                        tasks.forEach { task ->
                            tr {
                                td {
                                    form(action = "/admin/tasks/${task.id}", method = FormMethod.post) {
                                        textInput(name = "name") { value = task.name; required = true; attributes["maxlength"] = "100" }
                                        iconSelect(s, task.icon)
                                        numberInput(name = "sortOrder") { value = task.sortOrder.toString(); attributes["style"] = "width: 80px" }
                                        button(type = ButtonType.submit, classes = "secondary") { +s.save }
                                    }
                                }
                                td {
                                    span(if (task.isActive) "badge" else "badge off") { +(if (task.isActive) s.active else s.inactive) }
                                    form(action = "/admin/tasks/${task.id}/active", method = FormMethod.post) {
                                        input(type = InputType.hidden, name = "active") { value = (!task.isActive).toString() }
                                        button(type = ButtonType.submit, classes = if (task.isActive) "danger" else "secondary") {
                                            +(if (task.isActive) s.deactivate else s.activate)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        h2 { +s.addTask }
        form(action = "/admin/tasks", method = FormMethod.post, classes = "card row") {
            label { +s.taskName; textInput(name = "name") { required = true; attributes["maxlength"] = "100" } }
            label { +s.icon; iconSelect(s, "other") }
            label { +s.order; numberInput(name = "sortOrder") { value = ((tasks.maxOfOrNull { it.sortOrder } ?: 0) + 10).toString() } }
            button(type = ButtonType.submit) { +s.addTask }
        }
    }
}

private fun kotlinx.html.FlowOrInteractiveOrPhrasingContent.iconSelect(s: PanelStrings, current: String) {
    select {
        name = "icon"
        TaskDb.ICONS.forEach { key ->
            option { value = key; selected = key == current; +s.iconLabel(key) }
        }
    }
}

private fun Parameters.taskIcon() =
    this["icon"]?.takeIf { it in TaskDb.ICONS } ?: "other"

private suspend fun ApplicationCall.policyPage(deps: ServerDeps, message: String?, isError: Boolean) {
    val s = strings()
    val policy = blocking { deps.settings.appConfig().capture }
    respondPanel(s, deps.branding(), s.policyTitle, PanelSection.POLICY, status = if (isError) HttpStatusCode.BadRequest else HttpStatusCode.OK) {
        flash(message, isError)
        p("muted") { +s.policyIntro }
        form(action = "/admin/policy", method = FormMethod.post, classes = "card") {
            h2 { +s.accessTitle }
            checkbox("allowAnonymousUpload", s.allowAnonymousUpload, policy.allowAnonymousUpload)
            checkbox("requireSignIn", s.requireSignIn, policy.requireSignIn)
            checkbox("requireTask", s.requireTask, policy.requireTask)
            h2 { +s.policyTitle }
            checkbox("allowPhoto", s.allowPhoto, policy.allowPhoto)
            checkbox("allowVideo", s.allowVideo, policy.allowVideo)
            checkbox("requireLocation", s.requireLocation, policy.requireLocation)
            checkbox("recordAudio", s.recordAudio, policy.recordAudio)
            label {
                +s.maxVideoSeconds
                numberInput(name = "maxVideoSeconds") { value = policy.maxVideoSeconds.toString(); attributes["min"] = "5"; attributes["max"] = "3600" }
            }
            label {
                +s.defaultLanguage
                select {
                    name = "defaultLanguage"
                    option { value = "id"; selected = policy.defaultLanguage != "en"; +"Bahasa Indonesia" }
                    option { value = "en"; selected = policy.defaultLanguage == "en"; +"English" }
                }
            }
            button(type = ButtonType.submit) { +s.save }
        }
    }
}

private fun kotlinx.html.FORM.checkbox(name: String, text: String, checked: Boolean) {
    label("check") {
        checkBoxInput(name = name) {
            value = "true"
            this.checked = checked
        }
        +text
    }
}

private fun Parameters.flag(name: String) = this[name] == "true"

private fun Parameters.userCode() = this["userCode"]?.trim()?.takeIf { it.isNotEmpty() }

private fun Parameters.role() = if (this["role"] == UserRole.ADMIN.name) UserRole.ADMIN else UserRole.OFFICER

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(Locale.ROOT, bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(Locale.ROOT, bytes / 1024.0)
    else -> "$bytes B"
}
