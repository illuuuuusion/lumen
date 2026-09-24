package net.illunium.lumen.notifications;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.illunium.lumen.core.Config;
import org.junit.jupiter.api.Test;

class NotificationsTest {

    private static final String YAML = """
            guild:
              id: 1
            channels:
              announcements: 100
              status: 0
              matches: 300
            """;

    private static Config config() {
        return Config.parse(new StringReader(YAML), "test.yml");
    }

    @Test
    void everyTypeRoutesToAChannelKey() {
        for (NotificationType type : NotificationType.values()) {
            assertTrue(type.channelKey().startsWith("channels."),
                    type + " must route to a channel, got " + type.channelKey());
            assertNotNull(type.emoji(), type + " has no emoji");
        }
    }

    @Test
    void typesAreVisuallyDistinguishable() {
        long colors = Arrays.stream(NotificationType.values())
                .map(NotificationType::color)
                .distinct()
                .count();
        assertEquals(NotificationType.values().length, colors, "two types share a colour");
    }

    @Test
    void embedCarriesTypeColourAndText() {
        MessageEmbed embed = Notifications.embed(NotificationType.CRITICAL, "Kingdoms offline", "Seit 20:14");
        assertEquals(NotificationType.CRITICAL.color(), embed.getColorRaw());
        assertEquals("Seit 20:14", embed.getDescription());
        assertTrue(embed.getTitle().contains("Kingdoms offline"), embed.getTitle());
        assertTrue(embed.getTitle().startsWith(NotificationType.CRITICAL.emoji()), embed.getTitle());
        assertNotNull(embed.getTimestamp());
    }

    @Test
    void overlongUserTextIsCutInsteadOfFailingTheSend() {
        String reason = "x".repeat(MessageEmbed.DESCRIPTION_MAX_LENGTH + 500);
        MessageEmbed embed = Notifications.embed(NotificationType.STAFF, "Warn", reason);
        assertEquals(MessageEmbed.DESCRIPTION_MAX_LENGTH, embed.getDescription().length());
        assertTrue(embed.getDescription().endsWith("…"));

        assertEquals("ab…", Notifications.truncate("abcdef", 3));
        assertEquals("abc", Notifications.truncate("abc", 3), "text at the limit stays untouched");
    }

    @Test
    void unconfiguredChannelDropsTheMessageInsteadOfCrashing() {
        // A null JDA would blow up the moment a channel is resolved, which is exactly the
        // point: these two types must bail out on the config before they ever touch Discord.
        Notifications notifications = new Notifications(null, config());

        assertDoesNotThrow(() -> notifications.send(NotificationType.WARNING, "Degraded", "slow"),
                "channels.status is 0 (placeholder) and must be treated as unset");
        assertDoesNotThrow(() -> notifications.send(NotificationType.STAFF, "Audit", "entry"),
                "channels.bot-log is absent from the config entirely");
    }

    @Test
    void configuredChannelKeysResolveToTheirIds() {
        Config config = config();
        assertEquals(100L, config.findId(NotificationType.INFO.channelKey()).orElseThrow());
        assertEquals(300L, config.findId(NotificationType.MATCH.channelKey()).orElseThrow());
        assertTrue(config.findId("channels.bot-log").isEmpty());
    }

    @Test
    void requiredChannelKeysAreDocumentedInTheExampleConfig() throws Exception {
        String example = java.nio.file.Files.readString(java.nio.file.Path.of("config.example.yml"));
        String missing = Arrays.stream(NotificationType.values())
                .map(type -> type.channelKey().substring("channels.".length()))
                .distinct()
                .filter(key -> !example.contains(key + ":"))
                .collect(Collectors.joining(", "));
        assertEquals("", missing, "config.example.yml is missing channel keys: " + missing);
    }
}
