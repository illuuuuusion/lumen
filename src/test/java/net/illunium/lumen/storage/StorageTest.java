package net.illunium.lumen.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
