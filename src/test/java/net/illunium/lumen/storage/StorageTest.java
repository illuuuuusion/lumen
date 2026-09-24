package net.illunium.lumen.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.illunium.lumen.storage.TicketRepository.Status;
import net.illunium.lumen.storage.TicketRepository.Ticket;
import net.illunium.lumen.storage.TicketRepository.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {

    @Test
    void freshDatabaseIsMigratedToTheLatestVersion(@TempDir Path dir) throws SQLException {
        Path file = dir.resolve("nested").resolve("lumen.db");
        try (Database database = Database.open(file)) {
            assertEquals(Schema.MIGRATIONS.size(), database.version());
            assertTrue(Files.isRegularFile(file), "database file was not created");
        }
    }

    @Test
    void migrationsAreIdempotentAndDataSurvivesRestart(@TempDir Path dir) throws SQLException {
        Path file = dir.resolve("lumen.db");
        try (Database database = Database.open(file)) {
            new SelfRoleRepository(database).put(42L, "North", true);
        }
        try (Database database = Database.open(file)) {
            assertEquals(Schema.MIGRATIONS.size(), database.version());
            assertEquals(List.of(new SelfRoleRepository.SelfRole(42L, "North", true)),
                    new SelfRoleRepository(database).enabled());
        }
    }

    @Test
    void refusesADatabaseFromANewerBuild(@TempDir Path dir) throws SQLException {
        Path file = dir.resolve("lumen.db");
        try (Database database = Database.open(file);
                Statement statement = database.connection().createStatement()) {
            statement.execute("PRAGMA user_version = " + (Schema.MIGRATIONS.size() + 1));
        }
        assertThrows(IllegalStateException.class, () -> Database.open(file));
    }

    @Test
    void selfRolesRoundTrip(@TempDir Path dir) {
        try (Database database = Database.open(dir.resolve("lumen.db"))) {
            SelfRoleRepository roles = new SelfRoleRepository(database);
            roles.put(1L, "South", true);
            roles.put(2L, "East", true);
            roles.put(1L, "North", true);

            assertEquals(List.of("East", "North"), roles.enabled().stream().map(r -> r.label()).toList());

            assertTrue(roles.disable(2L));
            assertFalse(roles.disable(2L), "disabling twice is a no-op, not an error");
            assertEquals(List.of("North"), roles.enabled().stream().map(r -> r.label()).toList());
            assertEquals(List.of("East", "North"), roles.all().stream().map(r -> r.label()).toList(),
                    "a disabled role keeps its label and stays visible to staff");

            roles.put(2L, "East", true);
            assertEquals(List.of("East", "North"), roles.enabled().stream().map(r -> r.label()).toList(),
                    "allowing it again brings it back");

            assertFalse(roles.disable(999L), "a role that was never stored cannot be disabled");
        }
    }

    @Test
    void ticketLifecycleSurvivesARestartAndRepeatedButtons(@TempDir Path dir) {
        Path file = dir.resolve("lumen.db");
        long channel = 500L;
        long creator = 99L;
        long staff = 7L;

        try (Database database = Database.open(file)) {
            TicketRepository tickets = new TicketRepository(database);
            Ticket first = tickets.open(channel, creator, Type.SUPPORT);
            assertEquals("ticket-0001", first.name(), "the ticket ID is the channel name");
            assertEquals(Status.OPEN, first.status());

            assertEquals(Optional.of(first), tickets.openByCreator(creator));
            assertTrue(tickets.openByCreator(creator + 1).isEmpty());

            assertTrue(tickets.claim(channel, staff));
            assertFalse(tickets.claim(channel, staff + 1),
                    "a second claim must not steal the ticket");
        }

        try (Database database = Database.open(file)) {
            TicketRepository tickets = new TicketRepository(database);
            Ticket reopened = tickets.byChannel(channel).orElseThrow();
            assertEquals(Status.CLAIMED, reopened.status(), "claim state survives a restart");
            assertEquals(staff, reopened.claimedBy());

            assertTrue(tickets.close(channel, staff));
            assertFalse(tickets.close(channel, staff), "close is idempotent");

            Ticket closed = tickets.byChannel(channel).orElseThrow();
            assertTrue(closed.closed());
            assertTrue(tickets.openByCreator(creator).isEmpty(),
                    "a closed ticket no longer blocks the next one");

            Ticket second = tickets.open(channel + 1, creator, Type.REPORT);
            assertEquals("ticket-0002", second.name(), "ticket IDs keep counting up");
            assertEquals(Type.REPORT, second.type());
            assertNull(second.claimedBy());
        }
    }

    @Test
    void aTicketWhoseChannelVanishedCanBeClosedWithoutACloser(@TempDir Path dir) {
        try (Database database = Database.open(dir.resolve("lumen.db"))) {
            TicketRepository tickets = new TicketRepository(database);
            tickets.open(500L, 99L, Type.SUPPORT);

            assertTrue(tickets.close(500L, null), "nobody closed it, the channel just went away");
            assertTrue(tickets.byChannel(500L).orElseThrow().closed());
            assertTrue(tickets.openByCreator(99L).isEmpty(),
                    "the stale row must not block the member forever");
        }
    }

    @Test
    void warningsAccumulatePerMemberAndSurviveARestart(@TempDir Path dir) {
        Path file = dir.resolve("lumen.db");
        long target = 99L;
        long moderator = 7L;

        try (Database database = Database.open(file)) {
            WarningRepository warnings = new WarningRepository(database);
            assertEquals(0, warnings.count(target));
            assertEquals(List.of(), warnings.of(target, 10));

            assertEquals(1, warnings.add(target, moderator, "Spam"));
            assertEquals(2, warnings.add(target, moderator, "Beleidigung"),
                    "add reports the running total, which is what the moderator is told");
            assertEquals(1, warnings.add(target + 1, moderator, "Spam"),
                    "warnings are counted per member, not globally");
        }

        try (Database database = Database.open(file)) {
            WarningRepository warnings = new WarningRepository(database);
            assertEquals(2, warnings.count(target), "warnings survive a restart");

            List<WarningRepository.Warning> all = warnings.of(target, 10);
            assertEquals(List.of("Beleidigung", "Spam"),
                    all.stream().map(WarningRepository.Warning::reason).toList(),
                    "newest first");
            assertEquals(moderator, all.getFirst().moderatorId());
            assertTrue(all.getFirst().createdAt().isBefore(Instant.now().plusSeconds(1)));

            assertEquals(1, warnings.of(target, 1).size(), "the display cap is honoured");
            assertEquals(2, warnings.count(target), "but the cap never changes the true total");
        }
    }

    @Test
    void schemaConstraintsRejectBadRows(@TempDir Path dir) throws SQLException {
        try (Database database = Database.open(dir.resolve("lumen.db"));
                Statement statement = database.connection().createStatement()) {
            statement.execute("""
                    INSERT INTO minecraft_links
                      (discord_user_id, minecraft_uuid, minecraft_name, created_at, updated_at)
                    VALUES (1, 'uuid-a', 'Steve', '2026-09-24T10:00:00Z', '2026-09-24T10:00:00Z')
                    """);
            assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO minecraft_links
                      (discord_user_id, minecraft_uuid, minecraft_name, created_at, updated_at)
                    VALUES (2, 'uuid-a', 'Alex', '2026-09-24T10:00:00Z', '2026-09-24T10:00:00Z')
                    """), "the same Minecraft UUID must not link to two Discord users");

            assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO tickets (channel_id, creator_id, type, status, created_at)
                    VALUES (10, 20, 'QUESTION', 'OPEN', '2026-09-24T10:00:00Z')
                    """), "unknown ticket type must be rejected");
        }
    }
}
