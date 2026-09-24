package net.illunium.lumen.core;

import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/** Lists the registered commands. */
public final class HelpCommand implements Command {

    private final CommandRouter router;

    public HelpCommand(CommandRouter router) {
        this.router = router;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("help", "Zeigt alle verfügbaren Befehle");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String list = router.commands().stream()
                .map(command -> "`/" + command.data().getName() + "` – " + command.data().getDescription())
                .collect(Collectors.joining("\n"));
        event.reply(list).setEphemeral(true).queue();
    }
}
