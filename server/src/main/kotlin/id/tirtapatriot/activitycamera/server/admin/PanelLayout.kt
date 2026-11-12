package id.tirtapatriot.activitycamera.server.admin

import id.tirtapatriot.activitycamera.model.Branding
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.MAIN
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

enum class PanelSection(val path: String) {
    CAPTURES("/admin"),
    USERS("/admin/users"),
    BRANDING("/admin/branding"),
    POLICY("/admin/policy"),
    CONNECT("/admin/connect"),
}

private const val CSS = """
:root { --primary: #1565C0; --bg: #f5f6f8; --card: #fff; --text: #1d2330; --muted: #667085; --border: #dde1e7; --danger: #c62828; --ok: #2e7d32; }
* { box-sizing: border-box; }
body { margin: 0; font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); }
header { background: var(--primary); color: #fff; padding: 12px 20px; display: flex; flex-wrap: wrap; gap: 8px 20px; align-items: center; }
header .brand { font-weight: 600; margin-right: auto; }
header nav { display: flex; flex-wrap: wrap; gap: 4px; }
header a { color: #fff; text-decoration: none; padding: 6px 10px; border-radius: 6px; opacity: .85; }
header a.active, header a:hover { background: rgba(255,255,255,.18); opacity: 1; }
header form { margin: 0; }
header button { background: transparent; color: #fff; border: 1px solid rgba(255,255,255,.5); }
.lang a { padding: 4px 6px; font-size: 13px; }
main { max-width: 1100px; margin: 24px auto; padding: 0 16px; }
h1 { font-size: 22px; margin: 0 0 16px; }
h2 { font-size: 17px; margin: 24px 0 12px; }
.card { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 18px; margin-bottom: 16px; }
.muted { color: var(--muted); }
.flash { padding: 10px 14px; border-radius: 8px; margin-bottom: 16px; background: #e8f5e9; color: var(--ok); }
.flash.error { background: #fdecea; color: var(--danger); }
label { display: block; margin: 0 0 12px; font-weight: 500; }
label input[type=text], label input[type=password], label input[type=number], label input[type=date], label select { display: block; width: 100%; max-width: 420px; margin-top: 4px; }
label.check { display: flex; gap: 8px; align-items: center; font-weight: 400; }
input, select, button { font: inherit; padding: 7px 10px; border: 1px solid var(--border); border-radius: 6px; background: #fff; }
input[type=color] { padding: 2px; width: 56px; height: 36px; }
button, .button { background: var(--primary); color: #fff; border-color: var(--primary); cursor: pointer; text-decoration: none; display: inline-block; }
button.secondary { background: #fff; color: var(--text); border-color: var(--border); }
button.danger { background: #fff; color: var(--danger); border-color: var(--danger); }
.row { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; }
.row label { margin: 0; }
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; background: var(--card); }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); vertical-align: top; }
th { font-size: 13px; color: var(--muted); font-weight: 600; }
td form { display: inline-flex; gap: 6px; margin: 0 6px 6px 0; }
.badge { font-size: 12px; padding: 2px 8px; border-radius: 99px; background: #e8f5e9; color: var(--ok); }
.badge.off { background: #f1f1f1; color: var(--muted); }
.logo-preview { max-height: 80px; max-width: 240px; display: block; margin: 8px 0; }
.qr { background: #fff; display: inline-block; padding: 12px; border-radius: 10px; border: 1px solid var(--border); }
code { background: #eef1f5; padding: 2px 6px; border-radius: 4px; }
.login { max-width: 380px; margin: 80px auto; }
"""

private val colorPattern = Regex("^#[0-9A-Fa-f]{6}$")

/** Standard panel page: header with navigation, then [content]. Pass admin = false for the login page. */
suspend fun ApplicationCall.respondPanel(
    strings: PanelStrings,
    branding: Branding,
    pageTitle: String,
    section: PanelSection?,
    signedIn: Boolean = true,
    status: HttpStatusCode = HttpStatusCode.OK,
    content: MAIN.() -> Unit,
) {
    val primary = branding.primaryColor.takeIf { colorPattern.matches(it) } ?: "#1565C0"
    respondHtml(status) {
        attributes["lang"] = strings.language
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            meta(name = "robots", content = "noindex")
            title("$pageTitle · ${branding.organizationName}")
            style { unsafe { raw(CSS + ":root { --primary: $primary; }") } }
        }
        body {
            header {
                span("brand") { +"${branding.organizationName} — ${strings.panelTitle}" }
                if (signedIn) {
                    nav {
                        navLink(PanelSection.CAPTURES, strings.navCaptures, section)
                        navLink(PanelSection.USERS, strings.navUsers, section)
                        navLink(PanelSection.BRANDING, strings.navBranding, section)
                        navLink(PanelSection.POLICY, strings.navPolicy, section)
                        navLink(PanelSection.CONNECT, strings.navConnect, section)
                    }
                }
                div("lang") {
                    a(href = "/admin/lang/id") { +"ID" }
                    a(href = "/admin/lang/en") { +"EN" }
                }
                if (signedIn) {
                    form(action = "/admin/logout", method = FormMethod.post) {
                        button(type = ButtonType.submit) { +strings.logout }
                    }
                }
            }
            main {
                h1 { +pageTitle }
                content()
            }
        }
    }
}

private fun kotlinx.html.NAV.navLink(target: PanelSection, label: String, current: PanelSection?) {
    a(href = target.path, classes = if (target == current) "active" else null) { +label }
}

fun MAIN.flash(message: String?, isError: Boolean = false) {
    if (message.isNullOrBlank()) return
    div(if (isError) "flash error" else "flash") { +message }
}
