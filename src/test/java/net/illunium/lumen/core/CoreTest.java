package net.illunium.lumen.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
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
    void placeholderIdsFailStartupAndNameThemselves() {
        Config config = Config.parse(new StringReader("""
                guild:
                  id: 1234567890
                roles:
                  staff: 0
                """), "test.yml");

        assertDoesNotThrow(() -> config.requireIds("guild.id"));

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> config.requireIds("guild.id", "roles.staff"));
        assertTrue(e.getMessage().contains("roles.staff"), e.getMessage());
        assertFalse(e.getMessage().contains("guild.id"), "only the unfilled keys are named: " + e.getMessage());

        assertThrows(IllegalStateException.class, () -> config.requireIds("channels.status"),
                "an absent key counts as unfilled too");
    }

    @Test
    void subcommandPathMatchesHowCommandsDeclareIt() {
        assertEquals("", CommandRouter.subcommandPath(null, null));
        assertEquals("pick", CommandRouter.subcommandPath(null, "pick"));
        assertEquals("panel create", CommandRouter.subcommandPath("panel", "create"));
    }

    @Test
    void aPublicCommandCanStillGateSingleSubcommands() {
        Command roles = new Command() {
            @Override
            public SlashCommandData data() {
                return Commands.slash("roles", "Self Roles");
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
            }

            @Override
            public Set<String> staffOnlySubcommands() {
                return Set.of("allow", "panel create");
            }
        };
        assertFalse(CommandRouter.needsStaff(roles, ""), "the command itself is public");
        assertFalse(CommandRouter.needsStaff(roles, "pick"));
        assertTrue(CommandRouter.needsStaff(roles, "allow"));
        assertTrue(CommandRouter.needsStaff(roles, "panel create"));
        assertFalse(CommandRouter.needsStaff(roles, "panel"),
                "a group without its subcommand is not the gated path");
    }

    @Test
    void aStaffOnlyCommandGatesEverySubcommand() {
        Command staffCommand = new Command() {
            @Override
            public SlashCommandData data() {
                return Commands.slash("audit", "Staff only");
            }

            @Override
            public void handle(SlashCommandInteractionEvent event) {
            }

            @Override
            public boolean staffOnly() {
                return true;
            }
        };
        assertTrue(CommandRouter.needsStaff(staffCommand, ""));
        assertTrue(CommandRouter.needsStaff(staffCommand, "anything"));
    }

    @Test
    void uptimeIsHumanReadable() {
        assertEquals("1d 2h 3m", StatusCommand.format(Duration.ofDays(1).plusHours(2).plusMinutes(3)));
    }
}
