package org.rsmod.content.other.websiteapi

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.michaelbull.logging.InlineLogger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors

class WebsiteApiServices(
    val registrar: AccountRegistrar,
    val database: WebsiteDatabase,
    val accounts: AccountDatabase,
    val auth: AccountAuth,
    val tracker: TrackerDatabase,
    val reports: ReportsDatabase,
    val skills: () -> List<SkillInfo>,
    val status: () -> StatusView,
    val gameData: () -> GameDataIndex?,
)

class WebsiteApiServer(private val settings: WebsiteApiSettings, private val services: WebsiteApiServices) {
    private val logger = InlineLogger()
    private val mapper = jacksonObjectMapper()
    private val apiKey = settings.apiKey.toByteArray(StandardCharsets.UTF_8)
    private val registerAttempts = RateLimiter(ATTEMPT_LIMIT, ATTEMPT_WINDOW_MILLIS)
    private val accountsCreated = RateLimiter(CREATED_LIMIT, CREATED_WINDOW_MILLIS)
    private val reportLimiter = RateLimiter(REPORT_LIMIT, REPORT_WINDOW_MILLIS)

    private var server: HttpServer? = null

    fun start() {
        val http = HttpServer.create(InetSocketAddress(settings.bindAddress, settings.port), 0)
        http.executor =
            Executors.newFixedThreadPool(THREADS) {
                Thread(it, "website-api").apply { isDaemon = true }
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
            exchange.sendJson(401, mapOf("ok" to false, "message" to "Unauthorized"))
            return
        }
        val path = exchange.requestURI.path
        val get = exchange.requestMethod == "GET"
        val post = exchange.requestMethod == "POST"
        when {
            post && path == "/api/register" -> handleRegister(exchange)
            get && path == "/api/online" -> exchange.sendJson(200, services.database.online())
            get && path == "/api/status" -> exchange.sendJson(200, services.status())
            get && path == "/api/hiscores" -> handleHiscores(exchange)
            get && path == "/api/hiscores/player" -> handlePlayer(exchange)
            get && path == "/api/gains" -> handleGains(exchange)
            get && path == "/api/gains/player" -> handlePlayerGains(exchange)
            get && path == "/api/items" -> handleSearch(exchange) { data, q -> data.searchItems(q, SEARCH_LIMIT) }
            get && path == "/api/monsters" -> handleSearch(exchange) { data, q -> data.searchMonsters(q, SEARCH_LIMIT) }
            get && path.startsWith("/api/items/") -> handleDetail(exchange, path) { data, id -> data.item(id) }
            get && path.startsWith("/api/monsters/") -> handleDetail(exchange, path) { data, id -> data.monster(id) }
            get && path == "/api/reports/categories" -> exchange.sendJson(200, ReportRules.CATEGORIES)
            post && path == "/api/account/login" -> handleLogin(exchange)
            post && path == "/api/account/verify" -> handleVerify(exchange)
            post && path == "/api/account/logout" -> {
                services.auth.logout(exchange.accountToken())
                exchange.sendJson(200, AuthResponse(ok = true))
            }
            get && path == "/api/account" -> withAccount(exchange) { session ->
                val view = services.accounts.overview(session.accountId)
                if (view == null) unauthorized(exchange) else exchange.sendJson(200, view)
            }
            post && path == "/api/account/password" -> handlePassword(exchange)
            post && path == "/api/account/2fa/begin" -> withAccount(exchange) { session ->
                val info = services.auth.beginEnrolment(session)
                if (info == null) {
                    exchange.sendJson(400, AuthResponse(ok = false, message = "Your authenticator is already set up."))
                } else {
                    exchange.sendJson(200, AuthResponse(ok = true, secret = info.secret, otpauthUri = info.otpauthUri))
                }
            }
            post && path == "/api/account/2fa/confirm" -> withAccount(exchange) { session ->
                val code = exchange.readJson<CodeRequest>().code
                if (services.auth.confirmEnrolment(session, code)) {
                    exchange.sendJson(200, AuthResponse(ok = true, message = "Authenticator turned on."))
                } else {
                    exchange.sendJson(400, AuthResponse(ok = false, message = WRONG_CODE))
                }
            }
            post && path == "/api/account/2fa/disable" -> withAccount(exchange) { session ->
                val code = exchange.readJson<CodeRequest>().code
                if (services.auth.disableTwoFactor(session, code)) {
                    exchange.sendJson(200, AuthResponse(ok = true, message = "Authenticator removed."))
                } else {
                    exchange.sendJson(400, AuthResponse(ok = false, message = WRONG_CODE))
                }
            }
            post && path == "/api/account/discord" -> handleDiscordLink(exchange)
            post && path == "/api/account/discord/unlink" -> withAccount(exchange) { session ->
                services.accounts.unlinkDiscord(session.accountId)
                exchange.sendJson(200, AuthResponse(ok = true, message = "Discord unlinked."))
            }
            post && path == "/api/reports" -> handleReport(exchange)
            else -> exchange.sendJson(404, mapOf("ok" to false, "message" to "Not found"))
        }
    }

