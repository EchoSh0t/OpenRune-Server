package org.rsmod.content.other.websiteapi

import java.sql.Connection
import java.time.LocalDate
import org.rsmod.api.db.DatabaseConfig

enum class GainPeriod(val id: String, val days: Long) {
    Day("day", 1),
    Week("week", 7),
    Month("month", 30);

    fun since(today: LocalDate): LocalDate = today.minusDays(days - 1)

    companion object {
        fun parse(id: String?): GainPeriod? = entries.firstOrNull { it.id == id }
    }
}

data class LevelMilestone(val characterId: Int, val name: String, val statId: Int)

/**
 * Daily XP snapshots behind the XP tracker, and the level-99 milestones the Discord announcer
 * posts. A snapshot keeps the first value seen each day, so "gained today" is current XP minus the
 * day's first snapshot.
 */
class TrackerDatabase(private val config: () -> DatabaseConfig) {
    fun ensureSchema() {
        config.connect(WebsiteSchema::ensure)
    }

    fun snapshot() {
        config.connect { connection ->
            connection.createStatement().use { it.executeUpdate(INSERT_SNAPSHOT) }
            connection.createStatement().use { it.executeUpdate(PRUNE_SNAPSHOTS) }
        }
    }

    fun gainsPage(period: GainPeriod, statId: Int?, page: Int, pageSize: Int): List<GainRow> =
        config.connect { connection ->
            val skillFilter = if (statId == null) "" else "AND s.stat_id = ?"
            connection.prepareStatement(gainsSql(skillFilter)).use { ps ->
                var i = 0
                ps.setObject(++i, period.since(LocalDate.now()))
                statId?.let { ps.setInt(++i, it) }
                ps.setInt(++i, pageSize)
                ps.setInt(++i, page * pageSize)
                ps.executeQuery().use { rs ->
                    buildList {
                        var rank = page * pageSize
                        while (rs.next()) {
                            add(GainRow(++rank, rs.getString("display_name"), rs.getLong("gained") / FINE_XP_SCALE))
                        }
                    }
                }
            }
        }

