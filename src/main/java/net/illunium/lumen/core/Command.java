package net.illunium.lumen.core;

import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** A slash command: its Discord definition, its handler and who may run it. */
public interface Command {

    SlashCommandData data();

    void handle(SlashCommandInteractionEvent event);

    /** When true, only members holding the configured staff role may run it. */
    default boolean staffOnly() {
        return false;
    }

    /**
     * Subcommands that require the staff role even though the command itself is public,
     * named as Discord reports them, e.g. {@code "panel create"} for a subcommand group.
     *
     * <p>Exists because Discord applies default permissions per command, not per
     * subcommand, so a command like {@code /roles} that is public for members but
     * administrative in parts has to be gated here.
     */
    default Set<String> staffOnlySubcommands() {
        return Set.of();
    }

    /**
     * Handles a button or select menu whose component ID belongs to this command.
     *
     * <p>Component IDs follow {@code <command>:<rest>} so the router can dispatch them
     * without a second registry. Commands that ship no components never see this.
     */
    default void handleComponent(GenericComponentInteractionCreateEvent event) {
        throw new UnsupportedOperationException(
                "Command '" + data().getName() + "' has no components");
    }
}
