package net.illunium.lumen.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreTest {

    private static final String YAML = """
            guild:
              id: 1234567890
            roles:
              staff: 42
              verified: text
            """;

    private static Config config() {
        return Config.parse(new StringReader(YAML), "test.yml");
    }

    @Test
    void readsNestedIds() {
        assertEquals(1234567890L, config().guildId());
        assertEquals(42L, config().id("roles.staff"));
    }

    @Test
    void missingKeyNamesThePath() {
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> config().id("channels.status"));
        assertTrue(e.getMessage().contains("channels.status"), e.getMessage());
    }

    @Test
    void nonNumericIdFails() {
        assertThrows(IllegalStateException.class, () -> config().id("roles.verified"));
    }

    @Test
    void emptyConfigFails() {
        assertThrows(IllegalStateException.class, () -> Config.parse(new StringReader(""), "test.yml"));
    }

    @Test
    void requireEnvFailsOnMissingValue() {
        assertEquals(System.getenv("PATH"), Config.requireEnv("PATH"));
        assertThrows(IllegalStateException.class, () -> Config.requireEnv("LUMEN_DEFINITELY_UNSET"));
    }

    @Test
    void staffOnlyCommandsNeedTheStaffRole() {
        assertTrue(CommandRouter.isAllowed(false, List.of(), 42L));
        assertTrue(CommandRouter.isAllowed(true, List.of(7L, 42L), 42L));
        assertFalse(CommandRouter.isAllowed(true, List.of(7L), 42L));
        assertFalse(CommandRouter.isAllowed(true, List.of(), 42L));
    }

    @Test
    void uptimeIsHumanReadable() {
        assertEquals("1d 2h 3m", StatusCommand.format(Duration.ofDays(1).plusHours(2).plusMinutes(3)));
    }
}