    fun playerGains(name: String, skills: List<SkillInfo>, statId: Int?, historyDays: Int): PlayerGainsView? =
        config.connect { connection ->
            val (characterId, displayName) =
                connection.prepareStatement(SELECT_CHARACTER).use { ps ->
                    ps.setString(1, name.trim())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) to rs.getString(2) else null
                    }
                } ?: return@connect null
            val current = currentXp(connection, characterId)
            val today = LocalDate.now()
            val baselines = GainPeriod.entries.associateWith { baselines(connection, characterId, it.since(today)) }
            val rows =
                skills.map { skill ->
                    val xp = current[skill.id] ?: 0L
                    SkillGainView(
                        skill = skill.name,
                        xp = xp / FINE_XP_SCALE,
                        day = gained(xp, baselines.getValue(GainPeriod.Day)[skill.id]),
                        week = gained(xp, baselines.getValue(GainPeriod.Week)[skill.id]),
                        month = gained(xp, baselines.getValue(GainPeriod.Month)[skill.id]),
                    )
                }
            val overall =
                SkillGainView(
                    skill = "Overall",
                    xp = rows.sumOf { it.xp },
                    day = rows.sumOf { it.day },
                    week = rows.sumOf { it.week },
                    month = rows.sumOf { it.month },
                )
            val history = history(connection, characterId, statId, today.minusDays(historyDays - 1L))
            val now = statId?.let { current[it] } ?: current.values.sum()
            val points = history.filterKeys { it != today } + (today to now)
            PlayerGainsView(
                name = displayName,
                skills = listOf(overall) + rows,
                history = points.toSortedMap().map { (day, xp) -> HistoryPoint(day.toString(), xp / FINE_XP_SCALE) },
            )
        }

    /**
     * Records milestones that haven't been announced yet and returns the ones newly recorded. The
     * first run on an empty database records everything silently, so existing 99s aren't posted.
     */
    fun claimMilestones(skillCount: Int): List<LevelMilestone> =
        config.connect { connection ->
            val seeded =
                connection.prepareStatement(SELECT_META).use { ps ->
                    ps.setString(1, MILESTONES_SEEDED)
                    ps.executeQuery().use { it.next() }
                }
            val found = pending(connection, SELECT_NEW_99S, null) + pending(connection, SELECT_NEW_MAXED, skillCount)
            val claimed = found.filter { claim(connection, it) }
            if (!seeded) {
                connection.prepareStatement(INSERT_META).use { ps ->
                    ps.setString(1, MILESTONES_SEEDED)
                    ps.setString(2, "true")
                    ps.executeUpdate()
                }
                return@connect emptyList()
            }
            claimed
        }

    private fun pending(connection: Connection, sql: String, skillCount: Int?): List<LevelMilestone> =
        connection.prepareStatement(sql).use { ps ->
            skillCount?.let { ps.setInt(1, it) }
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(LevelMilestone(rs.getInt("character_id"), rs.getString("display_name"), rs.getInt("stat_id")))
                    }
                }
            }
        }

    private fun claim(connection: Connection, milestone: LevelMilestone): Boolean =
        connection.prepareStatement(INSERT_ANNOUNCED).use { ps ->
            ps.setInt(1, milestone.characterId)
            ps.setInt(2, milestone.statId)
            ps.executeUpdate() > 0
        }

    private fun currentXp(connection: Connection, characterId: Int): Map<Int, Long> =
        connection.prepareStatement(SELECT_CURRENT).use { ps ->
            ps.setInt(1, characterId)
            ps.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getInt("stat_id"), rs.getLong("fine_xp"))
                    }
                }
            }
        }

    private fun baselines(connection: Connection, characterId: Int, since: LocalDate): Map<Int, Long> =
        connection.prepareStatement(SELECT_BASELINES).use { ps ->
            ps.setInt(1, characterId)
            ps.setObject(2, since)
            ps.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getInt("stat_id"), rs.getLong("fine_xp"))
                    }
                }
            }
        }

    private fun history(connection: Connection, characterId: Int, statId: Int?, since: LocalDate): Map<LocalDate, Long> {
        val skillFilter = if (statId == null) "" else "AND stat_id = ?"
        val sql =
            "SELECT day, SUM(fine_xp) AS xp FROM website.xp_snapshots WHERE character_id = ? " +
                "AND day >= ? $skillFilter GROUP BY day"
        return connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, characterId)
            ps.setObject(2, since)
            statId?.let { ps.setInt(3, it) }
            ps.executeQuery().use { rs ->
                buildMap {
                    while (rs.next()) {
                        put(rs.getObject("day", LocalDate::class.java), rs.getLong("xp"))
                    }
                }
            }
        }
    }

    private fun gained(current: Long, baseline: Long?): Long =
        if (baseline == null) 0 else ((current - baseline) / FINE_XP_SCALE).coerceAtLeast(0)

    private companion object {
        const val FINE_XP_SCALE = 10L
        const val MILESTONES_SEEDED = "milestones_seeded"
        const val SNAPSHOT_RETENTION_DAYS = 400

        const val RANKABLE =
            "JOIN account_characters c ON c.id = s.character_id JOIN accounts a ON a.id = c.account_id " +
                "WHERE c.display_name IS NOT NULL AND c.banned_until IS NULL AND a.rights = ''"

        const val INSERT_SNAPSHOT =
            "INSERT INTO website.xp_snapshots (character_id, day, stat_id, fine_xp) " +
                "SELECT character_id, CURRENT_DATE, stat_id, fine_xp FROM main.stats " +
                "ON CONFLICT (character_id, day, stat_id) DO NOTHING"

        const val PRUNE_SNAPSHOTS =
            "DELETE FROM website.xp_snapshots WHERE day < CURRENT_DATE - $SNAPSHOT_RETENTION_DAYS"

        const val BASELINE_CTE =
            "WITH base AS (SELECT DISTINCT ON (character_id, stat_id) character_id, stat_id, fine_xp " +
                "FROM website.xp_snapshots WHERE day >= ? ORDER BY character_id, stat_id, day) "

        fun gainsSql(skillFilter: String): String =
            BASELINE_CTE +
                "SELECT c.display_name, SUM(s.fine_xp - b.fine_xp) AS gained FROM main.stats s " +
                "JOIN base b ON b.character_id = s.character_id AND b.stat_id = s.stat_id " +
                "$RANKABLE $skillFilter GROUP BY c.id, c.display_name " +
                "HAVING SUM(s.fine_xp - b.fine_xp) > 0 ORDER BY gained DESC, c.display_name LIMIT ? OFFSET ?"

        const val SELECT_CHARACTER =
            "SELECT id, display_name FROM account_characters WHERE LOWER(display_name) = LOWER(?)"

        const val SELECT_CURRENT = "SELECT stat_id, fine_xp FROM main.stats WHERE character_id = ?"

        const val SELECT_BASELINES =
            "SELECT DISTINCT ON (stat_id) stat_id, fine_xp FROM website.xp_snapshots " +
                "WHERE character_id = ? AND day >= ? ORDER BY stat_id, day"

        const val SELECT_META = "SELECT value FROM website.meta WHERE key = ?"

        const val INSERT_META =
            "INSERT INTO website.meta (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING"

        const val SELECT_NEW_99S =
            "SELECT s.character_id, c.display_name, s.stat_id FROM main.stats s $RANKABLE " +
                "AND s.base_level >= 99 AND NOT EXISTS (SELECT 1 FROM website.announced_levels l " +
                "WHERE l.character_id = s.character_id AND l.stat_id = s.stat_id)"

        const val SELECT_NEW_MAXED =
            "SELECT s.character_id, c.display_name, -1 AS stat_id FROM main.stats s $RANKABLE " +
                "AND NOT EXISTS (SELECT 1 FROM website.announced_levels l " +
                "WHERE l.character_id = s.character_id AND l.stat_id = -1) " +
                "GROUP BY s.character_id, c.display_name " +
                "HAVING COUNT(*) FILTER (WHERE s.base_level >= 99) >= ?"

        const val INSERT_ANNOUNCED =
            "INSERT INTO website.announced_levels (character_id, stat_id) VALUES (?, ?) " +
                "ON CONFLICT DO NOTHING"
    }
}
