package org.rsmod.content.other.websiteapi

import dev.or2.sql.OpenRuneSql
import java.sql.Connection
import java.sql.DriverManager
import org.rsmod.api.db.DatabaseConfig

/**
 * Opens a short-lived connection. The game's shared connection belongs to the game thread, and
 * website queries run on HTTP and background threads.
 */
fun <T> (() -> DatabaseConfig).connect(block: (Connection) -> T): T {
    val db = this()
    return DriverManager.getConnection(db.jdbcUrl, db.user, db.password).use(block)
}

class WebsiteDatabase(private val config: () -> DatabaseConfig) : AccountStore {
    override fun collisionKeyTaken(collisionKey: String): Boolean =
        connect { connection ->
            connection.prepareStatement(COLLISION_KEY_TAKEN).use { ps ->
                ps.setString(1, collisionKey)
                ps.executeQuery().use { it.next() }
            }
        }

    override fun insertIfAbsent(accountName: String, passwordHash: String, email: String?): Boolean =
        connect { connection ->
            connection.prepareStatement(INSERT_ACCOUNT).use { ps ->
                ps.setString(1, accountName)
                ps.setString(2, passwordHash)
                ps.setString(3, email)
                ps.executeUpdate() > 0
            }
        }

    fun online(): OnlineView =
        connect { connection ->
            connection.prepareStatement(ONLINE_BY_WORLD).use { ps ->
                ps.setLong(1, System.currentTimeMillis() - SESSION_STALE_MILLIS)
                ps.executeQuery().use { rs ->
                    val worlds = buildList {
                        while (rs.next()) {
                            add(WorldCount(rs.getInt("world_id"), rs.getInt("c")))
                        }
                    }
                    OnlineView(worlds.sumOf { it.count }, worlds)
                }
            }
        }

    fun skillPage(statId: Int, page: Int, pageSize: Int): List<HiscoreRow> =
        connect { connection ->
            connection.prepareStatement(SKILL_PAGE).use { ps ->
                ps.setInt(1, statId)
                ps.setInt(2, pageSize)
                ps.setInt(3, page * pageSize)
                ps.executeQuery().use { rs ->
                    buildList {
                        var rank = page * pageSize
                        while (rs.next()) {
                            val xp = rs.getLong("fine_xp") / FINE_XP_SCALE
                            add(HiscoreRow(++rank, rs.getString("display_name"), rs.getInt("base_level"), xp))
                        }
                    }
                }
            }
        }

    fun overallPage(page: Int, pageSize: Int): List<HiscoreRow> =
        connect { connection ->
            connection.prepareStatement(OVERALL_PAGE).use { ps ->
                ps.setInt(1, pageSize)
                ps.setInt(2, page * pageSize)
                ps.executeQuery().use { rs ->
                    buildList {
                        var rank = page * pageSize
                        while (rs.next()) {
                            val xp = rs.getLong("total_xp") / FINE_XP_SCALE
                            add(HiscoreRow(++rank, rs.getString("display_name"), rs.getInt("total_level"), xp))
                        }
                    }
                }
            }
        }

    fun player(name: String, skills: List<SkillInfo>): PlayerHiscoreView? =
        connect { connection ->
            val stats =
                connection.prepareStatement(PLAYER_STATS).use { ps ->
                    ps.setString(1, name.trim())
                    ps.executeQuery().use { rs ->
                        var displayName: String? = null
                        val byStat = HashMap<Int, Pair<Int, Long>>()
                        while (rs.next()) {
                            displayName = rs.getString("display_name")
                            byStat[rs.getInt("stat_id")] = rs.getInt("base_level") to rs.getLong("fine_xp")
                        }
                        displayName?.let { it to byStat }
                    }
                } ?: return@connect null
            val (displayName, byStat) = stats
            val rows =
                skills.map { skill ->
                    val (level, fineXp) = byStat[skill.id] ?: (1 to 0L)
                    val rank = if (fineXp > 0) skillRank(connection, skill.id, fineXp) else null
                    PlayerSkillView(skill.name, rank, level, fineXp / FINE_XP_SCALE)
                }
            val overall =
                PlayerSkillView(
                    skill = OVERALL,
                    rank = null,
                    level = rows.sumOf { it.level },
                    xp = rows.sumOf { it.xp },
                )
            PlayerHiscoreView(displayName, listOf(overall) + rows)
        }

    private fun skillRank(connection: Connection, statId: Int, fineXp: Long): Int =
        connection.prepareStatement(SKILL_RANK).use { ps ->
            ps.setInt(1, statId)
            ps.setLong(2, fineXp)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) + 1 else 1 }
        }

    private fun <T> connect(block: (Connection) -> T): T = config.connect(block)

    companion object {
        const val OVERALL = "overall"
        private const val FINE_XP_SCALE = 10L
        private const val SESSION_STALE_MILLIS = 120_000L

        private val COLLISION_KEY_TAKEN = OpenRuneSql.text("central/account/collision_key_taken.sql")

        private const val INSERT_ACCOUNT =
            "INSERT INTO accounts (account_name, password_hash, email) VALUES (?, ?, ?) " +
                "ON CONFLICT ((lower(account_name))) DO NOTHING"

        private const val ONLINE_BY_WORLD =
            "SELECT world_id, COUNT(*) AS c FROM sessions WHERE last_seen_at > ? GROUP BY world_id " +
                "ORDER BY world_id"

        private const val RANKABLE =
            "FROM main.stats s JOIN account_characters c ON c.id = s.character_id " +
                "JOIN accounts a ON a.id = c.account_id " +
                "WHERE c.display_name IS NOT NULL AND c.banned_until IS NULL AND a.rights = ''"

        private const val SKILL_PAGE =
            "SELECT c.display_name, s.base_level, s.fine_xp $RANKABLE AND s.stat_id = ? " +
                "AND s.fine_xp > 0 ORDER BY s.fine_xp DESC, s.updated_at ASC LIMIT ? OFFSET ?"

        private const val OVERALL_PAGE =
            "SELECT c.display_name, SUM(s.base_level) AS total_level, SUM(s.fine_xp) AS total_xp " +
                "$RANKABLE GROUP BY c.id, c.display_name HAVING SUM(s.fine_xp) > 0 " +
                "ORDER BY total_level DESC, total_xp DESC, MAX(s.updated_at) ASC LIMIT ? OFFSET ?"

        private const val SKILL_RANK = "SELECT COUNT(*) $RANKABLE AND s.stat_id = ? AND s.fine_xp > ?"

        private const val PLAYER_STATS =
            "SELECT c.display_name, s.stat_id, s.base_level, s.fine_xp FROM main.stats s " +
                "JOIN account_characters c ON c.id = s.character_id " +
                "WHERE LOWER(c.display_name) = LOWER(?)"
    }
}
