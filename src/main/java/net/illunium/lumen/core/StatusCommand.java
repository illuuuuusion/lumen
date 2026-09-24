package net.illunium.lumen.core;

import java.time.Duration;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** Reports the bot's own health. */
public final class StatusCommand implements Command {

    private final HealthState health;

    public StatusCommand(HealthState health) {
        this.health = health;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("status", "Zeigt den Status des Bots");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String state = health.ready() ? "verbunden" : "nicht verbunden";
        event.reply("Status: %s\nLaufzeit: %s\nGateway: %d ms"
                        .formatted(state, format(health.uptime()), event.getJDA().getGatewayPing()))
                .setEphemeral(true)
                .queue();
    }

    static String format(Duration uptime) {
        return "%dd %dh %dm".formatted(uptime.toDays(), uptime.toHoursPart(), uptime.toMinutesPart());
    }
}
