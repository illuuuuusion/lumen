package net.illunium.lumen.notifications;

/**
 * Kind of message Lumen sends, and where it goes.
 *
 * <p>The type carries its target channel as a config path, so a module picks a type by
 * meaning and never handles a channel ID itself. Which channel a type resolves to is
 * changed in {@code config.yml}, not here.
 */
public enum NotificationType {

    /** Neutral information for members, e.g. a project update. */
    INFO("channels.announcements", 0x5865F2, "ℹ️"),

    /** Something completed as intended and members should know. */
    SUCCESS("channels.announcements", 0x57F287, "✅"),

    /** Degraded but not broken, e.g. a server responding slowly. */
    WARNING("channels.status", 0xFEE75C, "⚠️"),

    /** Broken and member-visible, e.g. a server that stopped responding. */
    CRITICAL("channels.status", 0xED4245, "🔴"),

    /** Kingdoms match traffic: check-in, start, result, bracket. */
    MATCH("channels.matches", 0xEB459E, "⚔️"),

    /** Staff-only: audit log, moderation actions, escalations members should not see. */
    STAFF("channels.bot-log", 0x99AAB5, "🛠️");

    private final String channelKey;
    private final int color;
    private final String emoji;

    NotificationType(String channelKey, int color, String emoji) {
        this.channelKey = channelKey;
        this.color = color;
        this.emoji = emoji;
    }

    /** Dotted config path of the channel this type is routed to. */
    public String channelKey() {
        return channelKey;
    }

    public int color() {
        return color;
    }

    public String emoji() {
        return emoji;
    }
}
