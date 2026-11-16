package org.rsmod.content.other.webpanel

import dev.or2.central.account.Rights
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.content.other.websiteapi.WebsiteSchema
import org.rsmod.content.other.websiteapi.connect

/**
 * Staff-only reads and writes that never touch live game state (notes, reports, staff ranks,
 * search), so they run on the HTTP threads with their own connections.
 */
class PanelStaffDatabase(private val config: () -> DatabaseConfig) {
    fun ensureSchema() {
        config.connect(WebsiteSchema::ensure)
    }

    fun history(characterId: Int, includeIps: Boolean): PlayerHistoryView =
        config.connect { connection ->
            PlayerHistoryView(
                punishments = punishments(connection, characterId),
                notes = notes(connection, characterId),
                addresses = if (includeIps) addresses(connection, characterId) else emptyList(),
                alts = alts(connection, characterId),
            )
        }

    fun addNote(characterName: String, author: String, body: String): Boolean =
        config.connect { connection ->
            connection.prepareStatement(INSERT_NOTE).use { ps ->
                ps.setString(1, author)
                ps.setString(2, body)
                ps.setString(3, characterName.trim())
                ps.executeUpdate() > 0
            }
        }

    fun reports(open: Boolean): List<ReportView> =
        config.connect { connection ->
            connection.prepareStatement(if (open) SELECT_OPEN_REPORTS else SELECT_CLOSED_REPORTS).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                ReportView(
                                    id = rs.getLong("id"),
                                    reporter = rs.getString("reporter_name"),
                                    offender = rs.getString("offender_name"),
                                    category = rs.getString("category"),
                                    details = rs.getString("details"),
                                    source = rs.getString("source"),
                                    status = rs.getString("status"),
                                    createdAt = rs.time("created_at"),
                                    handledBy = rs.getString("handled_by"),
                                    handledAt = rs.time("handled_at"),
                                    staffComment = rs.getString("staff_comment"),
                                )
                            )
                        }
                    }
                }
            }
        }

    fun closeReport(id: Long, staff: String, comment: String?): Boolean =
        config.connect { connection ->
            connection.prepareStatement(CLOSE_REPORT).use { ps ->
                ps.setString(1, staff)
                ps.setString(2, comment)
                ps.setLong(3, id)
                ps.executeUpdate() > 0
            }
        }

    fun openReportCount(): Int =
        config.connect { connection ->
            connection.createStatement().use { st ->
                st.executeQuery(COUNT_OPEN_REPORTS).use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    fun staffMembers(): List<StaffMemberView> =
        config.connect { connection ->
            connection.createStatement().use { st ->
                st.executeQuery(SELECT_STAFF).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                StaffMemberView(
                                    accountName = rs.getString("account_name"),
                                    rights = Rights.fromRightsColumn(rs.getString("rights")).name,
                                    twoFactor = rs.getBoolean("two_factor"),
                                    characters = rs.getString("characters")?.split(',').orEmpty(),
                                )
                            )
                        }
                    }
                }
            }
        }

    fun findAccount(accountName: String): AccountRef? =
        config.connect { connection ->
            connection.prepareStatement(SELECT_ACCOUNT_REF).use { ps ->
                ps.setString(1, accountName.trim())
                ps.setString(2, accountName.trim())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        AccountRef(rs.getInt("id"), rs.getString("account_name"), Rights.fromRightsColumn(rs.getString("rights")))
                    } else {
                        null
                    }
                }
            }
        }

    fun setRights(accountId: Int, rights: Rights) {
        config.connect { connection ->
            connection.prepareStatement(UPDATE_RIGHTS).use { ps ->
                ps.setString(1, if (rights == Rights.NONE) "" else rights.name)
                ps.setInt(2, accountId)
                ps.executeUpdate()
            }
        }
    }

    fun resetTwoFactor(accountId: Int) {
        config.connect { connection ->
            connection.prepareStatement(RESET_TWO_FACTOR).use { ps ->
                ps.setInt(1, accountId)
                ps.executeUpdate()
            }
        }
    }

    /** Matches character and account names and emails; admins also match login addresses. */
    fun search(query: String, includeIps: Boolean): List<SearchResultView> =
        config.connect { connection ->
            val sql = if (includeIps) "$SEARCH_BASE UNION $SEARCH_IPS $SEARCH_ORDER" else "$SEARCH_BASE $SEARCH_ORDER"
            connection.prepareStatement(sql).use { ps ->
                val escaped = query.trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                val like = "%$escaped%"
                for (i in 1..4) {
                    ps.setString(i, like)
                }
                if (includeIps) {
                    ps.setString(5, "$escaped%")
                }
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                SearchResultView(
                                    name = rs.getString("display_name"),
                                    accountName = rs.getString("account_name"),
                                    email = rs.getString("email"),
                                    matched = rs.getString("matched"),
                                )
                            )
                        }
                    }
                }
            }
        }

    private fun punishments(connection: Connection, characterId: Int): List<PunishmentView> =
        connection.prepareStatement(SELECT_PUNISHMENTS).use { ps ->
            ps.setInt(1, characterId)
            ps.setInt(2, characterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            PunishmentView(
                                kind = rs.getString("kind"),
                                status = rs.getString("status"),
                                reason = rs.getString("reason"),
                                issuedBy = rs.getString("issued_by"),
                                issuedAt = rs.time("issued_at"),
                                expiresAt = rs.time("expires_at"),
                            )
                        )
                    }
                }
            }
        }

    private fun notes(connection: Connection, characterId: Int): List<NoteView> =
        connection.prepareStatement(SELECT_NOTES).use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(NoteView(rs.getString("author"), rs.getString("body"), rs.time("created_at")))
                    }
                }
            }
        }

    private fun addresses(connection: Connection, characterId: Int): List<AddressView> =
        connection.prepareStatement(SELECT_ADDRESSES).use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(AddressView(rs.getString("ip"), rs.getInt("logins"), rs.time("first_seen"), rs.time("last_seen")))
                    }
                }
            }
        }

    private fun alts(connection: Connection, characterId: Int): List<String> =
        connection.prepareStatement(SELECT_ALTS).use { ps ->
            ps.setInt(1, characterId)
            ps.setInt(2, characterId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getString(1))
                    }
                }
            }
        }

    private fun ResultSet.time(column: String): String? =
        getObject(column, LocalDateTime::class.java)?.format(DISPLAY_FORMAT)

    data class AccountRef(val id: Int, val accountName: String, val rights: Rights)

    private companion object {
        val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")
        const val MAX_ROWS = 100

        const val SELECT_PUNISHMENTS =
            "SELECT kind, status, reason, issued_by, issued_at, expires_at FROM punishments " +
                "WHERE character_id = ? OR account_id = (SELECT account_id FROM account_characters WHERE id = ?) " +
                "ORDER BY issued_at DESC LIMIT $MAX_ROWS"

        const val SELECT_NOTES =
            "SELECT author, body, created_at FROM website.staff_notes WHERE character_id = ? " +
                "ORDER BY created_at DESC LIMIT $MAX_ROWS"

        const val INSERT_NOTE =
            "INSERT INTO website.staff_notes (character_id, author, body) " +
                "SELECT id, ?, ? FROM account_characters WHERE LOWER(display_name) = LOWER(?)"

        const val SELECT_ADDRESSES =
            "SELECT ip, logins, first_seen, last_seen FROM website.login_ips WHERE character_id = ? " +
                "ORDER BY last_seen DESC LIMIT $MAX_ROWS"

        const val SELECT_ALTS =
            "SELECT DISTINCT c.display_name FROM website.login_ips mine " +
                "JOIN website.login_ips other ON other.ip = mine.ip AND other.character_id <> ? " +
                "JOIN account_characters c ON c.id = other.character_id " +
                "WHERE mine.character_id = ? AND c.display_name IS NOT NULL ORDER BY c.display_name LIMIT $MAX_ROWS"

        const val REPORT_COLUMNS =
            "SELECT id, reporter_name, offender_name, category, details, source, status, created_at, " +
                "handled_by, handled_at, staff_comment FROM website.reports"

        const val SELECT_OPEN_REPORTS = "$REPORT_COLUMNS WHERE status = 'open' ORDER BY created_at ASC LIMIT $MAX_ROWS"

        const val SELECT_CLOSED_REPORTS =
            "$REPORT_COLUMNS WHERE status <> 'open' ORDER BY handled_at DESC NULLS LAST LIMIT $MAX_ROWS"

        const val COUNT_OPEN_REPORTS = "SELECT COUNT(*) FROM website.reports WHERE status = 'open'"

        const val CLOSE_REPORT =
            "UPDATE website.reports SET status = 'closed', handled_by = ?, handled_at = CURRENT_TIMESTAMP, " +
                "staff_comment = ? WHERE id = ? AND status = 'open'"

        const val SELECT_STAFF =
            "SELECT a.account_name, a.rights, " +
                "(a.two_factor_secret IS NOT NULL AND a.two_factor_confirmed_at IS NOT NULL) AS two_factor, " +
                "(SELECT string_agg(c.display_name, ',' ORDER BY c.display_name) FROM account_characters c " +
                "WHERE c.account_id = a.id AND c.display_name IS NOT NULL) AS characters " +
                "FROM accounts a WHERE a.rights <> '' ORDER BY a.rights DESC, LOWER(a.account_name)"

        const val SELECT_ACCOUNT_REF =
            "SELECT a.id, a.account_name, a.rights FROM accounts a WHERE LOWER(a.account_name) = LOWER(?) " +
                "OR a.id = (SELECT c.account_id FROM account_characters c WHERE LOWER(c.display_name) = LOWER(?)) " +
                "LIMIT 1"

        const val UPDATE_RIGHTS = "UPDATE accounts SET rights = ? WHERE id = ?"

        const val RESET_TWO_FACTOR =
            "UPDATE accounts SET two_factor_secret = NULL, two_factor_confirmed_at = NULL, " +
                "two_factor_recovery_codes = NULL WHERE id = ?"

        const val SEARCH_BASE =
            "SELECT c.display_name, a.account_name, a.email, " +
                "CASE WHEN LOWER(c.display_name) LIKE ? THEN 'name' ELSE 'account or email' END AS matched " +
                "FROM account_characters c JOIN accounts a ON a.id = c.account_id " +
                "WHERE c.display_name IS NOT NULL AND (LOWER(a.account_name) LIKE ? OR LOWER(COALESCE(a.email, '')) LIKE ? " +
                "OR LOWER(c.display_name) LIKE ?)"

        const val SEARCH_IPS =
            "SELECT c.display_name, a.account_name, a.email, 'address ' || l.ip AS matched " +
                "FROM website.login_ips l JOIN account_characters c ON c.id = l.character_id " +
                "JOIN accounts a ON a.id = c.account_id WHERE c.display_name IS NOT NULL AND l.ip LIKE ?"

        const val SEARCH_ORDER = "ORDER BY 1 LIMIT $MAX_ROWS"
    }
}
