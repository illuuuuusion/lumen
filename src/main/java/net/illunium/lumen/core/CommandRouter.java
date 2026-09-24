package net.illunium.lumen.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the slash commands on the configured guild and routes interactions to them,
 * including the permission check and the shared error handler.
 */
public final class CommandRouter extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Config config;
    private final HealthState health;

    public CommandRouter(Config config, HealthState health) {
        this.config = config;
        this.health = health;
    }

    public CommandRouter register(Command command) {
        commands.put(command.data().getName(), command);
        return this;
    }

    public Collection<Command> commands() {
        return commands.values();
    }

    @Override
    public void onReady(ReadyEvent event) {
        health.markReady(true);
        Guild guild = event.getJDA().getGuildById(config.guildId());
        if (guild == null) {
            log.error("Configured guild {} not found - commands not registered", config.guildId());
            return;
        }
        // ponytail: guild-scoped registration, updates instantly. Global commands only if
        // Lumen ever serves more than the Illunium guild.
        guild.updateCommands()
                .addCommands(commands.values().stream().map(Command::data).toList())
                .queue(registered -> log.info("Registered {} commands on guild {}",
                        registered.size(), guild.getName()));
    }

    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        health.markReady(false);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Command command = commands.get(event.getName());
        if (command == null) {
            log.warn("Unknown command: {}", event.getName());
            event.reply("Diesen Befehl kenne ich nicht.").setEphemeral(true).queue();
            return;
        }
        String path = subcommandPath(event.getSubcommandGroup(), event.getSubcommandName());
        boolean staffOnly = needsStaff(command, path);
        if (!isAllowed(staffOnly, memberRoleIds(event), config.id("roles.staff"))) {
            log.info("Rejected /{}{} for user {}", event.getName(),
                    path.isEmpty() ? "" : " " + path, event.getUser().getId());
            event.reply("Dir fehlen die Rechte für diesen Befehl.").setEphemeral(true).queue();
            return;
        }
        run(event, "/" + event.getName(), () -> command.handle(event));
    }

    /**
     * Routes a button or select menu to the command that owns it, by the {@code <command>:}
     * prefix of its ID, through the same error handling as a slash command.
     */
    @Override
    public void onGenericComponentInteractionCreate(GenericComponentInteractionCreateEvent event) {
        String id = event.getComponentId();
        Command command = commands.get(id.split(":", 2)[0]);
        if (command == null) {
            // A panel from an older build, or a command that no longer exists.
            log.warn("No command owns component '{}'", id);
            event.reply("Dieses Element ist nicht mehr aktiv.").setEphemeral(true).queue();
            return;
        }
        run(event, "component " + id, () -> command.handleComponent(event));
    }

    /** Shared error handler: an interaction never dies silently and never kills the bot. */
    private void run(IReplyCallback event, String what, Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException e) {
            log.error("{} failed", what, e);
            String message = "Da ist etwas schiefgelaufen. Bitte melde dich beim Team.";
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            } else {
                event.reply(message).setEphemeral(true).queue();
            }
        }
    }

    private static List<Long> memberRoleIds(Interaction event) {
        return event.getMember() == null
                ? List.of()
                : event.getMember().getRoles().stream().map(Role::getIdLong).toList();
    }

    /** {@code "group sub"}, {@code "sub"} or {@code ""}, matching {@link Command#staffOnlySubcommands()}. */
    public static String subcommandPath(String group, String name) {
        if (name == null) {
            return "";
        }
        return group == null ? name : group + " " + name;
    }

    static boolean needsStaff(Command command, String subcommandPath) {
        return command.staffOnly() || command.staffOnlySubcommands().contains(subcommandPath);
    }

    static boolean isAllowed(boolean staffOnly, List<Long> memberRoleIds, long staffRoleId) {
        return !staffOnly || memberRoleIds.contains(staffRoleId);
    }
}
