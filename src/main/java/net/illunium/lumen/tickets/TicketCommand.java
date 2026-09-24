package net.illunium.lumen.tickets;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.illunium.lumen.core.Command;
import net.illunium.lumen.core.CommandRouter;
import net.illunium.lumen.core.Config;
import net.illunium.lumen.notifications.NotificationType;
import net.illunium.lumen.notifications.Notifications;
import net.illunium.lumen.storage.TicketRepository;
import net.illunium.lumen.storage.TicketRepository.Ticket;
import net.illunium.lumen.storage.TicketRepository.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support and report tickets in private channels, as a replacement for a ticket bot.
 *
 * <p>Access is carried by the channel's permission overwrites: {@code @everyone} is denied
 * the channel, the creator, the staff role and the bot are granted it explicitly. Nothing
 * about who may see a ticket is decided in code at read time, so a member who is not on the
 * overwrite list never sees the channel at all.
 *
 * <p>Claim and close state live in the database, not in the channel, so both survive a
 * restart, and both are conditional updates: pressing a button twice does nothing the
 * second time.
 */
public final class TicketCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(TicketCommand.class);

    /** Component IDs, prefixed with the command name so the router finds this command. */
    static final String CREATE = "ticket:create";
    static final String CLAIM = "ticket:claim";
    static final String CLOSE = "ticket:close";

    /** What a participant may do inside a ticket channel. */
    private static final EnumSet<Permission> ACCESS = EnumSet.of(
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_ADD_REACTION);

    /**
     * Grace period between closing a ticket and deleting its channel, so everyone involved
     * can read the last messages. The transcript is archived before the timer starts.
     */
    private static final long DELETE_AFTER_SECONDS = 30;

    /** Messages kept in a transcript. Support tickets are short; the cap bounds the upload. */
    private static final int TRANSCRIPT_LIMIT = 500;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /**
     * Creators whose channel is currently being created, so a double-click on the panel
     * yields one ticket instead of two.
     *
     * <p>ponytail: in memory, single process. A second Lumen on the same guild would need the
     * reservation in the database, and there is no second Lumen.
     */
    private final Set<Long> creating = ConcurrentHashMap.newKeySet();

    private final TicketRepository repository;
    private final Notifications notifications;
    private final Config config;

    public TicketCommand(TicketRepository repository, Notifications notifications, Config config) {
        this.repository = repository;
        this.notifications = notifications;
        this.config = config;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("ticket", "Support- und Report-Tickets")
                .addSubcommands(
                        new SubcommandData("create", "Öffnet ein Ticket")
                                .addOptions(new OptionData(OptionType.STRING, "typ",
                                        "Worum geht es?", true)
                                        .addChoice("Support", Type.SUPPORT.name())
                                        .addChoice("Report", Type.REPORT.name())),
                        new SubcommandData("close", "Schließt dieses Ticket"),
                        new SubcommandData("add", "Holt eine Person in dieses Ticket")
                                .addOption(OptionType.USER, "user", "Wen?", true),
                        new SubcommandData("remove", "Nimmt eine Person aus diesem Ticket")
                                .addOption(OptionType.USER, "user", "Wen?", true))
                .addSubcommandGroups(new SubcommandGroupData("panel", "Ticket-Panel")
                        .addSubcommands(new SubcommandData("create",
                                "Postet das Ticket-Panel in diesen Channel")));
    }

    @Override
    public Set<String> staffOnlySubcommands() {
        // Adding people to a report ticket exposes who reported whom, so it stays with staff.
        return Set.of("add", "remove", "panel create");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Tickets gibt es nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        switch (CommandRouter.subcommandPath(event.getSubcommandGroup(), event.getSubcommandName())) {
            case "create" -> create(event, guild,
                    Type.valueOf(event.getOption("typ", OptionMapping::getAsString)));
            case "close" -> close(event, guild);
            case "add" -> addUser(event);
            case "remove" -> removeUser(event);
            case "panel create" -> createPanel(event);
            default -> event.reply("Unbekannter Unterbefehl.").setEphemeral(true).queue();
        }
    }

    @Override
    public void handleComponent(GenericComponentInteractionCreateEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Tickets gibt es nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        String id = event.getComponentId();
        if (CLAIM.equals(id)) {
            claim(event);
        } else if (CLOSE.equals(id)) {
            close(event, guild);
        } else {
            // The panel ID carries the type, and a panel message outlives every build that
            // posted it, so the value is parsed defensively rather than trusted.
            Optional<Type> type = parseType(id);
            if (type.isEmpty()) {
                log.warn("Unknown ticket component '{}'", id);
                event.reply("Dieses Element kenne ich nicht.").setEphemeral(true).queue();
                return;
            }
            create(event, guild, type.get());
        }
    }

    // --- create ------------------------------------------------------------------------

    private void create(IReplyCallback event, Guild guild, Type type) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("Tickets gibt es nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        Category category = guild.getCategoryById(config.id("channels.tickets-category"));
        if (category == null) {
            log.error("Ticket category {} does not exist on guild {}",
                    config.id("channels.tickets-category"), guild.getId());
            event.reply("Die Ticket-Kategorie ist nicht eingerichtet. Bitte melde dich beim Team.")
                    .setEphemeral(true).queue();
            return;
        }
        long staffRoleId = config.id("roles.staff");
        if (guild.getRoleById(staffRoleId) == null) {
            log.error("Staff role {} does not exist on guild {}", staffRoleId, guild.getId());
            event.reply("Die Staff-Rolle ist nicht eingerichtet. Bitte melde dich beim Team.")
                    .setEphemeral(true).queue();
            return;
        }
        if (!guild.getSelfMember().hasPermission(category,
                Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS)) {
            log.error("Cannot create tickets: missing MANAGE_CHANNEL/MANAGE_PERMISSIONS in category {}",
                    category.getId());
            event.reply("Mir fehlen die Rechte, einen Ticket-Channel anzulegen.")
                    .setEphemeral(true).queue();
            return;
        }

        Optional<Ticket> existing = repository.openByCreator(member.getIdLong());
        if (existing.isPresent()) {
            Ticket ticket = existing.get();
            TextChannel channel = guild.getTextChannelById(ticket.channelId());
            if (channel != null) {
                event.reply("Du hast schon ein offenes Ticket: " + channel.getAsMention())
                        .setEphemeral(true).queue();
                return;
            }
            // The channel was deleted without going through close. This is the only place a
            // stale row would do damage, because it would otherwise block this member from
            // ever opening another ticket.
            // ponytail: reconciled lazily here, no channel-delete listener and no startup
            // sweep. Add one if anything else ever reads open tickets.
            repository.close(ticket.channelId(), null);
            log.warn("Ticket {} closed: its channel {} no longer exists",
                    ticket.name(), ticket.channelId());
        }

        if (!creating.add(member.getIdLong())) {
            event.reply("Dein Ticket wird gerade angelegt.").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        guild.createTextChannel("ticket", category)
                .addRolePermissionOverride(guild.getIdLong(), List.of(), List.of(Permission.VIEW_CHANNEL))
                .addRolePermissionOverride(staffRoleId, ACCESS, List.of())
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(), ACCESS, List.of())
                .addMemberPermissionOverride(member.getIdLong(), ACCESS, List.of())
                .setTopic(type.label() + " – " + member.getUser().getName())
                .reason("Ticket von " + member.getUser().getName())
                .queue(channel -> {
                    creating.remove(member.getIdLong());
                    opened(event, channel, member, type);
                }, error -> {
                    creating.remove(member.getIdLong());
                    log.error("Cannot create ticket channel for {}", member.getId(), error);
                    event.getHook().sendMessage("Das Ticket konnte nicht angelegt werden. "
                            + "Bitte melde dich beim Team.").queue();
                });
    }

    /**
     * Names and furnishes a freshly created channel.
     *
     * <p>The channel exists before the row does, because the row needs its ID, and the ticket
     * number comes from the row. So the channel is created under a placeholder name and
     * renamed once the ticket ID exists — a second call, but no guessed number that a
     * simultaneous second ticket could take.
     */
    private void opened(IReplyCallback event, TextChannel channel, Member member, Type type) {
        Ticket ticket = repository.open(channel.getIdLong(), member.getIdLong(), type);
        channel.getManager().setName(ticket.name()).reason("Ticket-ID").queue(null,
                error -> log.warn("Cannot rename ticket channel {} to {}",
                        channel.getId(), ticket.name(), error));
        channel.sendMessage(member.getAsMention())
                .setEmbeds(Notifications.embed(NotificationType.INFO,
                        ticket.name() + " – " + type.label(),
                        "Schildere dein Anliegen so genau wie möglich. Das Team meldet sich hier."))
                .addActionRow(Button.primary(CLAIM, "Übernehmen"),
                        Button.danger(CLOSE, "Schließen"))
                .queue(null, error -> log.error("Cannot post ticket intro in {}",
                        channel.getId(), error));
        event.getHook().sendMessage("Dein Ticket: " + channel.getAsMention()).queue();
        notifications.send(NotificationType.STAFF, "Ticket eröffnet",
                "%s hat %s eröffnet (%s).".formatted(
                        member.getAsMention(), channel.getAsMention(), type.label()));
        log.info("Ticket {} opened by {} ({})", ticket.name(), member.getId(), type);
    }

    // --- claim -------------------------------------------------------------------------

    private void claim(GenericComponentInteractionCreateEvent event) {
        if (!CommandRouter.isStaff(event, config.id("roles.staff"))) {
            event.reply("Nur das Team kann Tickets übernehmen.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = ticketOf(event);
        if (ticket == null) {
            return;
        }
        if (ticket.closed()) {
            event.reply("Dieses Ticket ist bereits geschlossen.").setEphemeral(true).queue();
            return;
        }
        if (!repository.claim(ticket.channelId(), event.getUser().getIdLong())) {
            Long claimedBy = repository.byChannel(ticket.channelId())
                    .map(Ticket::claimedBy).orElse(null);
            event.reply(claimedBy == null
                            ? "Dieses Ticket kann gerade nicht übernommen werden."
                            : "Das Ticket hat schon <@" + claimedBy + "> übernommen.")
                    .setEphemeral(true).queue();
            return;
        }
        log.info("Ticket {} claimed by {}", ticket.name(), event.getUser().getId());
        event.reply(event.getUser().getAsMention() + " kümmert sich um dieses Ticket.").queue();
    }

    // --- close -------------------------------------------------------------------------

    private void close(IReplyCallback event, Guild guild) {
        Ticket ticket = ticketOf(event);
        if (ticket == null) {
            return;
        }
        boolean staff = CommandRouter.isStaff(event, config.id("roles.staff"));
        if (!staff && event.getUser().getIdLong() != ticket.creatorId()) {
            event.reply("Nur der Ersteller oder das Team kann dieses Ticket schließen.")
                    .setEphemeral(true).queue();
            return;
        }
        if (!repository.close(ticket.channelId(), event.getUser().getIdLong())) {
            event.reply("Dieses Ticket ist bereits geschlossen.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = guild.getTextChannelById(ticket.channelId());
        if (channel == null) {
            event.reply("Ticket geschlossen.").setEphemeral(true).queue();
            return;
        }
        log.info("Ticket {} closed by {}", ticket.name(), event.getUser().getId());
        event.reply("Ticket geschlossen. Der Channel wird in %d Sekunden gelöscht."
                .formatted(DELETE_AFTER_SECONDS)).queue();
        lock(channel, ticket);
        archiveAndDelete(channel, ticket, event.getUser().getName());
    }

    /**
     * Takes writing rights off everyone but staff, so the ticket is frozen the moment it is
     * closed and stays frozen if the deletion below never runs, e.g. after a restart.
     */
    private void lock(TextChannel channel, Ticket ticket) {
        for (PermissionOverride override : channel.getMemberPermissionOverrides()) {
            if (override.getIdLong() != channel.getJDA().getSelfUser().getIdLong()) {
                override.getManager().deny(Permission.MESSAGE_SEND).reason("Ticket geschlossen")
                        .queue(null, error -> log.warn("Cannot lock ticket {} for {}",
                                ticket.name(), override.getId(), error));
            }
        }
    }

    /**
     * Writes the conversation to the staff log and deletes the channel afterwards.
     *
     * <p>The delete is queued only once the transcript has been handed to Discord, so a
     * failing archive leaves the channel standing instead of losing the conversation.
     */
    private void archiveAndDelete(TextChannel channel, Ticket ticket, String closedBy) {
        channel.getIterableHistory().takeAsync(TRANSCRIPT_LIMIT).whenComplete((messages, error) -> {
            if (error != null) {
                log.error("Cannot read history of ticket {}", ticket.name(), error);
                return;
            }
            notifications.send(NotificationType.STAFF, "Ticket geschlossen",
                    "%s (%s) wurde von %s geschlossen. Transcript im Anhang."
                            .formatted(ticket.name(), ticket.type().label(), closedBy),
                    FileUpload.fromData(
                            transcript(ticket, messages).getBytes(StandardCharsets.UTF_8),
                            ticket.name() + ".txt"));
            // ponytail: an in-memory timer. A restart inside the grace period leaves the
            // channel closed and locked instead of deleted, which staff can remove by hand.
            // A scheduled sweep only earns its place if that ever piles up.
            channel.delete().reason("Ticket geschlossen")
                    .queueAfter(DELETE_AFTER_SECONDS, TimeUnit.SECONDS, null,
                            failure -> log.warn("Cannot delete ticket channel {}",
                                    ticket.name(), failure));
        });
    }

    /** Plain text, oldest message first. {@code messages} arrives newest first from Discord. */
    static String transcript(Ticket ticket, List<Message> messages) {
        StringBuilder text = new StringBuilder(ticket.name())
                .append(" (").append(ticket.type().label()).append(")\n")
                .append("Ersteller: ").append(ticket.creatorId()).append('\n')
                .append("Nachrichten: ").append(messages.size()).append("\n\n");
        for (Message message : messages.reversed()) {
            text.append('[').append(STAMP.format(message.getTimeCreated())).append("] ")
                    .append(message.getAuthor().getName()).append(": ")
                    .append(message.getContentDisplay()).append('\n');
            for (Message.Attachment attachment : message.getAttachments()) {
                text.append("    Anhang: ").append(attachment.getUrl()).append('\n');
            }
        }
        return text.toString();
    }

    // --- participants ------------------------------------------------------------------

    private void addUser(SlashCommandInteractionEvent event) {
        Ticket ticket = openTicketOf(event);
        if (ticket == null) {
            return;
        }
        Member target = event.getOption("user", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Diese Person ist nicht auf dem Server.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = (TextChannel) event.getChannel();
        channel.upsertPermissionOverride(target).grant(ACCESS)
                .reason("Zu " + ticket.name() + " hinzugefügt")
                .queue(ok -> event.reply(target.getAsMention() + " hat jetzt Zugriff.").queue(),
                        error -> {
                            log.error("Cannot add {} to ticket {}", target.getId(), ticket.name(), error);
                            event.reply("Das hat nicht geklappt.").setEphemeral(true).queue();
                        });
    }

    private void removeUser(SlashCommandInteractionEvent event) {
        Ticket ticket = openTicketOf(event);
        if (ticket == null) {
            return;
        }
        Member target = event.getOption("user", OptionMapping::getAsMember);
        if (target == null) {
            event.reply("Diese Person ist nicht auf dem Server.").setEphemeral(true).queue();
            return;
        }
        if (target.getIdLong() == ticket.creatorId()) {
            event.reply("Der Ersteller kann nicht entfernt werden. Schließe das Ticket stattdessen.")
                    .setEphemeral(true).queue();
            return;
        }
        TextChannel channel = (TextChannel) event.getChannel();
        PermissionOverride override = channel.getPermissionOverride(target);
        if (override == null) {
            event.reply(target.getAsMention() + " hat ohnehin keinen eigenen Zugriff.")
                    .setEphemeral(true).queue();
            return;
        }
        override.delete().reason("Aus " + ticket.name() + " entfernt")
                .queue(ok -> event.reply(target.getAsMention() + " hat keinen Zugriff mehr.").queue(),
                        error -> {
                            log.error("Cannot remove {} from ticket {}",
                                    target.getId(), ticket.name(), error);
                            event.reply("Das hat nicht geklappt.").setEphemeral(true).queue();
                        });
    }

    // --- panel -------------------------------------------------------------------------

    private void createPanel(SlashCommandInteractionEvent event) {
        event.getChannel()
                .sendMessageEmbeds(Notifications.embed(NotificationType.INFO, "Tickets",
                        "Brauchst du Hilfe oder willst du jemanden melden? "
                                + "Wähle unten aus, dann öffnet sich ein privater Channel "
                                + "nur für dich und das Team."))
                .addActionRow(
                        Button.primary(CREATE + ":" + Type.SUPPORT.name(), "Support"),
                        Button.secondary(CREATE + ":" + Type.REPORT.name(), "Report"))
                .queue(sent -> event.reply("Panel erstellt.").setEphemeral(true).queue(),
                        error -> {
                            log.error("Cannot post ticket panel in {}",
                                    event.getChannel().getId(), error);
                            event.reply("Ich kann in diesem Channel nicht schreiben.")
                                    .setEphemeral(true).queue();
                        });
    }

    // --- shared ------------------------------------------------------------------------

    /** The ticket this interaction happened in, or null after replying why there is none. */
    private Ticket ticketOf(IReplyCallback event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            event.reply("Das hier ist kein Ticket-Channel.").setEphemeral(true).queue();
            return null;
        }
        Optional<Ticket> ticket = repository.byChannel(channel.getIdLong());
        if (ticket.isEmpty()) {
            event.reply("Das hier ist kein Ticket-Channel.").setEphemeral(true).queue();
            return null;
        }
        return ticket.get();
    }

    /** Same, but also refuses a closed ticket, which no longer takes participants. */
    private Ticket openTicketOf(IReplyCallback event) {
        Ticket ticket = ticketOf(event);
        if (ticket == null) {
            return null;
        }
        if (ticket.closed()) {
            event.reply("Dieses Ticket ist geschlossen.").setEphemeral(true).queue();
            return null;
        }
        return ticket;
    }

    /** The ticket type encoded in a panel button's component ID, empty if it is not one. */
    static Optional<Type> parseType(String componentId) {
        if (!componentId.startsWith(CREATE + ":")) {
            return Optional.empty();
        }
        String value = componentId.substring(CREATE.length() + 1);
        for (Type type : Type.values()) {
            if (type.name().equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
