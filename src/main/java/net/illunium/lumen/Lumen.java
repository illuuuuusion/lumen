package net.illunium.lumen;

import java.time.Duration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.illunium.lumen.core.CommandRouter;
import net.illunium.lumen.core.Config;
import net.illunium.lumen.core.HealthState;
import net.illunium.lumen.core.HelpCommand;
import net.illunium.lumen.core.StatusCommand;
import net.illunium.lumen.moderation.ModerationCommand;
import net.illunium.lumen.notifications.NotificationType;
import net.illunium.lumen.notifications.Notifications;
import net.illunium.lumen.roles.SelfRolesCommand;
import net.illunium.lumen.storage.Database;
import net.illunium.lumen.storage.SelfRoleRepository;
import net.illunium.lumen.storage.TicketRepository;
import net.illunium.lumen.storage.WarningRepository;
import net.illunium.lumen.tickets.TicketCommand;
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
        // Notification channels are checked per message and only log when unset. These three
        // are not optional: the permission layer hangs off roles.staff, and a ticket has
        // nowhere to be created without its category.
        config.requireIds("guild.id", "roles.staff", "channels.tickets-category");
        String token = Config.requireEnv("DISCORD_TOKEN");
        Database database = Database.open();

        HealthState health = new HealthState();
        CommandRouter router = new CommandRouter(config, health);
        router.register(new HelpCommand(router));
        router.register(new StatusCommand(health));

        // Notifications need the JDA instance, so this command is registered after build().
        // The router stays attached from build() on, because a missed ReadyEvent would mean
        // no commands are published at all; the gap until register() is a few microseconds
        // against a gateway handshake.
        JDA jda = JDABuilder.createDefault(token).addEventListeners(router).build();
        Notifications notifications = new Notifications(jda, config);
        router.register(new SelfRolesCommand(
                new SelfRoleRepository(database), notifications, config));
        router.register(new TicketCommand(
                new TicketRepository(database), notifications, config));
        ModerationCommand.all(new WarningRepository(database), notifications)
                .forEach(router::register);
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> shutdown(jda, database, notifications), "lumen-shutdown"));

        jda.awaitReady();
        log.info("Lumen connected to Discord");
        notifications.send(NotificationType.STAFF, "Lumen gestartet",
                "Der Bot ist verbunden und bereit.");
    }

    private static void shutdown(JDA jda, Database database, Notifications notifications) {
        log.info("Shutting down");
        // Queued before shutdown() on purpose: it drains pending requests, shutdownNow() does not.
        notifications.send(NotificationType.STAFF, "Lumen wird beendet",
                "Der Bot fährt kontrolliert herunter.");
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
        database.close();
    }

    private Lumen() {
    }
}
