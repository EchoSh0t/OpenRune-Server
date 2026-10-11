package org.rsmod.content.other.webpanel

import java.sql.Types
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.rsmod.api.db.jdbc.GameDatabase

/**
 * Reads and writes `account_characters.banned_until`, which the login hook checks. The game
 * database has a single shared connection, so every call here must run on the game thread.
 */
class PanelBans(private val database: GameDatabase) {
    fun ban(displayName: String, days: Int?): LocalDateTime? {
        val until = if (days == null) PERMANENT else LocalDateTime.now().plusDays(days.toLong())
        return if (setBannedUntil(displayName, until)) until else null
    }

    fun unban(displayName: String): Boolean = setBannedUntil(displayName, null)

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

    private fun setBannedUntil(displayName: String, until: LocalDateTime?): Boolean =
        database.withTransactionBlocking { connection ->
            connection.prepareStatement(UPDATE_BANNED_UNTIL).use { statement ->
                if (until == null) {
                    statement.setNull(1, Types.TIMESTAMP)
                } else {
                    statement.setObject(1, until)
                }
                statement.setString(2, displayName.trim())
                statement.executeUpdate() > 0
            }
        }

    private fun view(name: String, until: LocalDateTime): BanView {
        val permanent = !until.isBefore(PERMANENT)
        val text = if (permanent) "Permanent" else until.format(DISPLAY_FORMAT)
        return BanView(name, text, permanent)
    }

    private companion object {
        val PERMANENT: LocalDateTime = LocalDateTime.of(9999, 12, 31, 0, 0)
        val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")

        const val UPDATE_BANNED_UNTIL =
            "UPDATE account_characters SET banned_until = ? WHERE LOWER(display_name) = LOWER(?)"
        const val SELECT_BANNED =
            "SELECT display_name, banned_until FROM account_characters " +
                "WHERE banned_until IS NOT NULL ORDER BY LOWER(display_name)"
    }
}
