package org.rsmod.content.other.webpanel

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.michaelbull.logging.InlineLogger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.or2.central.account.Rights
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Private admin API used by the website's `/admin` pages. Every request needs the shared API key;
 * staff requests also carry the session token issued by [StaffAuth]. The website passes the
 * visitor's address in `X-Client-IP` so lockouts apply per staff member's machine.
 */
class PanelHttpServer(
    private val port: Int,
    private val settings: PanelSettings,
    private val auth: StaffAuth,
    private val audit: PanelAudit,
    private val snapshot: () -> ServerView?,
    private val lookup: (String, StaffSession) -> PlayerLookupView?,
    private val submit: (ActionRequest, StaffSession) -> CompletableFuture<ActionResult>,
    private val staffDatabase: PanelStaffDatabase,
) {
    private val logger = InlineLogger()
    private val mapper = jacksonObjectMapper()
    private val apiKey = settings.apiKey.toByteArray(StandardCharsets.UTF_8)

    private var server: HttpServer? = null

    fun start() {
        val http = HttpServer.create(InetSocketAddress(settings.bindAddress, port), 0)
        http.executor =
            Executors.newFixedThreadPool(THREADS) {
                Thread(it, "admin-api").apply { isDaemon = true }
            }
        http.createContext("/") { exchange -> exchange.handleSafely(::route) }
        http.start()
        server = http
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun route(exchange: HttpExchange) {
        if (!exchange.hasApiKey()) {
            exchange.sendJson(401, ActionResult(false, "Missing or wrong API key."))
            return
        }
        val method = exchange.requestMethod
        when (val path = exchange.requestURI.path) {
            "/api/staff/login" -> if (method == "POST") handleLogin(exchange) else notFound(exchange)
            "/api/staff/challenge" -> if (method == "GET") handleChallenge(exchange) else notFound(exchange)
            "/api/staff/verify" -> if (method == "POST") handleVerify(exchange) else notFound(exchange)
            else -> routeAuthenticated(exchange, method, path)
        }
    }

    private fun routeAuthenticated(exchange: HttpExchange, method: String, path: String) {
        val staff = auth.session(exchange.staffToken())
        if (staff == null) {
            exchange.sendJson(401, ActionResult(false, "Not signed in."))
            return
        }
        when {
            method == "GET" && path == "/api/staff/session" ->
                exchange.sendJson(200, StaffView(staff.name, staff.rights.name, staff.isAdmin))
            method == "POST" && path == "/api/staff/logout" -> handleLogout(exchange, staff)
            method == "GET" && path == "/api/status" -> {
                val view = snapshot()
                if (view == null) {
                    exchange.sendJson(503, ActionResult(false, "Server is still starting."))
                } else {
                    exchange.sendJson(200, view)
                }
            }
            method == "GET" && path == "/api/player" -> handleLookup(exchange, staff)
            method == "POST" && path == "/api/action" -> handleAction(exchange, staff)
            method == "GET" && path == "/api/reports" -> {
                val open = exchange.query("status") != "closed"
                exchange.sendJson(200, staffDatabase.reports(open))
            }
            method == "GET" && path == "/api/reports/count" ->
                exchange.sendJson(200, mapOf("open" to staffDatabase.openReportCount()))
            method == "GET" && path == "/api/search" -> handleSearch(exchange, staff)
            method == "POST" && path == "/api/staff/action" -> handleStaffAction(exchange, staff)
            method == "GET" && path == "/api/audit" ->
                adminOnly(exchange, staff) {
                    val filter = exchange.query("q")?.take(MAX_FILTER_LENGTH)
                    exchange.sendJson(200, audit.recent(filter, AUDIT_LIMIT))
                }
            method == "GET" && path == "/api/staff/members" ->
                adminOnly(exchange, staff) { exchange.sendJson(200, staffDatabase.staffMembers()) }
            else -> notFound(exchange)
        }
    }

    private inline fun adminOnly(exchange: HttpExchange, staff: StaffSession, block: () -> Unit) {
        if (!staff.isAdmin) {
            exchange.sendJson(403, ActionResult(false, "Only administrators can do that."))
            return
        }
        block()
    }

    private fun handleSearch(exchange: HttpExchange, staff: StaffSession) {
        val query = exchange.query("q")?.trim().orEmpty()
        if (query.length < MIN_SEARCH_LENGTH || query.length > MAX_FILTER_LENGTH) {
            exchange.sendJson(400, ActionResult(false, "Search for at least $MIN_SEARCH_LENGTH characters."))
            return
        }
        audit.record(staff.name, exchange.clientIp(), "searched for \"${query.take(MAX_FILTER_LENGTH)}\"")
        exchange.sendJson(200, staffDatabase.search(query, includeIps = staff.isAdmin))
    }

    private fun handleStaffAction(exchange: HttpExchange, staff: StaffSession) {
        val request = exchange.readJson<StaffRequest>()
        val result =
            when (request.type) {
                "note" -> addNote(request, staff)
                "closeReport" -> closeReport(request, staff)
                "setRights" -> if (staff.isAdmin) setRights(request, staff) else adminRequired()
                "resetTwoFactor" -> if (staff.isAdmin) resetTwoFactor(request, staff) else adminRequired()
                else -> ActionResult(false, "Unknown action '${request.type}'.")
            }
        audit.record(staff.name, exchange.clientIp(), "${request.describe()} -> ${result.message}")
        exchange.sendJson(if (result.ok) 200 else 400, result)
    }

    private fun addNote(request: StaffRequest, staff: StaffSession): ActionResult {
        val body = request.text?.trim().orEmpty()
        if (body.isEmpty() || body.length > MAX_NOTE_LENGTH) {
            return ActionResult(false, "Notes must be between 1 and $MAX_NOTE_LENGTH characters.")
        }
        val name = request.player?.trim().orEmpty()
        if (!staffDatabase.addNote(name, staff.name, body)) {
            return ActionResult(false, "No character named '$name'.")
        }
        return ActionResult(true, "Note added.")
    }

    private fun closeReport(request: StaffRequest, staff: StaffSession): ActionResult {
        val id = request.id ?: return ActionResult(false, "Missing report id.")
        val comment = request.text?.trim()?.take(MAX_NOTE_LENGTH)?.ifEmpty { null }
        if (!staffDatabase.closeReport(id, staff.name, comment)) {
            return ActionResult(false, "That report is already closed.")
        }
        return ActionResult(true, "Report #$id closed.")
    }

    private fun setRights(request: StaffRequest, staff: StaffSession): ActionResult {
        val target = staffTarget(request, staff) { return it }
        val rights =
            Rights.entries.firstOrNull { it.name.equals(request.rights, ignoreCase = true) }
                ?: return ActionResult(false, "Unknown rank '${request.rights}'.")
        if (!staff.rights.isHigherThan(rights)) {
            return ActionResult(false, "You can only give ranks below your own.")
        }
        if (rights == target.rights) {
            return ActionResult(false, "${target.accountName} is already ${rankLabel(rights)}.")
        }
        staffDatabase.setRights(target.id, rights)
        auth.endSessions(target.id)
        return ActionResult(true, "${target.accountName} is now ${rankLabel(rights)}. It applies in game from their next login.")
    }

    private fun resetTwoFactor(request: StaffRequest, staff: StaffSession): ActionResult {
        val target = staffTarget(request, staff) { return it }
        staffDatabase.resetTwoFactor(target.id)
        auth.endSessions(target.id)
        return ActionResult(true, "Removed the authenticator from ${target.accountName}.")
    }

    private inline fun staffTarget(
        request: StaffRequest,
        staff: StaffSession,
        onError: (ActionResult) -> Nothing,
    ): PanelStaffDatabase.AccountRef {
        val name = request.player?.trim().orEmpty()
        val target = staffDatabase.findAccount(name) ?: onError(ActionResult(false, "No account or character named '$name'."))
        if (target.id == staff.accountId) {
            onError(ActionResult(false, "You can't change your own account here."))
        }
        if (!staff.rights.isHigherThan(target.rights)) {
            onError(ActionResult(false, "${target.accountName} has the same or a higher rank than you."))
        }
        return target
    }

    private fun rankLabel(rights: Rights): String =
        when (rights) {
            Rights.NONE -> "a regular player"
            Rights.MOD -> "a moderator"
            Rights.ADMINISTRATOR -> "an administrator"
        }

    private fun adminRequired() = ActionResult(false, "Only administrators can do that.")

    private fun StaffRequest.describe(): String =
        buildList {
                add("staff-action=$type")
                player?.let { add("player=$it") }
                rights?.let { add("rights=$it") }
                id?.let { add("id=$it") }
                text?.let { add("text=\"${it.take(80)}\"") }
            }
            .joinToString(" ")

    private fun handleLogin(exchange: HttpExchange) {
        val request = exchange.readJson<StaffLoginRequest>()
        val client = exchange.clientIp()
        val name = request.username.trim().lowercase()
        val view =
            when (val result = auth.login(request.username, request.password, client)) {
                is StaffAuth.LoginResult.NeedCode ->
                    StaffLoginView(true, "Enter your authenticator code.", "code", result.challenge)
                is StaffAuth.LoginResult.NeedEnrolment ->
                    StaffLoginView(true, "Set up your authenticator.", "enrol", result.challenge)
                StaffAuth.LoginResult.Invalid -> {
                    audit.record(name, client, "failed staff login")
                    StaffLoginView(false, "Wrong username or password.")
                }
                StaffAuth.LoginResult.NotStaff -> {
                    audit.record(name, client, "staff login refused (no staff rights)")
                    StaffLoginView(false, "This account doesn't have staff access.")
                }
                is StaffAuth.LoginResult.Locked -> {
                    audit.record(name, client, "staff login blocked (locked out)")
                    StaffLoginView(false, lockedMessage(result.remainingMillis))
                }
            }
        exchange.sendJson(200, view)
    }

    private fun handleChallenge(exchange: HttpExchange) {
        val info = auth.challenge(exchange.query("id"))
        if (info == null) {
            exchange.sendJson(404, ActionResult(false, "Sign-in expired. Please start again."))
            return
        }
        exchange.sendJson(200, info)
    }

    private fun handleVerify(exchange: HttpExchange) {
        val request = exchange.readJson<StaffVerifyRequest>()
        val client = exchange.clientIp()
        val pending = auth.challenge(request.challenge)
        val enrolling = pending?.enrol == true
        val view =
            when (val result = auth.verify(request.challenge, request.code, client)) {
                is StaffAuth.VerifyResult.Success -> {
                    if (enrolling) {
                        audit.record(result.staff.name, client, "enabled two-factor authentication")
                    }
                    audit.record(result.staff.name, client, "staff login (${result.staff.rights.name})")
                    StaffVerifyView(true, "Signed in as ${result.staff.name}.", result.token)
                }
                is StaffAuth.VerifyResult.Invalid -> {
                    audit.record(pending?.accountName?.lowercase(), client, "wrong staff authenticator code")
                    val message =
                        if (result.restart) "Too many wrong codes. Please sign in again."
                        else "That code isn't right. Check your authenticator app and try again."
                    StaffVerifyView(false, message, restart = result.restart)
                }
                StaffAuth.VerifyResult.Expired ->
                    StaffVerifyView(false, "Sign-in expired. Please start again.", restart = true)
                is StaffAuth.VerifyResult.Locked ->
                    StaffVerifyView(false, lockedMessage(result.remainingMillis), restart = true)
            }
        exchange.sendJson(200, view)
    }

    private fun handleLogout(exchange: HttpExchange, staff: StaffSession) {
        auth.logout(exchange.staffToken())
        audit.record(staff.name, exchange.clientIp(), "staff logout")
        exchange.sendJson(200, ActionResult(true, "Signed out."))
    }

    private fun handleLookup(exchange: HttpExchange, staff: StaffSession) {
        val name = exchange.query("name")?.trim().orEmpty()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
            exchange.sendJson(400, ActionResult(false, "Enter a character name."))
            return
        }
        val view = lookup(name, staff)
        if (view == null) {
            exchange.sendJson(404, ActionResult(false, "No character named '$name'."))
            return
        }
        audit.record(staff.name, exchange.clientIp(), "looked up player ${view.name}")
        exchange.sendJson(200, view)
    }

    private fun handleAction(exchange: HttpExchange, staff: StaffSession) {
        val request = exchange.readJson<ActionRequest>()
        if (!staff.isAdmin && request.type !in MOD_ACTIONS) {
            exchange.sendJson(403, ActionResult(false, "Only administrators can do that."))
            return
        }
        val result =
            try {
                submit(request, staff).get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                ActionResult(ok = false, message = "Timed out waiting for the game tick.")
            }
        if (request.type !in READ_ONLY_ACTIONS) {
            audit.record(staff.name, exchange.clientIp(), "${request.describe()} -> ${result.message}")
        }
        exchange.sendJson(200, result)
    }

    private fun lockedMessage(remainingMillis: Long): String {
        val minutes = (remainingMillis / 60_000) + 1
        return "Too many failed attempts. Try again in $minutes minute(s)."
    }

    private fun notFound(exchange: HttpExchange) {
        exchange.sendJson(404, ActionResult(false, "Not found."))
    }

    private fun ActionRequest.describe(): String =
        buildList {
                add("action=$type")
                player?.let { add("player=$it") }
                item?.let { add("item=$it") }
                count?.let { add("count=$it") }
                if (x != null && z != null) add("coords=$x,$z,${level ?: 0}")
                enabled?.let { add("enabled=$it") }
                rate?.let { add("rate=$it") }
                mode?.let { add("mode=$it") }
                seconds?.let { add("seconds=$it") }
                days?.let { add("days=$it") }
                reason?.let { add("reason=\"${it.take(80)}\"") }
                text?.let { add("text=\"${it.take(80)}\"") }
            }
            .joinToString(" ")

    private fun HttpExchange.hasApiKey(): Boolean {
        val given = requestHeaders.getFirst(API_KEY_HEADER)?.toByteArray(StandardCharsets.UTF_8)
        return given != null && MessageDigest.isEqual(given, apiKey)
    }

    private fun HttpExchange.clientIp(): String =
        requestHeaders.getFirst(CLIENT_IP_HEADER)?.trim()?.take(MAX_IP_LENGTH)?.ifEmpty { null }
            ?: remoteAddress.address.hostAddress

    private fun HttpExchange.staffToken(): String? =
        requestHeaders.getFirst(STAFF_TOKEN_HEADER)?.trim()?.ifEmpty { null }

    private fun HttpExchange.query(name: String): String? =
        requestURI.rawQuery
            ?.split('&')
            ?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it[0] == name }
            ?.getOrNull(1)
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }

    private inline fun <reified T> HttpExchange.readJson(): T {
        val body = requestBody.use { it.readNBytes(MAX_BODY_BYTES + 1) }
        require(body.size <= MAX_BODY_BYTES) { "Request too large." }
        return mapper.readValue(body)
    }

    private fun HttpExchange.handleSafely(block: (HttpExchange) -> Unit) {
        try {
            block(this)
        } catch (e: JacksonException) {
            runCatching { sendJson(400, ActionResult(false, "Malformed request.")) }
        } catch (e: IllegalArgumentException) {
            runCatching { sendJson(400, ActionResult(false, e.message ?: "Bad request.")) }
        } catch (e: Exception) {
            logger.warn(e) { "Admin API request failed: ${requestMethod} ${requestURI.path}" }
            runCatching { sendJson(500, ActionResult(false, "Something went wrong on the server.")) }
        } finally {
            close()
        }
    }

    private fun HttpExchange.sendJson(status: Int, body: Any) {
        val bytes = mapper.writeValueAsBytes(body)
        responseHeaders.set("Content-Type", "application/json")
        responseHeaders.set("Cache-Control", "no-store")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val API_KEY_HEADER = "X-Api-Key"
        const val CLIENT_IP_HEADER = "X-Client-IP"
        const val STAFF_TOKEN_HEADER = "X-Staff-Token"
        const val ACTION_TIMEOUT_SECONDS = 3L
        const val MAX_BODY_BYTES = 16 * 1024
        const val MAX_NAME_LENGTH = 12
        const val MAX_IP_LENGTH = 64
        const val THREADS = 4
        const val AUDIT_LIMIT = 300
        const val MIN_SEARCH_LENGTH = 2
        const val MAX_FILTER_LENGTH = 64
        const val MAX_NOTE_LENGTH = 1000
        val READ_ONLY_ACTIONS = setOf("bans")
        val MOD_ACTIONS = setOf("kick", "ban", "unban", "mute", "unmute", "bans")
    }
}
