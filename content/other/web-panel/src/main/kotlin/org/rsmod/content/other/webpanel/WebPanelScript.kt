package org.rsmod.content.other.webpanel

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import jakarta.inject.Provider
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.jdbc.GameDatabase
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.net.central.OpenRuneCentralWorldLink
import org.rsmod.api.player.forceDisconnect
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.totp.Totp
import org.rsmod.api.totp.laravel.TwoFactorSecretResolver
import org.rsmod.api.totp.useGeneratedSecret
import org.rsmod.api.totp.useSecret
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.api.utils.system.SafeServiceExit
import org.rsmod.game.GameUpdate
import org.rsmod.game.GameUpdate.Companion.isCountdown
import org.rsmod.game.LoginRestrictions
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.objtx.TransactionResult
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WebPanelScript
@Inject
constructor(
    private val playerList: PlayerList,
    private val protectedAccess: ProtectedAccessLauncher,
    private val update: GameUpdate,
    private val loginRestrictions: LoginRestrictions,
    private val databaseConfig: Provider<DatabaseConfig>,
    private val centralLink: OpenRuneCentralWorldLink,
    private val totp: Totp,
    private val secretResolver: TwoFactorSecretResolver,
    private val serverConfig: ServerConfig,
    database: GameDatabase,
) : PluginScript() {
    private val logger = InlineLogger()

    private val bans = PanelBans(database)
    private val settings = PanelSettings(DATA_DIR.resolve("web-panel.properties"))
    private val supervised = System.getenv(SUPERVISED_ENV) == "1"

    private val snapshot = AtomicReference<ServerView?>()
    private val pendingActions = ConcurrentLinkedQueue<PendingAction>()
    private val tickDurations = LongArray(TICK_HISTORY)
    private val startedAt = System.nanoTime()

    private var cycle = 0L
    private var cycleStart = 0L
    private var server: PanelHttpServer? = null
    private var scheduledAction: ScheduledAction? = null
    private var exitRequested = false

    private val statNames: List<Pair<String, String>> by lazy {
        ServerCacheManager.getStats().keys.sorted().map { id ->
            val internal = RSCM.getReverseMapping(RSCMType.STAT, id)
            internal to internal.removePrefix("stat.")
        }
    }

    private val lookupSkills: List<Pair<Int, String>> by lazy {
        ServerCacheManager.getStats().keys.sorted().map { id ->
            val name = RSCM.getReverseMapping(RSCMType.STAT, id).removePrefix("stat.")
            id to name.replaceFirstChar(Char::uppercaseChar)
        }
    }

    private val staffTotp =
        object : StaffTotp {
            override fun newSecret(): String = totp.useGeneratedSecret { String(it) }

            override fun verify(storedSecret: String, code: String): Boolean {
                val secret = secretResolver.resolveStoredSecret(storedSecret) ?: return false
                return useSecret(secret) { totp.verifyCode(it, code) }
            }
        }

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.Startup> {
            loadSettings()
            startServer()
        }
        onEvent<GameLifecycle.Shutdown> { server?.stop() }
        onEvent<GameLifecycle.StartCycle> {
            cycleStart = System.nanoTime()
            processPendingActions()
        }
        onEvent<GameLifecycle.EndCycle> {
            tickDurations[(cycle % TICK_HISTORY).toInt()] = System.nanoTime() - cycleStart
            cycle++
            processScheduledExit()
            snapshot.set(buildSnapshot())
        }
        onPlayerLogin {
            player.xpRate = 1.0
            player.globalXpRate = settings.globalXpRate
        }
    }

    private fun loadSettings() {
        try {
            settings.load()
            settings.save()
        } catch (e: Exception) {
            logger.warn(e) { "Could not read web panel settings; using defaults." }
        }
        loginRestrictions.maintenance = settings.maintenance
    }

    private fun startServer() {
        val audit = PanelAudit(DATA_DIR.resolve("web-panel-audit.log"))
        val database = PanelDatabase(databaseConfig::get)
        val staffDatabase = PanelStaffDatabase(databaseConfig::get)
        try {
            staffDatabase.ensureSchema()
        } catch (e: Exception) {
            logger.warn(e) { "Could not create the website tables; notes, reports and IP history won't work." }
        }
        val auth = StaffAuth(database, centralLink::passwordHasher, staffTotp, serverConfig.name)
        val lookup = { name: String, staff: StaffSession -> lookupPlayer(database, staffDatabase, name, staff) }
        val http =
            PanelHttpServer(PORT, settings, auth, audit, snapshot::get, lookup, ::queueAction, staffDatabase)
        try {
            http.start()
            server = http
            logger.info {
                "Admin API running at http://${settings.bindAddress}:$PORT " +
                    "(api_key is in .data/web-panel.properties)"
            }
        } catch (e: Exception) {
            logger.warn(e) { "Admin API could not start on port $PORT." }
        }
    }

    private fun lookupPlayer(
        database: PanelDatabase,
        staffDatabase: PanelStaffDatabase,
        name: String,
        staff: StaffSession,
    ): PlayerLookupView? {
        val found = database.lookup(name, lookupSkills, ::itemName) ?: return null
        val history =
            try {
                staffDatabase.history(found.characterId, includeIps = staff.isAdmin)
            } catch (e: Exception) {
                logger.warn(e) { "Could not load staff history for ${found.name}" }
                null
            }
        val view = found.copy(history = history)
        val live = snapshot.get()?.players?.firstOrNull { it.name.equals(view.name, ignoreCase = true) }
        return if (live == null) {
            view
        } else {
            view.copy(online = true, x = live.x, z = live.z, level = live.level)
        }
    }

    private fun itemName(obj: String): String {
        val id = obj.toIntOrNull() ?: runCatching { obj.asRSCM(RSCMType.OBJ) }.getOrNull()
        val name = id?.let { ServerCacheManager.getItem(it)?.name }
        return name?.takeIf { it.isNotBlank() && it != "null" } ?: obj.removePrefix("obj.")
    }

    private fun queueAction(request: ActionRequest, staff: StaffSession): CompletableFuture<ActionResult> {
        val future = CompletableFuture<ActionResult>()
        pendingActions += PendingAction(request, staff, future)
        return future
    }

    private fun processPendingActions() {
        while (true) {
            val (request, staff, future) = pendingActions.poll() ?: return
            val result =
                try {
                    perform(request, staff)
                } catch (e: Exception) {
                    logger.warn(e) { "Web panel action failed: $request" }
                    ActionResult(ok = false, message = e.message ?: e.javaClass.simpleName)
                }
            future.complete(result)
        }
    }

    private fun perform(request: ActionRequest, staff: StaffSession): ActionResult =
        when (request.type) {
            "broadcast" -> broadcast(request.text)
            "kick" -> withPlayer(request) { kick(it, staff, request.reason) }
            "give" -> withPlayer(request) { give(it, request.item, request.count ?: 1) }
            "teleport" -> withPlayer(request) { teleport(it, request) }
            "ban" -> ban(request, staff)
            "unban" -> unban(request.player, staff)
            "mute" -> mute(request, staff)
            "unmute" -> unmute(request.player, staff)
            "bans" -> ActionResult(ok = true, message = "", data = bans.list())
            "maintenance" -> setMaintenance(request.enabled ?: false)
            "xpRate" -> setGlobalXpRate(request.rate)
            "schedule" -> schedule(request.mode, request.seconds ?: 0)
            "cancelSchedule" -> cancelSchedule()
            else -> fail("Unknown action '${request.type}'.")
        }

    private fun broadcast(text: String?): ActionResult {
        val message = text?.trim().orEmpty()
        if (message.isEmpty()) {
            return fail("Broadcast text is empty.")
        }
        var count = 0
        for (player in playerList) {
            player.mes(message, ChatType.Broadcast)
            count++
        }
        return ok("Broadcast sent to $count player(s).")
    }

    private fun kick(player: Player, staff: StaffSession, reason: String?): ActionResult {
        if (!staff.outranks(player.modLevel)) {
            return fail("You can't kick ${player.displayName}: they have the same or higher rank.")
        }
        bans.recordKick(player.characterId, reasonOrDefault(reason), staff.name)
        player.forceDisconnect()
        return ok("Kicked ${player.displayName}.")
    }

    private fun give(player: Player, item: String?, count: Int): ActionResult {
        val type = resolveObj(item.orEmpty()) ?: return fail("No item found for '$item'.")
        if (count <= 0) {
            return fail("Count must be positive.")
        }
        val added = player.invAdd(player.inv, type.id, count, strict = false)
        if (added.err is TransactionResult.RestrictedDummyitem) {
            return fail("That item can't be spawned.")
        }
        val name = type.name.ifEmpty { item.orEmpty() }
        val amount = added.completed().formatAmount
        player.mes("An admin gave you $name x $amount.")
        return ok("Gave ${player.displayName} $name x $amount.")
    }

    private fun teleport(player: Player, request: ActionRequest): ActionResult {
        val x = request.x ?: return fail("Missing x coordinate.")
        val z = request.z ?: return fail("Missing z coordinate.")
        val dest = CoordGrid(x, z, request.level ?: 0)
        val launched =
            protectedAccess.launch(player) {
                player.mes("An admin teleported you.")
                telejump(dest, TeleportType.Exempt)
            }
        if (!launched) {
            return fail("${player.displayName} is busy, try again.")
        }
        return ok("Teleported ${player.displayName} to $dest.")
    }

    private fun ban(request: ActionRequest, staff: StaffSession): ActionResult {
        val target = punishable(request, staff, "ban") { return it }
        bans.ban(target, request.days, request.reason!!.trim(), staff.name)
        findOnline(target.name)?.forceDisconnect()
        return ok("Banned ${target.name} ${length(request.days)}.")
    }

    private fun unban(name: String?, staff: StaffSession): ActionResult {
        val target = bans.target(name.orEmpty()) ?: return fail("No character named '${name.orEmpty().trim()}'.")
        bans.unban(target, staff.name)
        return ok("Unbanned ${target.name}.")
    }

    private fun mute(request: ActionRequest, staff: StaffSession): ActionResult {
        val target = punishable(request, staff, "mute") { return it }
        val until = bans.mute(target, request.days, request.reason!!.trim(), staff.name)
        findOnline(target.name)?.let { player ->
            player.mutedUntil = until
            player.mes("You have been muted.")
        }
        return ok("Muted ${target.name} ${length(request.days)}.")
    }

    private fun unmute(name: String?, staff: StaffSession): ActionResult {
        val target = bans.target(name.orEmpty()) ?: return fail("No character named '${name.orEmpty().trim()}'.")
        bans.unmute(target, staff.name)
        findOnline(target.name)?.let { player ->
            player.mutedUntil = null
            player.mes("You are no longer muted.")
        }
        return ok("Unmuted ${target.name}.")
    }

    private inline fun punishable(
        request: ActionRequest,
        staff: StaffSession,
        verb: String,
        onError: (ActionResult) -> Nothing,
    ): PanelBans.Target {
        val name = request.player?.trim().orEmpty()
        if (name.isEmpty()) {
            onError(fail("Enter a player name to $verb."))
        }
        if (request.days != null && request.days <= 0) {
            onError(fail("Length must be at least 1 day."))
        }
        if (request.reason.isNullOrBlank()) {
            onError(fail("Give a reason so other staff know why."))
        }
        val target = bans.target(name) ?: onError(fail("No character named '$name'."))
        if (!staff.outranks(target.rights)) {
            onError(fail("You can't $verb ${target.name}: they have the same or higher rank."))
        }
        return target
    }

    private fun length(days: Int?): String = if (days == null) "permanently" else "for $days day(s)"

    private fun reasonOrDefault(reason: String?): String = reason?.trim()?.ifEmpty { null } ?: "No reason given"

    private fun setMaintenance(enabled: Boolean): ActionResult {
        loginRestrictions.maintenance = enabled
        settings.maintenance = enabled
        settings.save()
        val state = if (enabled) "on: only staff can log in" else "off"
        return ok("Maintenance mode $state.")
    }

    private fun setGlobalXpRate(rate: Double?): ActionResult {
        if (rate == null || rate !in MIN_XP_RATE..MAX_XP_RATE) {
            return fail("XP rate must be between $MIN_XP_RATE and $MAX_XP_RATE.")
        }
        settings.globalXpRate = rate
        settings.save()
        for (player in playerList) {
            player.xpRate = 1.0
            player.globalXpRate = rate
        }
        return ok("XP rate set to ${rate}x.")
    }

    private fun schedule(mode: String?, seconds: Int): ActionResult {
        val action = ScheduledAction.entries.firstOrNull { it.id == mode }
        if (action == null) {
            return fail("Unknown scheduled action '$mode'.")
        }
        if (update.isUpdating()) {
            return fail("The server is already shutting down.")
        }
        if (action.restarts && !supervised) {
            return fail("Start the server with ./run-server.sh to use restart.")
        }
        if (seconds !in 0..MAX_COUNTDOWN_SECONDS) {
            return fail("Countdown must be between 0 and $MAX_COUNTDOWN_SECONDS seconds.")
        }
        val cycles = maxOf(1, ceil(seconds * 1000.0 / CYCLE_MILLIS).toInt())
        update.startCountdown(cycles)
        for (player in playerList) {
            MiscOutput.updateRebootTimer(player, cycles)
        }
        scheduledAction = action
        return ok("${action.label} scheduled in $seconds second(s).")
    }

    private fun cancelSchedule(): ActionResult {
        if (!update.state.isCountdown()) {
            return fail("There is no countdown to cancel.")
        }
        update.clear()
        scheduledAction = null
        return ok("Countdown cancelled.")
    }

    private fun processScheduledExit() {
        val action = scheduledAction ?: return
        if (!update.isUpdating()) {
            if (!update.state.isCountdown()) {
                scheduledAction = null
            }
            return
        }
        if (exitRequested || playerList.any()) {
            return
        }
        exitRequested = true
        action.marker?.let { Files.writeString(DATA_DIR.resolve(RESTART_MARKER), it) }
        logger.info { "Web panel: ${action.label.lowercase()} now that every player has logged out." }
        SafeServiceExit.terminate()
    }

    private inline fun withPlayer(
        request: ActionRequest,
        action: (Player) -> ActionResult,
    ): ActionResult {
        val name = request.player?.trim().orEmpty()
        val player = findOnline(name) ?: return fail("No online player named '$name'.")
        return action(player)
    }

    private fun findOnline(name: String): Player? =
        playerList.firstOrNull { it.displayName.equals(name, ignoreCase = true) }

    private fun resolveObj(input: String): ItemServerType? {
        val trimmed = input.trim()
        trimmed.toIntOrNull()?.let {
            return ServerCacheManager.getItem(it)
        }
        val internal = "obj." + trimmed.lowercase().replace(' ', '_')
        val id = runCatching { internal.asRSCM(RSCMType.OBJ) }.getOrNull() ?: return null
        return ServerCacheManager.getItem(id)
    }

    private fun buildSnapshot(): ServerView {
        val samples = minOf(cycle, TICK_HISTORY.toLong()).toInt()
        val recent = tickDurations.take(samples)
        val lastIndex = ((cycle - 1) % TICK_HISTORY).toInt()
        val runtime = Runtime.getRuntime()
        val state = update.state
        val countdown = if (state.isCountdown()) state.current * CYCLE_MILLIS / 1000 else null
        return ServerView(
            uptimeSeconds = (System.nanoTime() - startedAt) / 1_000_000_000,
            cycle = cycle,
            lastTickMs = tickDurations[lastIndex].toMillis(),
            avgTickMs = if (recent.isEmpty()) 0.0 else recent.average() / NANOS_PER_MILLI,
            maxTickMs = (recent.maxOrNull() ?: 0L).toMillis(),
            heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
            heapMaxMb = runtime.maxMemory() / BYTES_PER_MB,
            maintenance = loginRestrictions.maintenance,
            globalXpRate = settings.globalXpRate,
            supervised = supervised,
            countdownSeconds = countdown,
            scheduledAction = scheduledAction?.label,
            players = playerList.map(::viewOf).sortedBy { it.name.lowercase() },
        )
    }

    private fun viewOf(player: Player): PlayerView {
        val skills =
            statNames.map { (internal, name) ->
                SkillView(name, player.stat(internal), player.statBase(internal))
            }
        return PlayerView(
            name = player.displayName,
            combatLevel = player.combatLevel,
            totalLevel = skills.sumOf { it.base },
            x = player.coords.x,
            z = player.coords.z,
            level = player.coords.level,
            runEnergy = player.runEnergy / 100,
            skills = skills,
        )
    }

    private fun StaffSession.outranks(target: Rights): Boolean = rights.isHigherThan(target)

    private fun Long.toMillis(): Double = this / NANOS_PER_MILLI

    private fun ok(message: String) = ActionResult(ok = true, message = message)

    private fun fail(message: String) = ActionResult(ok = false, message = message)

    private enum class ScheduledAction(val id: String, val label: String, val marker: String?) {
        Restart("restart", "Restart", "restart"),
        Rebuild("rebuild", "Rebuild & restart", "rebuild"),
        Shutdown("shutdown", "Shutdown", null);

        val restarts: Boolean
            get() = marker != null
    }

    private data class PendingAction(
        val request: ActionRequest,
        val staff: StaffSession,
        val future: CompletableFuture<ActionResult>,
    )

    private companion object {
        const val PORT = 8090
        const val TICK_HISTORY = 100
        const val CYCLE_MILLIS = 600
        const val NANOS_PER_MILLI = 1_000_000.0
        const val BYTES_PER_MB = 1024L * 1024L
        const val MIN_XP_RATE = 0.1
        const val MAX_XP_RATE = 100.0
        const val MAX_COUNTDOWN_SECONDS = 3600
        const val SUPERVISED_ENV = "OPENRUNE_SUPERVISED"
        const val RESTART_MARKER = "restart-request"
        val DATA_DIR: Path = Path.of(".data")
    }
}
