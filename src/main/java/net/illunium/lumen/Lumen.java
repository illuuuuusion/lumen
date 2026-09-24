package net.illunium.lumen;

import net.dv8tion.jda.api.JDABuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 *
 * <p>Baseline only: connects to Discord with the token from the environment.
 * Config loading, shutdown handling and command registration follow in phase 1.
 */
public final class Lumen {

    private static final Logger log = LoggerFactory.getLogger(Lumen.class);

    public static void main(String[] args) throws InterruptedException {
        String token = requireEnv("DISCORD_TOKEN");

        JDABuilder.createDefault(token).build().awaitReady();
        log.info("Lumen connected to Discord");
    }

    static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private Lumen() {
    }
}
