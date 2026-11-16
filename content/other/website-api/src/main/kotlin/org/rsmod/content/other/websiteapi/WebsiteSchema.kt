package org.rsmod.content.other.websiteapi

import java.sql.Connection

/**
 * Tables owned by the website and admin APIs. They live in their own `website` schema so Central's
 * migrations never touch them; every statement is idempotent and runs on each server start.
 */
object WebsiteSchema {
    @Volatile private var ensured = false

    @Synchronized
    fun ensure(connection: Connection) {
        if (ensured) {
            return
        }
        connection.createStatement().use { statement ->
            STATEMENTS.forEach(statement::execute)
        }
        ensured = true
    }

    private val STATEMENTS =
        listOf(
            "CREATE SCHEMA IF NOT EXISTS website",
            "CREATE TABLE IF NOT EXISTS website.meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            """
            CREATE TABLE IF NOT EXISTS website.xp_snapshots (
                character_id INTEGER NOT NULL REFERENCES public.account_characters (id) ON DELETE CASCADE,
                day DATE NOT NULL,
                stat_id INTEGER NOT NULL,
                fine_xp BIGINT NOT NULL,
                PRIMARY KEY (character_id, day, stat_id)
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_xp_snapshots_day ON website.xp_snapshots (day)",
            """
            CREATE TABLE IF NOT EXISTS website.announced_levels (
                character_id INTEGER NOT NULL REFERENCES public.account_characters (id) ON DELETE CASCADE,
                stat_id INTEGER NOT NULL,
                announced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (character_id, stat_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS website.reports (
                id BIGSERIAL PRIMARY KEY,
                reporter_account_id INTEGER NULL REFERENCES public.accounts (id) ON DELETE SET NULL,
                reporter_name TEXT NOT NULL,
                offender_name TEXT NOT NULL,
                category TEXT NOT NULL,
                details TEXT NOT NULL,
                source TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'open',
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                handled_by TEXT NULL,
                handled_at TIMESTAMP NULL,
                staff_comment TEXT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_reports_status ON website.reports (status, created_at DESC)",
            """
            CREATE TABLE IF NOT EXISTS website.login_ips (
                account_id INTEGER NOT NULL REFERENCES public.accounts (id) ON DELETE CASCADE,
                character_id INTEGER NOT NULL REFERENCES public.account_characters (id) ON DELETE CASCADE,
                ip TEXT NOT NULL,
                first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                logins INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (character_id, ip)
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_login_ips_ip ON website.login_ips (ip)",
            """
            CREATE TABLE IF NOT EXISTS website.staff_notes (
                id BIGSERIAL PRIMARY KEY,
                character_id INTEGER NOT NULL REFERENCES public.account_characters (id) ON DELETE CASCADE,
                author TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_staff_notes_character ON website.staff_notes (character_id)",
        )
}
