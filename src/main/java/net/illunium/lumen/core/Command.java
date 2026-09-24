package net.illunium.lumen.core;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** A slash command: its Discord definition, its handler and who may run it. */
public interface Command {

    SlashCommandData data();

    void handle(SlashCommandInteractionEvent event);

    /** When true, only members holding the configured staff role may run it. */
    default boolean staffOnly() {
        return false;
    }
}
