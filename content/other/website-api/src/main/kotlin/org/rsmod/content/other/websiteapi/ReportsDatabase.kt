package org.rsmod.content.other.websiteapi

import java.sql.Types
import org.rsmod.api.db.DatabaseConfig

class ReportsDatabase(private val config: () -> DatabaseConfig) {
    /** The offender's display name as stored, or null when no such character exists. */
    fun characterName(name: String): String? =
        config.connect { connection ->
            connection.prepareStatement(SELECT_NAME).use { ps ->
                ps.setString(1, name.trim())
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }

    fun submit(reporterAccountId: Int?, reporterName: String, report: NewReport, source: String) {
        config.connect { connection ->
            connection.prepareStatement(INSERT_REPORT).use { ps ->
                if (reporterAccountId == null) ps.setNull(1, Types.INTEGER) else ps.setInt(1, reporterAccountId)
                ps.setString(2, reporterName)
                ps.setString(3, report.offender)
                ps.setString(4, report.category)
                ps.setString(5, report.details)
                ps.setString(6, source)
                ps.executeUpdate()
            }
        }
    }

    fun recordLogin(accountId: Int, characterId: Int, ip: String) {
        config.connect { connection ->
            connection.prepareStatement(UPSERT_LOGIN_IP).use { ps ->
                ps.setInt(1, accountId)
                ps.setInt(2, characterId)
                ps.setString(3, ip)
                ps.executeUpdate()
            }
        }
    }

    fun pruneLoginIps() {
        config.connect { connection -> connection.createStatement().use { it.executeUpdate(PRUNE_LOGIN_IPS) } }
    }

    private companion object {
        const val LOGIN_IP_RETENTION_DAYS = 180

        const val SELECT_NAME =
            "SELECT display_name FROM account_characters WHERE LOWER(display_name) = LOWER(?)"

        const val INSERT_REPORT =
            "INSERT INTO website.reports (reporter_account_id, reporter_name, offender_name, category, " +
                "details, source) VALUES (?, ?, ?, ?, ?, ?)"

        const val UPSERT_LOGIN_IP =
            "INSERT INTO website.login_ips (account_id, character_id, ip) VALUES (?, ?, ?) " +
                "ON CONFLICT (character_id, ip) DO UPDATE SET last_seen = CURRENT_TIMESTAMP, " +
                "logins = website.login_ips.logins + 1"

        const val PRUNE_LOGIN_IPS =
            "DELETE FROM website.login_ips WHERE last_seen < CURRENT_TIMESTAMP - " +
                "INTERVAL '$LOGIN_IP_RETENTION_DAYS days'"
    }
}

data class NewReport(val offender: String, val category: String, val details: String)

object ReportRules {
    val CATEGORIES =
        listOf("Botting or macroing", "Offensive language", "Scamming", "Bug abuse", "Harassment", "Other")
    const val IN_GAME_CATEGORY = "In-game report"
    const val MAX_DETAILS = 1000
    const val MIN_DETAILS = 10

    /** Null when the report is acceptable, otherwise the reason it isn't. */
    fun problem(category: String, details: String): String? =
        when {
            category !in CATEGORIES && category != IN_GAME_CATEGORY -> "Pick a report category."
            details.trim().length < MIN_DETAILS -> "Tell us a bit more about what happened."
            details.length > MAX_DETAILS -> "Keep the details under $MAX_DETAILS characters."
            else -> null
        }
}
