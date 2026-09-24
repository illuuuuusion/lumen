package net.illunium.lumen.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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
        List<Long> roleIds = event.getMember() == null
                ? List.of()
                : event.getMember().getRoles().stream().map(Role::getIdLong).toList();
        if (!isAllowed(command.staffOnly(), roleIds, config.id("roles.staff"))) {
            log.info("Rejected /{} for user {}", event.getName(), event.getUser().getId());
            event.reply("Dir fehlen die Rechte für diesen Befehl.").setEphemeral(true).queue();
            return;
        }
        try {
            command.handle(event);
        } catch (RuntimeException e) {
            log.error("Command /{} failed", event.getName(), e);
            String message = "Da ist etwas schiefgelaufen. Bitte melde dich beim Team.";
            if (event.isAcknowledged()) {
                event.getHook().sendMessage(message).setEphemeral(true).queue();
            } else {
                event.reply(message).setEphemeral(true).queue();
            }
        }
    }

    static boolean isAllowed(boolean staffOnly, List<Long> memberRoleIds, long staffRoleId) {
        return !staffOnly || memberRoleIds.contains(staffRoleId);
    }
}
