package org.rsmod.content.other.webpanel

import dev.or2.central.account.Rights
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.rsmod.api.db.DatabaseConfig

/**
 * Opens its own short-lived connections: the game's shared connection belongs to the game thread,
 * and these queries run on HTTP worker threads.
 */
class PanelDatabase(private val config: () -> DatabaseConfig) : StaffStore {
    override fun findAccount(accountName: String): StaffAccount? =
        connect { connection ->
            connection.prepareStatement(SELECT_ACCOUNT).use { ps ->
                ps.setString(1, accountName.trim())
                ps.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return@connect null
                    }
                    val secret = rs.getString("two_factor_secret")?.takeIf { it.isNotBlank() }
                    StaffAccount(
                        accountId = rs.getInt("id"),
                        accountName = rs.getString("account_name"),
                        passwordHash = rs.getString("password_hash"),
                        rights = Rights.fromRightsColumn(rs.getString("rights")),
                        twoFactorSecret = secret,
                        twoFactorConfirmed =
                            secret != null && rs.getObject("two_factor_confirmed_at") != null,
                    )
                }
            }
        }

    override fun rightsOf(accountId: Int): Rights =
        connect { connection ->
            connection.prepareStatement(SELECT_RIGHTS).use { ps ->
                ps.setInt(1, accountId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) Rights.fromRightsColumn(rs.getString(1)) else Rights.NONE
                }
            }
        }

    override fun enableTwoFactor(accountId: Int, secret: String) {
        connect { connection ->
            connection.prepareStatement(ENABLE_TWO_FACTOR).use { ps ->
                ps.setString(1, secret)
                ps.setObject(2, LocalDateTime.now())
                ps.setInt(3, accountId)
                ps.executeUpdate()
            }
        }
    }

    fun lookup(name: String, skills: List<Pair<Int, String>>, items: ItemNames): PlayerLookupView? =
        connect { connection ->
            val identity =
                connection.prepareStatement(SELECT_IDENTITY).use { ps ->
                    ps.setString(1, name.trim())
                    ps.executeQuery().use { rs -> if (rs.next()) identity(rs) else null }
                } ?: return@connect null
            val levels = HashMap<Int, Pair<Int, Long>>()
            connection.prepareStatement(SELECT_STATS).use { ps ->
                ps.setInt(1, identity.characterId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        levels[rs.getInt("stat_id")] =
                            rs.getInt("base_level") to rs.getLong("fine_xp") / FINE_XP_SCALE
                    }
                }
            }
            val objs = LinkedHashMap<String, MutableList<LookupItem>>()
            connection.prepareStatement(SELECT_OBJS).use { ps ->
                ps.setInt(1, identity.characterId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val item =
                            LookupItem(rs.getInt("slot"), items.nameOf(rs.getString("obj")), rs.getInt("count"))
                        objs.getOrPut(rs.getString("inv")) { mutableListOf() } += item
                    }
                }
            }
            identity.copy(
                skills =
                    skills.map { (id, skill) ->
                        val (level, xp) = levels[id] ?: (1 to 0L)
                        LookupSkill(skill, level, xp)
                    },
                inventories =
                    objs.entries
                        .sortedBy { INV_ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                        .map { (inv, list) -> LookupInventory(inv, invLabel(inv), list) },
            )
        }

    private fun identity(rs: ResultSet): PlayerLookupView =
        PlayerLookupView(
            name = rs.getString("display_name"),
            characterId = rs.getInt("character_id"),
            accountId = rs.getInt("account_id"),
            accountName = rs.getString("account_name"),
            rights = Rights.fromRightsColumn(rs.getString("rights")).name,
            email = rs.getString("email"),
            members = rs.getBoolean("members"),
            twoFactor = rs.getBoolean("two_factor"),
            createdAt = rs.time("created_at"),
            lastLogin = rs.time("last_login"),
            lastLogout = rs.time("last_logout"),
            bannedUntil = rs.time("banned_until"),
            mutedUntil = rs.time("muted_until"),
            x = rs.getObject("x") as Int?,
            z = rs.getObject("z") as Int?,
            level = rs.getObject("level") as Int?,
        )

    private fun ResultSet.time(column: String): String? =
        getObject(column, LocalDateTime::class.java)?.format(DISPLAY_FORMAT)

    private fun invLabel(inv: String): String =
        when (inv) {
            "inv.inv" -> "Inventory"
            "inv.worn" -> "Equipment"
            "inv.bank" -> "Bank"
            else -> inv.removePrefix("inv.").replace('_', ' ').replaceFirstChar(Char::uppercaseChar)
        }

    private fun <T> connect(block: (Connection) -> T): T {
        val db = config()
        return DriverManager.getConnection(db.jdbcUrl, db.user, db.password).use(block)
    }

    fun interface ItemNames {
        fun nameOf(obj: String): String
    }

    private companion object {
        const val FINE_XP_SCALE = 10L
        val INV_ORDER = listOf("inv.inv", "inv.worn", "inv.bank")
        val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")

        const val SELECT_ACCOUNT =
            "SELECT id, account_name, password_hash, rights, two_factor_secret, " +
                "two_factor_confirmed_at FROM accounts WHERE LOWER(account_name) = LOWER(?)"

        const val SELECT_RIGHTS = "SELECT rights FROM accounts WHERE id = ?"

        const val ENABLE_TWO_FACTOR =
            "UPDATE accounts SET two_factor_secret = ?, two_factor_confirmed_at = ?, " +
                "two_factor_recovery_codes = NULL WHERE id = ?"

        const val SELECT_IDENTITY =
            "SELECT c.id AS character_id, c.display_name, c.members, c.created_at, c.banned_until, " +
                "c.muted_until, a.id AS account_id, a.account_name, a.rights, a.email, " +
                "(a.two_factor_secret IS NOT NULL AND a.two_factor_confirmed_at IS NOT NULL) " +
                "AS two_factor, p.last_login, p.last_logout, p.x, p.z, p.level " +
                "FROM account_characters c JOIN accounts a ON a.id = c.account_id " +
                "LEFT JOIN main.character_progress p ON p.character_id = c.id " +
                "WHERE LOWER(c.display_name) = LOWER(?)"

        const val SELECT_STATS =
            "SELECT stat_id, base_level, fine_xp FROM main.stats WHERE character_id = ?"

        const val SELECT_OBJS =
            "SELECT inv, slot, obj, count FROM main.inventory_objs WHERE character_id = ? " +
                "ORDER BY inv, slot"
    }
}
