package net.illunium.lumen.storage;

import java.util.List;

/**
 * Versioned migrations. Each entry is one version, applied in order and tracked in
 * SQLite's own {@code PRAGMA user_version}.
 *
 * <p>Migrations are append-only: an entry that has shipped is never edited or removed,
 * because existing databases have already applied it. Corrections arrive as a new entry.
 *
 * <p>Timestamps are stored as ISO-8601 text. SQLite has no date type, and ISO-8601 sorts
 * and compares lexicographically, so plain {@code TEXT} is both correct and readable.
 */
final class Schema {

    // ponytail: no `users` table. The plan lists one but states no requirements for it;
    // Discord is the user registry and every table below keys off the Discord user ID
    // directly. Add it when something actually needs per-user rows of its own.
    static final List<List<String>> MIGRATIONS = List.of(
            List.of(
                    """
                    CREATE TABLE minecraft_links (
                      discord_user_id INTEGER PRIMARY KEY,
                      minecraft_uuid  TEXT NOT NULL UNIQUE,
                      minecraft_name  TEXT NOT NULL,
                      created_at      TEXT NOT NULL,
                      updated_at      TEXT NOT NULL
                    )
                    """,
                    """
                    CREATE TABLE tickets (
                      id         INTEGER PRIMARY KEY AUTOINCREMENT,
                      channel_id INTEGER NOT NULL UNIQUE,
                      creator_id INTEGER NOT NULL,
                      type       TEXT NOT NULL CHECK (type IN ('SUPPORT', 'REPORT')),
                      status     TEXT NOT NULL CHECK (status IN ('OPEN', 'CLAIMED', 'CLOSED')),
                      claimed_by INTEGER,
                      closed_by  INTEGER,
                      closed_at  TEXT,
                      created_at TEXT NOT NULL
                    )
                    """,
                    """
                    CREATE TABLE warnings (
                      id           INTEGER PRIMARY KEY AUTOINCREMENT,
                      target_id    INTEGER NOT NULL,
                      moderator_id INTEGER NOT NULL,
                      reason       TEXT NOT NULL,
                      created_at   TEXT NOT NULL
                    )
                    """,
                    "CREATE INDEX idx_warnings_target ON warnings (target_id)",
                    """
                    CREATE TABLE self_roles (
                      role_id INTEGER PRIMARY KEY,
                      label   TEXT NOT NULL,
                      enabled INTEGER NOT NULL DEFAULT 1
                    )
                    """,
                    """
                    CREATE TABLE server_status (
                      server_key            TEXT PRIMARY KEY,
                      state                 TEXT NOT NULL,
                      last_change           TEXT NOT NULL,
                      last_successful_check TEXT
                    )
                    """,
                    """
                    CREATE TABLE kingdom_role_state (
                      discord_user_id INTEGER PRIMARY KEY,
                      minecraft_uuid  TEXT NOT NULL,
                      kingdom_id      TEXT NOT NULL,
                      last_sync       TEXT NOT NULL
                    )
                    """));

    private Schema() {
    }
}
