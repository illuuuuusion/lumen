package net.illunium.lumen.moderation;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.illunium.lumen.core.Command;
import net.illunium.lumen.notifications.NotificationType;
import net.illunium.lumen.notifications.Notifications;
import net.illunium.lumen.storage.WarningRepository;
import net.illunium.lumen.storage.WarningRepository.Warning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic moderation: warn, warnings, timeout, kick and ban.
 *
 * <p>One class serves all five commands, one instance per {@link Action}, because they
 * differ only in their options and their one effect — the guard chain, the audit entry and
 * the error handling are identical and are written once.
 *
 * <p>Every command is staff-only, so the router refuses members before the handler runs.
 * On top of that, each action is checked against the Discord role hierarchy twice: the
 * target must stand below the moderator, and below the bot whenever the bot has to act on
 * Discord. Replies are ephemeral; the audit trail is {@code #bot-log}, so moderation does
 * not leak into whatever channel the command was typed in.
 */
public final class ModerationCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(ModerationCommand.class);

    /** Warnings shown by {@code /warnings}; the total is reported separately. */
    private static final int LIST_LIMIT = 10;

    /** Discord's own ceiling on a timeout, {@value} days, expressed in minutes. */
    private static final long MAX_TIMEOUT_MINUTES = Member.MAX_TIME_OUT_LENGTH * 24L * 60L;

    /** Discord rejects an audit-log reason over 512 characters, so the option is capped below it. */
    private static final int REASON_MAX_LENGTH = 400;

    private static final String NO_REASON = "Kein Grund angegeben";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /**
     * One moderation command. {@code permission} is the Discord permission the bot needs to
     * carry it out, or null when the action never touches Discord.
     */
    public enum Action {
        WARN("warn", "Verwarnt eine Person", null, "verwarnt"),
        WARNINGS("warnings", "Zeigt die Verwarnungen einer Person", null, null),
        TIMEOUT("timeout", "Schaltet eine Person zeitweise stumm",
                Permission.MODERATE_MEMBERS, "stummgeschaltet"),
        KICK("kick", "Wirft eine Person vom Server", Permission.KICK_MEMBERS, "gekickt"),
        BAN("ban", "Bannt eine Person vom Server", Permission.BAN_MEMBERS, "gebannt");

        private final String name;
        private final String description;
        private final Permission permission;
        private final String pastTense;

        Action(String name, String description, Permission permission, String pastTense) {
            this.name = name;
            this.description = description;
            this.permission = permission;
            this.pastTense = pastTense;
        }

        /** The Discord permission the bot needs, or null if the action only writes a row. */
        public Permission permission() {
            return permission;
        }
    }

    /**
     * What the guards need to know about the target, separated from JDA so the decision is
     * testable on its own.
     *
     * <p>{@code member} is false when the user is not on the server, in which case both
     * hierarchy flags are meaningless and are set to true by the caller.
     */
    record Target(long id, boolean member, boolean botOutranks, boolean moderatorOutranks) {
    }

    private final Action action;
    private final WarningRepository warnings;
    private final Notifications notifications;

    public ModerationCommand(Action action, WarningRepository warnings,
            Notifications notifications) {
        this.action = action;
        this.warnings = warnings;
        this.notifications = notifications;
    }

    /** One command per action, ready to register on the router. */
    public static List<Command> all(WarningRepository warnings, Notifications notifications) {
        return List.of(Action.values()).stream()
                .map(action -> (Command) new ModerationCommand(action, warnings, notifications))
                .toList();
    }

    @Override
    public SlashCommandData data() {
        OptionData user = new OptionData(OptionType.USER, "user", "Wen?", true);
        SlashCommandData command = Commands.slash(action.name, action.description).addOptions(user);
        if (action == Action.TIMEOUT) {
            command.addOptions(new OptionData(OptionType.INTEGER, "minuten",
                    "Wie lange? (1 bis 40320 Minuten)", true)
                    .setRequiredRange(1, MAX_TIMEOUT_MINUTES));
        }
        if (action != Action.WARNINGS) {
            // Discord enforces the length itself, so no reason can ever overflow the audit log.
            command.addOptions(new OptionData(OptionType.STRING, "grund", "Warum?",
                    action == Action.WARN).setMaxLength(REASON_MAX_LENGTH));
        }
        return command;
    }

    @Override
    public boolean staffOnly() {
        return true;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Moderation gibt es nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        OptionMapping option = event.getOption("user");
        if (option == null) {
            event.reply("Bitte gib eine Person an.").setEphemeral(true).queue();
            return;
        }
        User target = option.getAsUser();
        if (action == Action.WARNINGS) {
            listWarnings(event, target);
            return;
        }

        // The member comes from the interaction payload, not the cache, so this works without
        // the privileged GUILD_MEMBERS intent. It is null when the user is not on the server.
        Member member = option.getAsMember();
        Member moderator = event.getMember();
        Member self = guild.getSelfMember();
        String refusal = refuse(action,
                new Target(target.getIdLong(), member != null,
                        member == null || self.canInteract(member),
                        member == null || moderator == null || moderator.canInteract(member)),
                event.getUser().getIdLong(), self.getIdLong(),
                action.permission() == null || self.hasPermission(action.permission()));
        if (refusal != null) {
            event.reply(refusal).setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("grund", NO_REASON, OptionMapping::getAsString);
        switch (action) {
            case WARN -> warn(event, target, reason);
            case TIMEOUT -> timeout(event, member, target, reason);
            case KICK -> apply(event, guild.kick(target), target, reason, "");
            case BAN -> apply(event, guild.ban(target, 0, TimeUnit.SECONDS), target, reason, "");
            default -> throw new IllegalStateException("Unhandled action " + action);
        }
    }

    /**
     * Why {@code action} must not run against this target, or null if it may.
     *
     * <p>Order matters: the cheap identity checks come first, then membership, then the
     * moderator's own standing, and only then what the bot is able to do. That way a
     * moderator aiming at someone above themselves is told so rather than being told the
     * bot lacks a permission.
     */
    static String refuse(Action action, Target target, long moderatorId, long botId,
            boolean botHasPermission) {
        if (target.id() == moderatorId) {
            return "Dich selbst kannst du nicht moderieren.";
        }
        if (target.id() == botId) {
            return "Mich selbst kann ich nicht moderieren.";
        }
        // A ban is the one action that still works on someone who already left.
        if (!target.member() && action != Action.BAN) {
            return "Diese Person ist nicht auf dem Server.";
        }
        if (!target.moderatorOutranks()) {
            return "Diese Person steht in der Rollenhierarchie nicht unter dir.";
        }
        if (action.permission() == null) {
            // /warn only writes a row, so the bot's own standing does not matter.
            return null;
        }
        if (!botHasPermission) {
            return "Mir fehlt das Recht „%s“ auf diesem Server."
                    .formatted(action.permission().getName());
        }
        if (!target.botOutranks()) {
            return "Diese Person steht in der Rollenhierarchie über mir.";
        }
        return null;
    }

    // --- actions -----------------------------------------------------------------------

    private void warn(SlashCommandInteractionEvent event, User target, String reason) {
        int total = warnings.add(target.getIdLong(), event.getUser().getIdLong(), reason);
        // ponytail: no escalation ladder. The count is reported and staff decides; automatic
        // timeouts at n warnings only make sense once someone has asked for a threshold.
        done(event, target, reason, " Das ist Verwarnung #%d.".formatted(total));
    }

    private void timeout(SlashCommandInteractionEvent event, Member member, User target,
            String reason) {
        long minutes = event.getOption("minuten", OptionMapping::getAsLong);
        apply(event, member.timeoutFor(Duration.ofMinutes(minutes)), target, reason,
                " für %d Minuten".formatted(minutes));
    }

    /** Runs the Discord side, then reports and audits it. A failure never leaves silence. */
    private void apply(SlashCommandInteractionEvent event, AuditableRestAction<Void> request,
            User target, String reason, String detail) {
        event.deferReply(true).queue();
        request.reason(event.getUser().getName() + ": " + reason).queue(
                ok -> done(event, target, reason, detail),
                error -> {
                    log.error("/{} on {} failed", action.name, target.getId(), error);
                    event.getHook().sendMessage(
                            "Discord hat die Aktion abgelehnt. Bitte prüfe meine Rechte und "
                                    + "meine Rollenposition.").queue();
                });
    }

    /**
     * The single exit for a successful action: confirm to the moderator and write the audit
     * entry. Every command lands here, so nothing can succeed without being logged.
     */
    private void done(SlashCommandInteractionEvent event, User target, String reason,
            String detail) {
        String message = "%s wurde%s %s. Grund: %s"
                .formatted(target.getAsMention(), detail, action.pastTense, reason);
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(message).queue();
        } else {
            event.reply(message).setEphemeral(true).queue();
        }
        notifications.send(NotificationType.STAFF, action.pastTense.substring(0, 1).toUpperCase()
                        + action.pastTense.substring(1),
                "%s hat %s (`%s`)%s %s.\nGrund: %s".formatted(
                        event.getUser().getAsMention(), target.getAsMention(), target.getId(),
                        detail, action.pastTense, reason));
        log.info("/{} {} by {}: {}", action.name, target.getId(), event.getUser().getId(), reason);
    }

    // --- warnings ----------------------------------------------------------------------

    private void listWarnings(SlashCommandInteractionEvent event, User target) {
        int total = warnings.count(target.getIdLong());
        event.replyEmbeds(Notifications.embed(NotificationType.STAFF,
                        "Verwarnungen von " + target.getName(),
                        render(warnings.of(target.getIdLong(), LIST_LIMIT), total)))
                .setEphemeral(true).queue();
    }

    /** The warning list as embed text. {@code shown} arrives newest first. */
    static String render(List<Warning> shown, int total) {
        if (total == 0) {
            return "Keine Verwarnungen.";
        }
        StringBuilder text = new StringBuilder();
        if (shown.size() < total) {
            text.append("Die neuesten %d von %d Verwarnungen:\n\n".formatted(shown.size(), total));
        } else {
            text.append("Insgesamt %d Verwarnung%s:\n\n".formatted(total, total == 1 ? "" : "en"));
        }
        for (Warning warning : shown) {
            text.append("`#%d` %s von <@%d>\n%s\n\n".formatted(warning.id(),
                    STAMP.format(warning.createdAt()), warning.moderatorId(), warning.reason()));
        }
        return text.toString();
    }
}
