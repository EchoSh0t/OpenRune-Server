package org.rsmod.content.other.websiteapi

import dev.or2.central.account.Rights
import java.sql.ResultSet
import java.time.LocalDateTime
import org.rsmod.api.db.DatabaseConfig

class AccountDatabase(private val config: () -> DatabaseConfig) : AccountAuthStore {
    override fun findAccount(accountName: String): WebAccount? =
        config.connect { connection ->
            connection.prepareStatement("$SELECT_ACCOUNT WHERE LOWER(account_name) = LOWER(?)").use { ps ->
                ps.setString(1, accountName.trim())
                ps.executeQuery().use { rs -> if (rs.next()) account(rs) else null }
            }
        }

    override fun account(accountId: Int): WebAccount? =
        config.connect { connection ->
            connection.prepareStatement("$SELECT_ACCOUNT WHERE id = ?").use { ps ->
                ps.setInt(1, accountId)
                ps.executeQuery().use { rs -> if (rs.next()) account(rs) else null }
            }
        }

    override fun updatePassword(accountId: Int, passwordHash: String) {
        config.connect { connection ->
            connection.prepareStatement(UPDATE_PASSWORD).use { ps ->
                ps.setString(1, passwordHash)
                ps.setInt(2, accountId)
                ps.executeUpdate()
            }
        }
    }

    override fun setTwoFactor(accountId: Int, secret: String?) {
        config.connect { connection ->
            connection.prepareStatement(UPDATE_TWO_FACTOR).use { ps ->
                ps.setString(1, secret)
                ps.setObject(2, secret?.let { LocalDateTime.now() })
                ps.setInt(3, accountId)
                ps.executeUpdate()
            }
        }
    }

    fun overview(accountId: Int): AccountView? =
        config.connect { connection ->
            val base =
                connection.prepareStatement(SELECT_OVERVIEW).use { ps ->
                    ps.setInt(1, accountId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@connect null
                        AccountView(
                            name = rs.getString("account_name"),
                            email = rs.getString("email"),
                            twoFactor = rs.getBoolean("two_factor"),
                            discordLinked = !rs.getString("discord_id").isNullOrBlank(),
                            createdAt = rs.getObject("created_at", LocalDateTime::class.java)?.toLocalDate()?.toString(),
                            staff = Rights.fromRightsColumn(rs.getString("rights")).isAtLeast(Rights.MOD),
                            characters = emptyList(),
                        )
                    }
                }
            val now = LocalDateTime.now()
            val characters =
                connection.prepareStatement(SELECT_CHARACTERS).use { ps ->
                    ps.setInt(1, accountId)
                    ps.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(
                                    CharacterView(
                                        name = rs.getString("display_name"),
                                        members = rs.getBoolean("members"),
                                        totalLevel = rs.getInt("total_level"),
                                        createdAt =
                                            rs.getObject("created_at", LocalDateTime::class.java)
                                                ?.toLocalDate()
                                                ?.toString(),
                                        banned = rs.isAfter("banned_until", now),
                                        muted = rs.isAfter("muted_until", now),
                                    )
                                )
                            }
                        }
                    }
                }
            base.copy(characters = characters)
        }

    /** False when another account is already linked to [discordId]. */
    fun linkDiscord(accountId: Int, discordId: String): Boolean =
        config.connect { connection ->
            connection.prepareStatement(LINK_DISCORD).use { ps ->
                ps.setString(1, discordId)
                ps.setInt(2, accountId)
                ps.setString(3, discordId)
                ps.setInt(4, accountId)
                ps.executeUpdate() > 0
            }
        }

    fun unlinkDiscord(accountId: Int) {
        config.connect { connection ->
            connection.prepareStatement(UNLINK_DISCORD).use { ps ->
                ps.setInt(1, accountId)
                ps.executeUpdate()
            }
        }
    }

    private fun account(rs: ResultSet): WebAccount {
        val secret = rs.getString("two_factor_secret")?.takeIf { it.isNotBlank() }
        return WebAccount(
            accountId = rs.getInt("id"),
            accountName = rs.getString("account_name"),
            passwordHash = rs.getString("password_hash"),
            twoFactorSecret = secret,
            twoFactorConfirmed = secret != null && rs.getObject("two_factor_confirmed_at") != null,
        )
    }

    private fun ResultSet.isAfter(column: String, now: LocalDateTime): Boolean =
        getObject(column, LocalDateTime::class.java)?.isAfter(now) == true

    private companion object {
        const val SELECT_ACCOUNT =
            "SELECT id, account_name, password_hash, two_factor_secret, two_factor_confirmed_at " +
                "FROM accounts"

        const val UPDATE_PASSWORD = "UPDATE accounts SET password_hash = ? WHERE id = ?"

        const val UPDATE_TWO_FACTOR =
            "UPDATE accounts SET two_factor_secret = ?, two_factor_confirmed_at = ?, " +
                "two_factor_recovery_codes = NULL WHERE id = ?"

        const val SELECT_OVERVIEW =
            "SELECT account_name, email, discord_id, created_at, rights, " +
                "(two_factor_secret IS NOT NULL AND two_factor_confirmed_at IS NOT NULL) AS two_factor " +
                "FROM accounts WHERE id = ?"

        const val SELECT_CHARACTERS =
            "SELECT c.display_name, c.members, c.created_at, c.banned_until, c.muted_until, " +
                "COALESCE((SELECT SUM(s.base_level) FROM main.stats s WHERE s.character_id = c.id), 0) " +
                "AS total_level FROM account_characters c " +
                "WHERE c.account_id = ? AND c.display_name IS NOT NULL ORDER BY c.created_at"

        const val LINK_DISCORD =
            "UPDATE accounts SET discord_id = ? WHERE id = ? AND NOT EXISTS " +
                "(SELECT 1 FROM accounts WHERE discord_id = ? AND id <> ?)"

        const val UNLINK_DISCORD = "UPDATE accounts SET discord_id = NULL WHERE id = ?"
    }
}