    private fun handleRegister(exchange: HttpExchange) {
        val client = exchange.clientIp()
        if (registerAttempts.isLimited(client) || accountsCreated.isLimited(client)) {
            exchange.sendJson(
                429,
                RegisterResult(
                    ok = false,
                    code = ErrorCode.RATE_LIMITED,
                    message = "Too many sign-ups from your network. Please try again later.",
                ),
            )
            return
        }
        registerAttempts.record(client)
        val request = exchange.readJson<RegisterRequest>()
        val result = services.registrar.register(request)
        if (result.ok) {
            accountsCreated.record(client)
            logger.info { "Website registered account '${result.accountName}' from $client" }
        }
        exchange.sendJson(if (result.ok) 201 else 400, result)
    }

    private fun handleHiscores(exchange: HttpExchange) {
        val query = exchange.query()
        val page = (query["page"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_PAGE)
        val skillParam = query["skill"]?.lowercase() ?: WebsiteDatabase.OVERALL
        val skillList = services.skills()
        val rows =
            if (skillParam == WebsiteDatabase.OVERALL) {
                services.database.overallPage(page, PAGE_SIZE)
            } else {
                val skill = skillList.firstOrNull { it.name.equals(skillParam, ignoreCase = true) }
                if (skill == null) {
                    exchange.sendJson(400, mapOf("ok" to false, "message" to "Unknown skill"))
                    return
                }
                services.database.skillPage(skill.id, page, PAGE_SIZE)
            }
        exchange.sendJson(200, HiscoresView(skillParam, page, PAGE_SIZE, rows, skillList))
    }

    private fun handlePlayer(exchange: HttpExchange) {
        val name = exchange.playerName() ?: return
        val view = services.database.player(name, services.skills())
        if (view == null) {
            exchange.sendJson(404, mapOf("ok" to false, "message" to "Player not found"))
        } else {
            exchange.sendJson(200, view)
        }
    }

    private fun handleGains(exchange: HttpExchange) {
        val query = exchange.query()
        val period = GainPeriod.parse(query["period"] ?: GainPeriod.Week.id)
        if (period == null) {
            exchange.sendJson(400, mapOf("ok" to false, "message" to "Unknown period"))
            return
        }
        val page = (query["page"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_PAGE)
        val skillParam = query["skill"]?.lowercase() ?: WebsiteDatabase.OVERALL
        val skillList = services.skills()
        val statId =
            if (skillParam == WebsiteDatabase.OVERALL) {
                null
            } else {
                skillList.firstOrNull { it.name.equals(skillParam, ignoreCase = true) }?.id
                    ?: run {
                        exchange.sendJson(400, mapOf("ok" to false, "message" to "Unknown skill"))
                        return
                    }
            }
        val rows = services.tracker.gainsPage(period, statId, page, PAGE_SIZE)
        exchange.sendJson(200, GainsView(period.id, skillParam, page, PAGE_SIZE, rows, skillList))
    }

    private fun handlePlayerGains(exchange: HttpExchange) {
        val name = exchange.playerName() ?: return
        val query = exchange.query()
        val skillParam = query["skill"]?.lowercase() ?: WebsiteDatabase.OVERALL
        val skillList = services.skills()
        val statId =
            if (skillParam == WebsiteDatabase.OVERALL) null
            else skillList.firstOrNull { it.name.equals(skillParam, ignoreCase = true) }?.id
        val days = (query["days"]?.toIntOrNull() ?: DEFAULT_HISTORY_DAYS).coerceIn(2, MAX_HISTORY_DAYS)
        val view = services.tracker.playerGains(name, skillList, statId, days)
        if (view == null) {
            exchange.sendJson(404, mapOf("ok" to false, "message" to "Player not found"))
        } else {
            exchange.sendJson(200, view)
        }
    }

    private fun <T> handleSearch(exchange: HttpExchange, search: (GameDataIndex, String) -> List<T>) {
        val data = services.gameData() ?: return exchange.notReady()
        val q = exchange.query()["q"]?.trim().orEmpty().take(MAX_SEARCH_LENGTH)
        exchange.sendJson(200, search(data, q))
    }

    private fun <T : Any> handleDetail(exchange: HttpExchange, path: String, find: (GameDataIndex, Int) -> T?) {
        val data = services.gameData() ?: return exchange.notReady()
        val id = path.substringAfterLast('/').toIntOrNull()
        val found = id?.let { find(data, it) }
        if (found == null) {
            exchange.sendJson(404, mapOf("ok" to false, "message" to "Not found"))
        } else {
            exchange.sendJson(200, found)
        }
    }

    private fun handleLogin(exchange: HttpExchange) {
        val request = exchange.readJson<LoginRequest>()
        val response =
            when (val result = services.auth.login(request.username, request.password, exchange.clientIp())) {
                is AccountAuth.LoginResult.SignedIn -> AuthResponse(ok = true, token = result.token)
                is AccountAuth.LoginResult.NeedCode -> AuthResponse(ok = true, challenge = result.challenge)
                AccountAuth.LoginResult.Invalid -> AuthResponse(ok = false, message = "Wrong username or password.")
                is AccountAuth.LoginResult.Locked -> AuthResponse(ok = false, message = lockedMessage(result.remainingMillis))
            }
        exchange.sendJson(if (response.ok) 200 else 400, response)
    }

    private fun handleVerify(exchange: HttpExchange) {
        val request = exchange.readJson<CodeRequest>()
        val response =
            when (val result = services.auth.verify(request.challenge, request.code, exchange.clientIp())) {
                is AccountAuth.VerifyResult.Success -> AuthResponse(ok = true, token = result.token)
                is AccountAuth.VerifyResult.Invalid ->
                    AuthResponse(
                        ok = false,
                        restart = result.restart,
                        message = if (result.restart) "Too many wrong codes. Sign in again." else WRONG_CODE,
                    )
                AccountAuth.VerifyResult.Expired ->
                    AuthResponse(ok = false, restart = true, message = "That sign-in expired. Sign in again.")
                is AccountAuth.VerifyResult.Locked ->
                    AuthResponse(ok = false, restart = true, message = lockedMessage(result.remainingMillis))
            }
        exchange.sendJson(if (response.ok) 200 else 400, response)
    }

    private fun handlePassword(exchange: HttpExchange) {
        val token = exchange.accountToken() ?: return unauthorized(exchange)
        val request = exchange.readJson<PasswordRequest>()
        val response =
            when (val result = services.auth.changePassword(token, request.current, request.next, exchange.clientIp())) {
                AccountAuth.ChangeResult.Changed ->
                    AuthResponse(ok = true, message = "Password changed. Other sign-ins have been ended.")
                AccountAuth.ChangeResult.WrongPassword ->
                    AuthResponse(ok = false, message = "Your current password is wrong.")
                AccountAuth.ChangeResult.SignedOut -> return unauthorized(exchange)
                is AccountAuth.ChangeResult.Rejected -> AuthResponse(ok = false, message = result.message)
                is AccountAuth.ChangeResult.Locked -> AuthResponse(ok = false, message = lockedMessage(result.remainingMillis))
            }
        exchange.sendJson(if (response.ok) 200 else 400, response)
    }

    private fun handleDiscordLink(exchange: HttpExchange) {
        withAccount(exchange) { session ->
            val discordId = exchange.readJson<DiscordLinkRequest>().discordId.trim()
            if (!DISCORD_ID.matches(discordId)) {
                exchange.sendJson(400, AuthResponse(ok = false, message = "That Discord account couldn't be read."))
            } else if (!services.accounts.linkDiscord(session.accountId, discordId)) {
                exchange.sendJson(
                    400,
                    AuthResponse(ok = false, message = "That Discord account is already linked to another account."),
                )
            } else {
                exchange.sendJson(200, AuthResponse(ok = true, message = "Discord linked."))
            }
        }
    }

    private fun handleReport(exchange: HttpExchange) {
        withAccount(exchange) { session ->
            val key = "account:${session.accountId}"
            if (reportLimiter.isLimited(key)) {
                exchange.sendJson(429, AuthResponse(ok = false, message = "You've sent a lot of reports. Try again later."))
                return@withAccount
            }
            val request = exchange.readJson<ReportRequest>()
            val details = request.details.trim()
            ReportRules.problem(request.category, details)?.let {
                exchange.sendJson(400, AuthResponse(ok = false, message = it))
                return@withAccount
            }
            val offender = services.reports.characterName(request.offender.take(MAX_NAME_QUERY))
            if (offender == null) {
                exchange.sendJson(400, AuthResponse(ok = false, message = "There's no player with that name."))
                return@withAccount
            }
            reportLimiter.record(key)
            services.reports.submit(session.accountId, session.accountName, NewReport(offender, request.category, details), SOURCE_WEBSITE)
            exchange.sendJson(201, AuthResponse(ok = true, message = "Thanks, your report has been sent to the staff team."))
        }
    }

    private inline fun withAccount(exchange: HttpExchange, block: (AccountSession) -> Unit) {
        val session = services.auth.session(exchange.accountToken())
        if (session == null) {
            unauthorized(exchange)
            return
        }
        block(session)
    }

    private fun unauthorized(exchange: HttpExchange) {
        exchange.sendJson(401, AuthResponse(ok = false, message = "Please sign in again."))
    }

    private fun HttpExchange.notReady() {
        sendJson(503, mapOf("ok" to false, "message" to "The database is still loading. Try again shortly."))
    }

    private fun HttpExchange.playerName(): String? {
        val name = query()["name"]?.trim().orEmpty()
        if (name.isEmpty() || name.length > MAX_NAME_QUERY) {
            sendJson(400, mapOf("ok" to false, "message" to "Enter a player name"))
            return null
        }
        return name
    }

    private fun lockedMessage(remainingMillis: Long): String {
        val minutes = ((remainingMillis + 59_999) / 60_000).coerceAtLeast(1)
        return "Too many attempts. Try again in $minutes minute${if (minutes == 1L) "" else "s"}."
    }

    private fun HttpExchange.accountToken(): String? =
        requestHeaders.getFirst(ACCOUNT_TOKEN_HEADER)?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_TOKEN_LENGTH }

    private fun HttpExchange.hasApiKey(): Boolean {
        val provided = requestHeaders.getFirst(API_KEY_HEADER) ?: return false
        return MessageDigest.isEqual(provided.toByteArray(StandardCharsets.UTF_8), apiKey)
    }

    private fun HttpExchange.clientIp(): String =
        requestHeaders.getFirst(CLIENT_IP_HEADER)?.trim()?.take(MAX_IP_LENGTH)?.ifEmpty { null }
            ?: remoteAddress.address.hostAddress

    private fun HttpExchange.query(): Map<String, String> =
        requestURI.rawQuery
            ?.split('&')
            ?.mapNotNull { part ->
                val key = part.substringBefore('=')
                if (key.isEmpty()) return@mapNotNull null
                val value = part.substringAfter('=', "")
                decode(key) to decode(value)
            }
            ?.toMap()
            .orEmpty()

    private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

    private inline fun <reified T> HttpExchange.readJson(): T {
        val body = requestBody.use { it.readNBytes(MAX_BODY_BYTES + 1) }
        require(body.size <= MAX_BODY_BYTES) { "Request too large." }
        return mapper.readValue(body)
    }

    private fun HttpExchange.handleSafely(block: (HttpExchange) -> Unit) {
        try {
            block(this)
        } catch (_: IllegalArgumentException) {
            runCatching { sendJson(400, mapOf("ok" to false, "message" to "Bad request")) }
        } catch (_: JacksonException) {
            runCatching { sendJson(400, mapOf("ok" to false, "message" to "Bad request")) }
        } catch (e: Exception) {
            logger.warn(e) { "Website API request failed: $requestMethod ${requestURI.path}" }
            runCatching {
                sendJson(
                    500,
                    mapOf("ok" to false, "code" to ErrorCode.SERVER_ERROR, "message" to "Server error"),
                )
            }
        } finally {
            close()
        }
    }

    private fun HttpExchange.sendJson(status: Int, body: Any) {
        val bytes = mapper.writeValueAsBytes(body)
        responseHeaders.set("Content-Type", "application/json")
        responseHeaders.set("Cache-Control", "no-store")
        responseHeaders.set("X-Content-Type-Options", "nosniff")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val API_KEY_HEADER = "X-Api-Key"
        const val ACCOUNT_TOKEN_HEADER = "X-Account-Token"
        const val CLIENT_IP_HEADER = "X-Client-IP"
        const val SOURCE_WEBSITE = "website"
        const val WRONG_CODE = "That code isn't right. Check your authenticator app and try again."
        const val MAX_BODY_BYTES = 16 * 1024
        const val MAX_IP_LENGTH = 64
        const val MAX_TOKEN_LENGTH = 128
        const val MAX_NAME_QUERY = 32
        const val MAX_SEARCH_LENGTH = 50
        const val SEARCH_LIMIT = 50
        const val MAX_PAGE = 10_000
        const val PAGE_SIZE = 25
        const val DEFAULT_HISTORY_DAYS = 30
        const val MAX_HISTORY_DAYS = 365
        const val THREADS = 4
        const val ATTEMPT_LIMIT = 20
        const val ATTEMPT_WINDOW_MILLIS = 10 * 60_000L
        const val CREATED_LIMIT = 3
        const val CREATED_WINDOW_MILLIS = 60 * 60_000L
        const val REPORT_LIMIT = 5
        const val REPORT_WINDOW_MILLIS = 60 * 60_000L
        val DISCORD_ID = Regex("^[0-9]{5,25}$")
    }
}
