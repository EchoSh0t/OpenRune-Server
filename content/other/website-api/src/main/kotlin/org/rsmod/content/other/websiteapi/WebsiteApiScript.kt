package org.rsmod.content.other.websiteapi

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.or2.central.account.AccountNameAuthPolicy
import dev.or2.sql.OpenRuneSql
import jakarta.inject.Inject
import jakarta.inject.Provider
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.net.rsprot.player.SessionStart
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.totp.Totp
import org.rsmod.api.totp.laravel.TwoFactorSecretResolver
import org.rsmod.api.totp.useGeneratedSecret
import org.rsmod.api.totp.useSecret
import org.rsmod.game.GameUpdate
import org.rsmod.game.GameUpdate.Companion.isCountdown
import org.rsmod.game.LoginRestrictions
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WebsiteApiScript
@Inject
constructor(
    private val databaseConfig: Provider<DatabaseConfig>,
    private val centralLink: OpenRuneCentralWorldLink,
    private val playerList: PlayerList,
    private val update: GameUpdate,
    private val loginRestrictions: LoginRestrictions,
    private val serverConfig: ServerConfig,
    private val totp: Totp,
    private val secretResolver: TwoFactorSecretResolver,
) : PluginScript() {
    private val logger = InlineLogger()
    private val startedAt = System.nanoTime()
    private val status = AtomicReference(StatusView(true, 0, false, null, 0))
    private val gameData = AtomicReference<GameDataIndex?>()
    private val inGameReports = RateLimiter(IN_GAME_REPORT_LIMIT, IN_GAME_REPORT_WINDOW_MILLIS)

    private var server: WebsiteApiServer? = null
    private var background: ScheduledExecutorService? = null
    private var reports: ReportsDatabase? = null
    private var cycle = 0L

    private val skills: List<SkillInfo> by lazy {
        ServerCacheManager.getStats().keys.sorted().map { id ->
            val internal = RSCM.getReverseMapping(RSCMType.STAT, id).removePrefix("stat.")
            SkillInfo(id, internal.replaceFirstChar(Char::uppercaseChar))
        }
    }

    private val badWordRoots: Set<String> by lazy {
        AccountNameAuthPolicy.parseBadWordLines(
            readResource(BAD_WORDS_LOCAL) + "\n" + readResource(BAD_WORDS_CUSTOM)
        )
    }

    private val accountTotp =
        object : AccountTotp {
            override fun newSecret(): String = totp.useGeneratedSecret { String(it) }

            override fun verify(storedSecret: String, code: String): Boolean {
                val secret = secretResolver.resolveStoredSecret(storedSecret) ?: return false
                return useSecret(secret) { totp.verifyCode(it, code) }
            }
        }

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.Startup> { startServer() }
        onEvent<GameLifecycle.Shutdown> {
            server?.stop()
            background?.shutdownNow()
        }
        onEvent<GameLifecycle.EndCycle> {
            if (cycle++ % STATUS_EVERY_CYCLES == 0L) {
                status.set(buildStatus())
            }
        }
        onEvent<SessionStart> { recordLogin(player, session.inetAddress) }
        onCommand("report") {
            desc = "Report a player to staff: ::report name, what happened"
            cheat { report(player, args.joinToString(" ")) }
        }
    }

    private fun startServer() {
        val settings = WebsiteApiSettings(SETTINGS_FILE)
        try {
            settings.load()
        } catch (e: Exception) {
            logger.error(e) { "Could not read website API settings; the API will not start." }
            return
        }
        if (!settings.enabled) {
            return
        }
        val config = databaseConfig::get
        val tracker = TrackerDatabase(config)
        val accounts = AccountDatabase(config)
        val reportsDb = ReportsDatabase(config)
        reports = reportsDb
        guarded("create the website tables") { tracker.ensureSchema() }
        val services =
            WebsiteApiServices(
                registrar = AccountRegistrar(WebsiteDatabase(config), centralLink::passwordHasher, ::badWordRoots),
                database = WebsiteDatabase(config),
                accounts = accounts,
                auth = AccountAuth(accounts, centralLink::passwordHasher, accountTotp, serverConfig.name),
                tracker = tracker,
                reports = reportsDb,
                skills = ::skills,
                status = status::get,
                gameData = gameData::get,
            )
        startBackgroundJobs(tracker, reportsDb, settings.discordWebhookUrl)
        val http = WebsiteApiServer(settings, services)
        try {
            http.start()
            server = http
            logger.info {
                "Website API running at http://${settings.bindAddress}:${settings.port} " +
                    "(api_key is in $SETTINGS_FILE)"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Website API could not start on port ${settings.port}." }
        }
    }

    private fun startBackgroundJobs(tracker: TrackerDatabase, reportsDb: ReportsDatabase, webhookUrl: String) {
        val executor =
            Executors.newSingleThreadScheduledExecutor { Thread(it, "website-jobs").apply { isDaemon = true } }
        background = executor
        val webhook = webhookUrl.takeIf { it.startsWith("https://") }?.let(::DiscordWebhook)
        executor.scheduleWithFixedDelay(
            {
                guarded("take XP snapshots") {
                    tracker.ensureSchema()
                    tracker.snapshot()
                    reportsDb.pruneLoginIps()
                }
            },
            0,
            SNAPSHOT_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
        executor.execute {
            guarded("build the item and monster database") { gameData.set(GameDataIndex.build()) }
        }
        executor.scheduleWithFixedDelay(
            { guarded("check for level milestones") { announceMilestones(tracker, webhook) } },
            MILESTONE_INTERVAL_SECONDS,
            MILESTONE_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun announceMilestones(tracker: TrackerDatabase, webhook: DiscordWebhook?) {
        tracker.ensureSchema()
        val milestones = tracker.claimMilestones(skills.size)
        if (webhook == null) {
            return
        }
        for (milestone in milestones) {
            val skill = skills.firstOrNull { it.id == milestone.statId }
            val text =
                if (skill == null) {
                    "**${milestone.name}** has reached the maximum total level!"
                } else {
                    "**${milestone.name}** has reached level 99 ${skill.name}!"
                }
            webhook.post(text)
        }
    }

    private fun recordLogin(player: Player, ip: String) {
        val db = reports ?: return
        val accountId = player.accountId
        val characterId = player.characterId
        background?.execute {
            guarded("record a login address") {
                db.recordLogin(accountId, characterId, ip)
            }
        }
    }

    private fun report(player: Player, input: String) {
        val db = reports
        val executor = background
        if (db == null || executor == null) {
            player.mes("Reports aren't available right now.")
            return
        }
        val offender = input.substringBefore(',').trim()
        val details = input.substringAfter(',', "").trim()
        if (offender.isEmpty() || details.isEmpty()) {
            player.mes("Use ::report name, what happened - for example ::report Bob, scamming at the GE")
            return
        }
        if (offender.equals(player.displayName, ignoreCase = true)) {
            player.mes("You can't report yourself.")
            return
        }
        ReportRules.problem(ReportRules.IN_GAME_CATEGORY, details)?.let {
            player.mes(it)
            return
        }
        val key = "account:${player.accountId}"
        if (inGameReports.isLimited(key)) {
            player.mes("You've sent a lot of reports. Try again later.")
            return
        }
        inGameReports.record(key)
        val accountId = player.accountId
        val reporter = player.displayName
        executor.execute {
            guarded("store an in-game report") {
                val name = db.characterName(offender.take(MAX_NAME_LENGTH)) ?: offender
                db.submit(accountId, reporter, NewReport(name, ReportRules.IN_GAME_CATEGORY, details), SOURCE_IN_GAME)
            }
        }
        player.mes("Thanks, your report has been sent to the staff team.")
    }

    private fun buildStatus(): StatusView {
        val state = update.state
        return StatusView(
            online = true,
            players = playerList.count(),
            maintenance = loginRestrictions.maintenance,
            countdownSeconds = if (state.isCountdown()) state.current * CYCLE_MILLIS / 1000 else null,
            uptimeSeconds = (System.nanoTime() - startedAt) / 1_000_000_000,
        )
    }

    private inline fun guarded(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.warn(e) { "Website jobs: could not $action." }
        }
    }

    private fun readResource(path: String): String =
        OpenRuneSql::class.java.classLoader.getResourceAsStream(path)
            ?.use { InputStreamReader(it, StandardCharsets.UTF_8).readText() }
            .orEmpty()

    private companion object {
        const val BAD_WORDS_LOCAL = "profanity/bad_words_local.txt"
        const val BAD_WORDS_CUSTOM = "profanity/bad_words_custom.txt"
        const val STATUS_EVERY_CYCLES = 5L
        const val CYCLE_MILLIS = 600L
        const val SNAPSHOT_INTERVAL_MINUTES = 60L
        const val MILESTONE_INTERVAL_SECONDS = 60L
        const val IN_GAME_REPORT_LIMIT = 5
        const val IN_GAME_REPORT_WINDOW_MILLIS = 60 * 60_000L
        const val MAX_NAME_LENGTH = 32
        const val SOURCE_IN_GAME = "in-game"
        val SETTINGS_FILE: Path = Path.of(".data", "website-api.properties")
    }
}
