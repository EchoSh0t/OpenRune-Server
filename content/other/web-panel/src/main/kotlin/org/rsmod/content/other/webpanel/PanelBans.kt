package org.rsmod.content.other.webpanel

import dev.or2.central.account.Rights
import java.sql.Types
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.rsmod.api.db.jdbc.GameDatabase

/**
 * Reads and writes `account_characters.banned_until` / `muted_until` (checked at login and in
 * chat) and keeps a matching row in `punishments` for the history shown to staff. The game
 * database has a single shared connection, so every call here must run on the game thread.
 */
class PanelBans(private val database: GameDatabase) {
    data class Target(val characterId: Int, val name: String, val rights: Rights)

    fun target(displayName: String): Target? =
        database.withTransactionBlocking { connection ->
            connection.prepareStatement(SELECT_TARGET).use { statement ->
                statement.setString(1, displayName.trim())
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        Target(rs.getInt("id"), rs.getString("display_name"), Rights.fromRightsColumn(rs.getString("rights")))
                    } else {
                        null
                    }
                }
            }
        }

    fun ban(target: Target, days: Int?, reason: String, staff: String): LocalDateTime {
        val until = until(days)
        setColumn(UPDATE_BANNED_UNTIL, target.characterId, until)
        record(target.characterId, if (days == null) "ban" else "temp_ban", ACTIVE, reason, staff, until.takeIf { days != null })
        return until
    }

    fun unban(target: Target, staff: String) {
        setColumn(UPDATE_BANNED_UNTIL, target.characterId, null)
        deactivate(target.characterId, "ban" to "temp_ban", staff)
    }

    fun mute(target: Target, days: Int?, reason: String, staff: String): LocalDateTime {
        val until = until(days)
        setColumn(UPDATE_MUTED_UNTIL, target.characterId, until)
        record(target.characterId, if (days == null) "mute" else "temp_mute", ACTIVE, reason, staff, until.takeIf { days != null })
        return until
    }

    fun unmute(target: Target, staff: String) {
        setColumn(UPDATE_MUTED_UNTIL, target.characterId, null)
        deactivate(target.characterId, "mute" to "temp_mute", staff)
    }

    /** Kicks are stored inactive: an active kick row would make Central kick the player again. */
    fun recordKick(characterId: Int, reason: String, staff: String) {
        record(characterId, "kick", INACTIVE, reason, staff, null)
    }

    fun list(): List<BanView> {
        val now = LocalDateTime.now()
        return database.withTransactionBlocking { connection ->
            connection.prepareStatement(SELECT_BANNED).use { statement ->
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val until = rs.getObject("banned_until", LocalDateTime::class.java)
                            if (until.isAfter(now)) {
                                add(view(rs.getString("display_name"), until))
                            }
                        }
                    }
                }
            }
        }
    }

    fun isPermanent(until: LocalDateTime): Boolean = !until.isBefore(PERMANENT)

    private fun until(days: Int?): LocalDateTime =
        if (days == null) PERMANENT else LocalDateTime.now().plusDays(days.toLong())

    private fun setColumn(sql: String, characterId: Int, until: LocalDateTime?) {
        database.withTransactionBlocking { connection ->
            connection.prepareStatement(sql).use { statement ->
                if (until == null) {
                    statement.setNull(1, Types.TIMESTAMP)
                } else {
                    statement.setObject(1, until)
                }
                statement.setInt(2, characterId)
                statement.executeUpdate()
            }
        }
    }

    private fun record(
        characterId: Int,
        kind: String,
        status: String,
        reason: String,
        staff: String,
        expiresAt: LocalDateTime?,
    ) {
        database.withTransactionBlocking { connection ->
            connection.prepareStatement(INSERT_PUNISHMENT).use { statement ->
                statement.setInt(1, characterId)
                statement.setString(2, kind)
                statement.setObject(3, expiresAt)
                statement.setString(4, reason)
                statement.setString(5, staff)
                statement.setString(6, status)
                statement.executeUpdate()
            }
        }
    }

    private fun deactivate(characterId: Int, kinds: Pair<String, String>, staff: String) {
        database.withTransactionBlocking { connection ->
            connection.prepareStatement(DEACTIVATE).use { statement ->
                statement.setString(1, "Lifted by $staff")
                statement.setInt(2, characterId)
                statement.setString(3, kinds.first)
                statement.setString(4, kinds.second)
                statement.executeUpdate()
            }
        }
    }

    private fun view(name: String, until: LocalDateTime): BanView {
        val permanent = isPermanent(until)
        val text = if (permanent) "Permanent" else until.format(DISPLAY_FORMAT)
        return BanView(name, text, permanent)
    }

    private companion object {
        val PERMANENT: LocalDateTime = LocalDateTime.of(9999, 12, 31, 0, 0)
        val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")
        const val ACTIVE = "active"
        const val INACTIVE = "inactive"

        const val SELECT_TARGET =
            "SELECT c.id, c.display_name, a.rights FROM account_characters c " +
                "JOIN accounts a ON a.id = c.account_id WHERE LOWER(c.display_name) = LOWER(?)"
        const val UPDATE_BANNED_UNTIL = "UPDATE account_characters SET banned_until = ? WHERE id = ?"
        const val UPDATE_MUTED_UNTIL = "UPDATE account_characters SET muted_until = ? WHERE id = ?"
        const val SELECT_BANNED =
            "SELECT display_name, banned_until FROM account_characters " +
                "WHERE banned_until IS NOT NULL ORDER BY LOWER(display_name)"
        const val INSERT_PUNISHMENT =
            "INSERT INTO punishments (scope, character_id, kind, expires_at, reason, issued_by, status) " +
                "VALUES ('character', ?, ?, ?, ?, ?, ?)"
        const val DEACTIVATE =
            "UPDATE punishments SET status = 'inactive', private_notes = " +
                "COALESCE(private_notes || E'\\n', '') || ? " +
                "WHERE scope = 'character' AND character_id = ? AND kind IN (?, ?) AND status = 'active'"
    }
}
