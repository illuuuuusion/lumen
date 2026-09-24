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

Only `core`, `storage`, `notifications`, `roles` and `tickets` exist so far — the
remaining packages are added in their phases.

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

## Notifications

Everything Lumen writes to Discord goes through `Notifications`. A module picks a
`NotificationType` by meaning and never handles a channel ID:

| Type | Channel | Meaning |
|---|---|---|
| `INFO` | `channels.announcements` | neutral information for members |
| `SUCCESS` | `channels.announcements` | something finished as intended |
| `WARNING` | `channels.status` | degraded, not broken |
| `CRITICAL` | `channels.status` | broken and member-visible |
| `MATCH` | `channels.matches` | Kingdoms match traffic |
| `STAFF` | `channels.bot-log` | staff-only: audit, moderation, escalation |

```java
notifications.send(NotificationType.CRITICAL, "Kingdoms offline", "Seit 20:14");
```

`send(type, title, message, file)` attaches a file, e.g. a ticket transcript.

A missing, unknown or unwritable channel drops the message with an explicit log
line and never throws — a broken announcements channel must not take down a
moderation command. `send(type, channelId, ...)` targets an explicit channel for
the cases where the config holds something more specific, such as a per-server
`status-channel`.

## Commands

Commands implement `Command` and are registered on the `CommandRouter`, which
publishes them to the configured guild on startup and handles permission checks
and errors centrally.

A command with `staffOnly()` requires the `roles.staff` role. Discord applies
default permissions per command and not per subcommand, so a command that is
public for members but administrative in parts lists those paths in
`staffOnlySubcommands()` (e.g. `"panel create"`) and the router gates them.

Buttons and select menus are routed by the `<command>:` prefix of their
component ID to `handleComponent`, through the same error handler.

`/help`, `/status`, `/roles` and `/ticket` exist so far.

## Self roles

Members assign themselves roles from a select menu; staff decides which roles
are on offer. The allowed roles live in the `self_roles` table, not in
`config.yml`.

| Command | Who | Effect |
|---|---|---|
| `/roles pick` | everyone | ephemeral menu, current roles preselected |
| `/roles panel create` | staff | posts a public, persistent panel in this channel |
| `/roles allow <role> [label]` | staff | offers a role |
| `/roles deny <role>` | staff | takes it off the menu, keeping its label |
| `/roles list` | staff | every entry and its state |

The selection is the member's desired end state: an offered role left
unselected is removed.

Two guards hold this up, enforced independently:

- Submitted values are never trusted. Every application re-reads `self_roles`
  and intersects, so a forged interaction can name any role ID and get nothing.
- Roles with privileged permissions, `@everyone`, integration-managed roles and
  the staff role are refused — when staff allows them *and again on every
  grant*. The second check is the one that matters: a harmless role allowed last
  week can be given Administrator today.

The bot needs `MANAGE_ROLES` and its highest role must sit above every offered
role; both are checked and reported rather than failing.

## Tickets

Support and report tickets in private channels. Members open one from a panel
button or with `/ticket create`; the state lives in the `tickets` table.

| Command / button | Who | Effect |
|---|---|---|
| panel button `Support` / `Report` | everyone | opens a ticket |
| `/ticket create <typ>` | everyone | the same without a panel |
| button `Übernehmen` | staff | claims it |
| button `Schließen`, `/ticket close` | creator or staff | closes it |
| `/ticket add <user>` | staff | grants access to this ticket channel |
| `/ticket remove <user>` | staff | takes it away again |
| `/ticket panel create` | staff | posts the panel in this channel |

Visibility is carried entirely by the channel's permission overwrites:
`@everyone` is denied `VIEW_CHANNEL`, the creator, the staff role and the bot are
granted it explicitly. No code decides at read time who may see someone else's
ticket.

Claim and close are conditional updates (`WHERE status = 'OPEN'` and
`WHERE status <> 'CLOSED'`), so claim survives a restart and pressing close twice
changes nothing and deletes nothing twice.

The ticket ID is the row's primary key and the channel name (`ticket-0042`). The
channel is created before the row, because the row needs its ID, so it starts
under a placeholder name and is renamed once the ID exists — a guessed number
could have been taken by a simultaneous second ticket.

Closing runs in this order: database, lock the channel, transcript as a `.txt`
into `#bot-log`, delete the channel 30 seconds later. The deletion is only queued
once the transcript is out, so a failing archive leaves the channel standing
instead of losing the conversation.

A ticket channel deleted by hand leaves an `OPEN` row behind. The only place that
hurts is the creator's next ticket, and that is where the row is closed — no
channel-delete listener and no startup sweep. A member has at most one open
ticket; an in-memory guard catches double-clicks on the panel.

`channels.tickets-category` and `roles.staff` must be filled in, and the bot needs
`MANAGE_CHANNEL` plus `MANAGE_PERMISSIONS` in that category.

## Conventions

- Commit messages: English, short, describing the change only.
- Code, identifiers and logs: English. User-facing Discord texts may be German.
