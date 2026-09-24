package net.illunium.lumen.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.illunium.lumen.core.Command;
import net.illunium.lumen.moderation.ModerationCommand.Action;
import net.illunium.lumen.moderation.ModerationCommand.Target;
import net.illunium.lumen.storage.WarningRepository.Warning;
import org.junit.jupiter.api.Test;

class ModerationTest {

    private static final long MODERATOR = 1L;
    private static final long BOT = 2L;
    private static final long MEMBER = 3L;

    /** A regular member of the server that both the moderator and the bot outrank. */
    private static Target ordinary() {
        return new Target(MEMBER, true, true, true);
    }

    private static String refuse(Action action, Target target) {
        return ModerationCommand.refuse(action, target, MODERATOR, BOT, true);
    }

    @Test
    void everyModerationCommandIsStaffOnlyAndPublishesItsOwnName() {
        List<Command> commands = ModerationCommand.all(null, null);
        assertEquals(Set.of("warn", "warnings", "timeout", "kick", "ban"),
                commands.stream().map(c -> c.data().getName()).collect(Collectors.toSet()));
        for (Command command : commands) {
            assertTrue(command.staffOnly(), command.data().getName() + " must not be public");
            assertTrue(command.staffOnlySubcommands().isEmpty(),
                    "the whole command is gated, so no subcommand needs gating");
        }
    }

    @Test
    void onlyTheActionsThatTouchDiscordDeclareAPermission() {
        assertNull(Action.WARN.permission(), "a warning is a database row, not a Discord action");
        assertNull(Action.WARNINGS.permission());
        assertEquals(Permission.MODERATE_MEMBERS, Action.TIMEOUT.permission());
        assertEquals(Permission.KICK_MEMBERS, Action.KICK.permission());
        assertEquals(Permission.BAN_MEMBERS, Action.BAN.permission());
    }

    @Test
    void anOrdinaryMemberCanBeModerated() {
        for (Action action : List.of(Action.WARN, Action.TIMEOUT, Action.KICK, Action.BAN)) {
            assertNull(refuse(action, ordinary()), action + " should have been allowed");
        }
    }

    @Test
    void nobodyModeratesThemselvesOrTheBot() {
        for (Action action : List.of(Action.WARN, Action.TIMEOUT, Action.KICK, Action.BAN)) {
            assertNotNull(refuse(action, new Target(MODERATOR, true, true, true)), action.toString());
            assertNotNull(refuse(action, new Target(BOT, true, true, true)), action.toString());
        }
    }

    @Test
    void aModeratorCannotReachSomeoneAboveThemselves() {
        Target above = new Target(MEMBER, true, true, false);
        for (Action action : List.of(Action.WARN, Action.TIMEOUT, Action.KICK, Action.BAN)) {
            String refusal = refuse(action, above);
            assertNotNull(refusal, action.toString());
            assertTrue(refusal.contains("unter dir"), refusal);
        }
    }

    @Test
    void theBotRefusesWhatDiscordHierarchyPutsAboveIt() {
        Target aboveTheBot = new Target(MEMBER, true, false, true);
        for (Action action : List.of(Action.TIMEOUT, Action.KICK, Action.BAN)) {
            String refusal = refuse(action, aboveTheBot);
            assertNotNull(refusal, action + " must not be attempted against a higher role");
            assertTrue(refusal.contains("über mir"), refusal);
        }
        assertNull(refuse(Action.WARN, aboveTheBot),
                "a warning writes a row, so the bot's own role position is irrelevant");
    }

    @Test
    void aMissingBotPermissionIsNamedInsteadOfAttempted() {
        for (Action action : List.of(Action.TIMEOUT, Action.KICK, Action.BAN)) {
            String refusal =
                    ModerationCommand.refuse(action, ordinary(), MODERATOR, BOT, false);
            assertNotNull(refusal, action.toString());
            assertTrue(refusal.contains(action.permission().getName()),
                    "the moderator is told which permission is missing: " + refusal);
        }
        assertNull(ModerationCommand.refuse(Action.WARN, ordinary(), MODERATOR, BOT, false),
                "a warning needs no Discord permission at all");
    }

    @Test
    void onlyABanReachesSomeoneWhoAlreadyLeft() {
        // Hierarchy is meaningless for a non-member, so both flags are true.
        Target gone = new Target(MEMBER, false, true, true);
        assertNull(refuse(Action.BAN, gone), "banning someone who left must stay possible");
        for (Action action : List.of(Action.WARN, Action.TIMEOUT, Action.KICK)) {
            assertNotNull(refuse(action, gone), action + " needs the person on the server");
        }
    }

    @Test
    void anEmptyWarningListSaysSoInsteadOfShowingNothing() {
        assertEquals("Keine Verwarnungen.", ModerationCommand.render(List.of(), 0));
    }

    @Test
    void theWarningListReportsTheTrueTotalEvenWhenItIsCapped() {
        List<Warning> shown = List.of(warning(9), warning(8));
        assertTrue(ModerationCommand.render(shown, 2).startsWith("Insgesamt 2 Verwarnungen:"));
        assertTrue(ModerationCommand.render(shown, 17).startsWith("Die neuesten 2 von 17 Verwarnungen:"),
                "a capped list must not look like the whole history");
        assertTrue(ModerationCommand.render(List.of(warning(1)), 1)
                .startsWith("Insgesamt 1 Verwarnung:"), "singular, not \"1 Verwarnungen\"");

        String text = ModerationCommand.render(shown, 2);
        assertTrue(text.contains("`#9`"), text);
        assertTrue(text.contains("<@7>"), "the moderator is named: " + text);
        assertTrue(text.contains("Spam"), text);
    }

    private static Warning warning(long id) {
        return new Warning(id, MEMBER, 7L, "Spam", Instant.parse("2026-09-24T10:00:00Z"));
    }
}
