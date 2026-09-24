package net.illunium.lumen;

import java.time.Duration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.illunium.lumen.core.CommandRouter;
import net.illunium.lumen.core.Config;
import net.illunium.lumen.core.HealthState;
import net.illunium.lumen.core.HelpCommand;
import net.illunium.lumen.core.StatusCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point: loads the configuration, starts the JDA client and
 * shuts it down cleanly.
 */
public final class Lumen {

    private static final Logger log = LoggerFactory.getLogger(Lumen.class);

    public static void main(String[] args) throws InterruptedException {
        Config config = Config.load();
        String token = Config.requireEnv("DISCORD_TOKEN");

        HealthState health = new HealthState();
        CommandRouter router = new CommandRouter(config, health);
        router.register(new HelpCommand(router));
        router.register(new StatusCommand(health));

        JDA jda = JDABuilder.createDefault(token).addEventListeners(router).build();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(jda), "lumen-shutdown"));

        jda.awaitReady();
        log.info("Lumen connected to Discord");
    }

    private static void shutdown(JDA jda) {
        log.info("Shutting down");
        jda.shutdown();
        try {
            if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                log.warn("Graceful shutdown timed out, forcing");
                jda.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
    }

    private Lumen() {
    }
}
