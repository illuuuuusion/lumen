package net.illunium.lumen.notifications;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.illunium.lumen.core.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place Lumen writes to Discord from.
 *
 * <p>Callers pick a {@link NotificationType} and never a channel ID; the type resolves to
 * a channel through the config. A misconfigured or unreachable channel drops the message
 * with an explicit log line and never propagates: a broken announcements channel must not
 * take down a moderation command or the status poller.
 */
public final class Notifications {

    private static final Logger log = LoggerFactory.getLogger(Notifications.class);

    private final JDA jda;
    private final Config config;

    public Notifications(JDA jda, Config config) {
        this.jda = jda;
        this.config = config;
    }

    /** Sends to the channel configured for {@code type}. */
    public void send(NotificationType type, String title, String message) {
        send(type, title, message, null);
    }

    /**
     * Sends with a file attached, for the cases where the embed only summarises something
     * that has to be archived in full, such as a ticket transcript.
     */
    public void send(NotificationType type, String title, String message, FileUpload file) {
        OptionalLong channelId = config.findId(type.channelKey());
        // 0 is the placeholder in config.example.yml, so an unfilled ID is a missing one.
        if (channelId.isEmpty() || channelId.getAsLong() == 0L) {
            log.error("Dropped {} notification \"{}\": '{}' is not set in the config",
                    type, title, type.channelKey());
            closeQuietly(file);
            return;
        }
        send(type, channelId.getAsLong(), title, message, file);
    }

    /**
     * Sends to an explicit channel, for the cases where the config holds a more specific
     * target than the type's default, such as a per-server {@code status-channel}.
     */
    public void send(NotificationType type, long channelId, String title, String message) {
        send(type, channelId, title, message, null);
    }

    private void send(NotificationType type, long channelId, String title, String message,
            FileUpload file) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            log.error("Dropped {} notification \"{}\": channel {} is not a text channel the bot can see",
                    type, title, channelId);
            closeQuietly(file);
            return;
        }
        if (!channel.canTalk()) {
            log.error("Dropped {} notification \"{}\": missing permission to post in #{}",
                    type, title, channel.getName());
            closeQuietly(file);
            return;
        }
        channel.sendMessageEmbeds(embed(type, title, message))
                .addFiles(file == null ? List.of() : List.of(file))
                .queue(sent -> log.debug("Sent {} notification to #{}", type, channel.getName()),
                        error -> log.error("Failed to send {} notification \"{}\" to #{}",
                                type, title, channel.getName(), error));
    }

    /** A dropped upload still holds an open stream; JDA only closes the ones it sends. */
    private static void closeQuietly(FileUpload file) {
        if (file == null) {
            return;
        }
        try {
            file.close();
        } catch (Exception e) {
            log.warn("Cannot close dropped attachment {}", file.getName(), e);
        }
    }

    /**
     * Builds the shared embed. Public so a module that needs the sent message back, such as
     * a status message it later edits, reuses the same look instead of rebuilding it.
     */
    public static MessageEmbed embed(NotificationType type, String title, String message) {
        return new EmbedBuilder()
                .setColor(type.color())
                .setTitle(truncate(type.emoji() + " " + title, MessageEmbed.TITLE_MAX_LENGTH))
                .setDescription(truncate(message, MessageEmbed.DESCRIPTION_MAX_LENGTH))
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Cuts {@code text} to Discord's limit. Descriptions carry user input such as a warn
     * reason or a ticket subject, and an over-long one would otherwise fail the whole send.
     */
    static String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit - 1) + "…";
    }
}
