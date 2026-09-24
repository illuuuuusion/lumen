# Lumen

Discord bot for the Illunium community: roles, tickets, moderation basics,
Minecraft status, account linking and Kingdoms integration.

The implementation plan lives in [Umsetzungsplan.md](Umsetzungsplan.md), the
MVP concept in [DISCORD_BOT_MVP_LUMEN.md](DISCORD_BOT_MVP_LUMEN.md).

## Stack

| | |
|---|---|
| Language | Java 25 |
| Discord | JDA 5 |
| Build | Gradle (Kotlin DSL) |
| Storage | SQLite (`sqlite-jdbc`, no ORM) |
| Logging | SLF4J + Logback to stdout |

## Architecture

A single deployable modular monolith. Business boundaries are packages under
`net.illunium.lumen`, not separate Gradle subprojects:

```text
core           startup, JDA client, config, secrets, commands, permissions
storage        SQLite connection, schema, repositories
notifications  central Discord output layer
identity       Discord <-> Minecraft account linking
roles          self roles and role management
tickets        ticket lifecycle
minecraft      server status
kingdoms       kingdom role sync, match mirroring
monitoring     outage detection and reporting
moderation     warn, timeout, kick, ban
web            static UI bundle, Discord OAuth login, config API
```

Kingdoms stays the source of truth for gameplay state; Lumen only mirrors it.

Only `core` and `storage` exist so far — the remaining packages are added in
their phases.

## Local setup

Requires a JDK 25 on `PATH` (or let the Gradle toolchain provision one).

```bash
cp .env.example .env           # fill in DISCORD_TOKEN
cp config.example.yml config.yml   # fill in guild, channel and role IDs
./gradlew build                # compile + tests
```

Run the bot with the token exported:

```bash
set -a && . ./.env && set +a
./gradlew run
```

Build a distributable archive instead:

```bash
./gradlew installDist
DISCORD_TOKEN=... ./build/install/lumen/bin/lumen
```

## Configuration

Secrets come from environment variables only and are never committed. See
[.env.example](.env.example) for the full list.

| Variable | Purpose |
|---|---|
| `DISCORD_TOKEN` | Discord bot token (required) |
| `LUMEN_INTERNAL_API_SECRET` | shared secret for the internal event API |
| `LUMEN_CONFIG_PATH` | path to the non-sensitive config file |
| `LUMEN_DATABASE_PATH` | path to the SQLite database file |

Non-sensitive Discord IDs (guild, channels, roles) live in `config.yml`
(see [config.example.yml](config.example.yml)), never hardcoded in commands or
event handlers. Look them up via `config.id("channels.status")`.

## Storage

SQLite, one file, no ORM and no migration framework. `Database.open()` creates
the file (and its directory) if missing, sets WAL plus a busy timeout, and
applies every pending migration on startup.

The applied schema version is SQLite's own `PRAGMA user_version`, so there is no
bookkeeping table. `Schema.MIGRATIONS` is append-only: each entry is one
version, and an entry that has shipped is never edited — corrections arrive as a
new entry. A database at a *higher* version than the build knows is refused at
startup rather than silently used.

SQL belongs in a repository under `storage`, never in a command or event
handler. `SelfRoleRepository` is the reference implementation; the repositories
for the other tables follow its shape and arrive with the phase that needs them.

## Commands

Commands implement `Command` and are registered on the `CommandRouter`, which
publishes them to the configured guild on startup and handles permission checks
and errors centrally. A command with `staffOnly()` requires the `roles.staff`
role. `/help` and `/status` exist so far.

## Conventions

- Commit messages: English, short, describing the change only.
- Code, identifiers and logs: English. User-facing Discord texts may be German.
