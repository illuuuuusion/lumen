package net.illunium.lumen.roles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.illunium.lumen.core.Command;
import net.illunium.lumen.core.CommandRouter;
import net.illunium.lumen.core.Config;
import net.illunium.lumen.notifications.NotificationType;
import net.illunium.lumen.notifications.Notifications;
import net.illunium.lumen.storage.SelfRoleRepository;
import net.illunium.lumen.storage.SelfRoleRepository.SelfRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Self-assignable roles through a select menu, as a replacement for a reaction-role bot.
 *
 * <p>Two rules carry this command and are enforced independently of each other:
 *
 * <ul>
 *   <li>Only roles stored in {@code self_roles} are ever granted. The values a member
 *       submits are intersected with a fresh read of that table, so a forged interaction
 *       can name any role ID it likes and still get nothing.
 *   <li>A role carrying privileged permissions is refused, both when staff allows it and
 *       again every time it would be granted. The second check is the one that matters:
 *       a harmless role allowed last week can be given Administrator today.
 * </ul>
 */
public final class SelfRolesCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(SelfRolesCommand.class);

    /** Component ID of the select menu, prefixed with the command name for the router. */
    static final String PICK = "roles:pick";

    /** Discord refuses a select menu with more than 25 options. */
    private static final int MAX_OPTIONS = 25;

    /**
     * Permissions that make a role unsafe to hand out. Deliberately broad: a self role is
     * cosmetic, so anything that touches members, messages or server settings is out.
     */
    private static final Set<Permission> PRIVILEGED = Set.of(
            Permission.ADMINISTRATOR,
            Permission.MANAGE_SERVER,
            Permission.MANAGE_ROLES,
            Permission.MANAGE_PERMISSIONS,
            Permission.MANAGE_CHANNEL,
            Permission.MANAGE_WEBHOOKS,
            Permission.MANAGE_THREADS,
            Permission.MANAGE_EVENTS,
            Permission.MANAGE_GUILD_EXPRESSIONS,
            Permission.VIEW_AUDIT_LOGS,
            Permission.KICK_MEMBERS,
            Permission.BAN_MEMBERS,
            Permission.MODERATE_MEMBERS,
            Permission.NICKNAME_MANAGE,
            Permission.MESSAGE_MANAGE,
            Permission.MESSAGE_MENTION_EVERYONE);

    private final SelfRoleRepository repository;
    private final Notifications notifications;
    private final Config config;

    public SelfRolesCommand(SelfRoleRepository repository, Notifications notifications, Config config) {
        this.repository = repository;
        this.notifications = notifications;
        this.config = config;
    }

    @Override
    public SlashCommandData data() {
        return Commands.slash("roles", "Self Roles")
                .addSubcommands(
                        new SubcommandData("pick", "Wähle deine Rollen"),
                        new SubcommandData("allow", "Gibt eine Rolle als Self Role frei")
                                .addOption(OptionType.ROLE, "role", "Die Rolle", true)
                                .addOption(OptionType.STRING, "label", "Name im Panel", false),
                        new SubcommandData("deny", "Nimmt eine Rolle aus den Self Roles")
                                .addOption(OptionType.ROLE, "role", "Die Rolle", true),
                        new SubcommandData("list", "Zeigt alle konfigurierten Self Roles"))
                .addSubcommandGroups(new SubcommandGroupData("panel", "Self-Role-Panel")
                        .addSubcommands(new SubcommandData("create",
                                "Postet das Panel in diesen Channel")));
    }

    @Override
    public Set<String> staffOnlySubcommands() {
        return Set.of("allow", "deny", "list", "panel create");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Self Roles gibt es nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        switch (CommandRouter.subcommandPath(event.getSubcommandGroup(), event.getSubcommandName())) {
            case "pick" -> pick(event, guild);
            case "panel create" -> createPanel(event, guild);
            case "allow" -> allow(event, guild);
            case "deny" -> deny(event, guild);
            case "list" -> list(event, guild);
            default -> event.reply("Unbekannter Unterbefehl.").setEphemeral(true).queue();
        }
    }

    @Override
    public void handleComponent(GenericComponentInteractionCreateEvent event) {
        if (!(event instanceof StringSelectInteractionEvent select) || !PICK.equals(event.getComponentId())) {
            event.reply("Dieses Element kenne ich nicht.").setEphemeral(true).queue();
            return;
        }
        apply(select);
    }

    // --- member facing ---------------------------------------------------------------

    /** Ephemeral menu with the member's current roles preselected. */
    private void pick(SlashCommandInteractionEvent event, Guild guild) {
        List<Role> options = assignableRoles(guild);
        if (options.isEmpty()) {
            event.reply("Es sind noch keine Self Roles freigegeben.").setEphemeral(true).queue();
            return;
        }
        event.reply("Wähle deine Rollen. Was du nicht auswählst, wird entfernt.")
                .addActionRow(menu(options, event.getMember()))
                .setEphemeral(true)
                .queue();
    }

    /**
     * Applies a submitted selection. The selection is the member's desired end state, so
     * a self role that is allowed but unselected is removed.
     */
    private void apply(StringSelectInteractionEvent select) {
        Guild guild = select.getGuild();
        Member member = select.getMember();
        if (guild == null || member == null) {
            select.reply("Self Roles gibt es nur auf dem Server.").setEphemeral(true).queue();
            return;
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            log.error("Cannot apply self roles: bot lacks MANAGE_ROLES on guild {}", guild.getId());
            select.reply("Mir fehlt das Recht, Rollen zu vergeben. Bitte melde dich beim Team.")
                    .setEphemeral(true).queue();
            return;
        }

        // Never trust the submitted IDs: intersect them with what is allowed right now.
        Set<Long> wanted = parseIds(select.getValues());
        List<Role> add = new ArrayList<>();
        List<Role> remove = new ArrayList<>();
        List<String> blocked = new ArrayList<>();

        for (Role role : assignableRoles(guild, blocked)) {
            boolean has = member.getRoles().contains(role);
            boolean wants = wanted.contains(role.getIdLong());
            if (wants && !has) {
                add.add(role);
            } else if (!wants && has) {
                remove.add(role);
            }
        }

        if (add.isEmpty() && remove.isEmpty()) {
            select.reply(blocked.isEmpty() ? "Nichts geändert." : blockedNote(blocked))
                    .setEphemeral(true).queue();
            return;
        }
        select.deferReply(true).queue();
        guild.modifyMemberRoles(member, add, remove)
                .reason("Self Roles")
                .queue(ok -> {
                    log.info("Self roles for {}: +{} -{}", member.getId(), add.size(), remove.size());
                    select.getHook().sendMessage(summary(names(add), names(remove)) + blockedNote(blocked)).queue();
                }, error -> {
                    log.error("Failed to apply self roles for {}", member.getId(), error);
                    select.getHook()
                            .sendMessage("Das hat nicht geklappt. Bitte melde dich beim Team.")
                            .queue();
                });
    }

    // --- staff facing ----------------------------------------------------------------

    private void createPanel(SlashCommandInteractionEvent event, Guild guild) {
        List<Role> options = assignableRoles(guild);
        if (options.isEmpty()) {
            event.reply("Es sind noch keine Self Roles freigegeben. Erst `/roles allow`.")
                    .setEphemeral(true).queue();
            return;
        }
        // No preselection: a public panel is shared, so it cannot show per-member state.
        event.getChannel()
                .sendMessageEmbeds(Notifications.embed(NotificationType.INFO, "Self Roles",
                        "Wähle deine Rollen. Was du nicht auswählst, wird entfernt."))
                .addActionRow(menu(options, null))
                .queue(sent -> event.reply("Panel erstellt.").setEphemeral(true).queue(),
                        error -> {
                            log.error("Cannot post self role panel in {}", event.getChannel().getId(), error);
                            event.reply("Ich kann in diesem Channel nicht schreiben.")
                                    .setEphemeral(true).queue();
                        });
    }

    private void allow(SlashCommandInteractionEvent event, Guild guild) {
        Role role = event.getOption("role", OptionMapping::getAsRole);
        Optional<String> unsafe = unsafeReason(role);
        if (unsafe.isPresent()) {
            event.reply("Abgelehnt: " + unsafe.get()).setEphemeral(true).queue();
            return;
        }
        if (!guild.getSelfMember().canInteract(role)) {
            event.reply("Abgelehnt: " + role.getName()
                            + " steht über meiner höchsten Rolle, ich könnte sie nicht vergeben.")
                    .setEphemeral(true).queue();
            return;
        }
        String label = event.getOption("label", role.getName(), OptionMapping::getAsString);
        repository.put(role.getIdLong(), label, true);
        log.info("Self role allowed: {} ({}) by {}", role.getName(), role.getId(), event.getUser().getId());
        notifications.send(NotificationType.STAFF, "Self Role freigegeben",
                event.getUser().getAsMention() + " hat " + role.getAsMention()
                        + " als Self Role freigegeben (`" + label + "`).");
        event.reply("`" + label + "` ist jetzt eine Self Role.").setEphemeral(true).queue();
    }

    private void deny(SlashCommandInteractionEvent event, Guild guild) {
        Role role = event.getOption("role", OptionMapping::getAsRole);
        if (!repository.disable(role.getIdLong())) {
            event.reply(role.getName() + " ist keine aktive Self Role.").setEphemeral(true).queue();
            return;
        }
        log.info("Self role denied: {} ({}) by {}", role.getName(), role.getId(), event.getUser().getId());
        notifications.send(NotificationType.STAFF, "Self Role entfernt",
                event.getUser().getAsMention() + " hat " + role.getAsMention()
                        + " aus den Self Roles entfernt. Bereits vergebene Rollen bleiben bestehen.");
        event.reply(role.getName() + " ist keine Self Role mehr. Wer sie hat, behält sie.")
                .setEphemeral(true).queue();
    }

    private void list(SlashCommandInteractionEvent event, Guild guild) {
        List<SelfRole> all = repository.all();
        if (all.isEmpty()) {
            event.reply("Es sind keine Self Roles konfiguriert.").setEphemeral(true).queue();
            return;
        }
        String lines = all.stream().map(self -> {
            Role role = guild.getRoleById(self.roleId());
            String state = !self.enabled() ? "deaktiviert"
                    : role == null ? "**gelöscht**"
                    : unsafeReason(role).map(reason -> "**gesperrt**: " + reason)
                            .orElseGet(() -> guild.getSelfMember().canInteract(role)
                                    ? "aktiv"
                                    : "**über meiner Rolle**");
            return "• `" + self.label() + "` – " + (role == null ? self.roleId() : role.getAsMention())
                    + " – " + state;
        }).collect(Collectors.joining("\n"));
        event.reply(lines).setEphemeral(true).queue();
    }

    // --- shared ----------------------------------------------------------------------

    private List<Role> assignableRoles(Guild guild) {
        return assignableRoles(guild, new ArrayList<>());
    }

    /**
     * The enabled self roles that may actually be granted right now, capped at Discord's
     * menu limit. Roles that were allowed but have since been deleted, given privileged
     * permissions or moved above the bot are dropped here and named in {@code blocked}.
     */
    private List<Role> assignableRoles(Guild guild, List<String> blocked) {
        List<Role> roles = new ArrayList<>();
        for (SelfRole self : repository.enabled()) {
            Role role = guild.getRoleById(self.roleId());
            if (role == null) {
                log.warn("Self role {} ({}) no longer exists on the guild", self.label(), self.roleId());
                continue;
            }
            Optional<String> unsafe = unsafeReason(role);
            if (unsafe.isPresent()) {
                log.error("Refusing self role {} ({}): {}", self.label(), self.roleId(), unsafe.get());
                blocked.add(self.label());
                continue;
            }
            if (!guild.getSelfMember().canInteract(role)) {
                log.warn("Cannot assign self role {} ({}): above the bot's highest role",
                        self.label(), self.roleId());
                blocked.add(self.label());
                continue;
            }
            roles.add(role);
            if (roles.size() == MAX_OPTIONS) {
                // ponytail: hard cap, no paging. 25 self roles is already more than the
                // MVP plans; add a second menu or a category select if that ever changes.
                log.warn("More than {} self roles configured, the rest is not shown", MAX_OPTIONS);
                break;
            }
        }
        return roles;
    }

    /** Menu over {@code roles}; with a member, their current roles are preselected. */
    private StringSelectMenu menu(List<Role> roles, Member member) {
        StringSelectMenu.Builder builder = StringSelectMenu.create(PICK)
                .setPlaceholder("Rollen auswählen")
                .setRequiredRange(0, roles.size());
        Map<Long, String> labels = labels();
        for (Role role : roles) {
            builder.addOption(labels.getOrDefault(role.getIdLong(), role.getName()), role.getId());
        }
        if (member != null) {
            List<String> mine = roles.stream()
                    .filter(member.getRoles()::contains)
                    .map(Role::getId)
                    .toList();
            if (!mine.isEmpty()) {
                builder.setDefaultValues(mine);
            }
        }
        return builder.build();
    }

    private Map<Long, String> labels() {
        Map<Long, String> labels = new LinkedHashMap<>();
        repository.enabled().forEach(self -> labels.put(self.roleId(), self.label()));
        return labels;
    }

    private Optional<String> unsafeReason(Role role) {
        return unsafeReason(role.getIdLong(), config.id("roles.staff"), role.isPublicRole(),
                role.isManaged(), role.getPermissions());
    }

    /**
     * Why this role must never be self-assignable, or empty if it is safe.
     *
     * <p>Kept free of JDA types so the rule that guards the whole feature can be tested
     * directly rather than through a Discord connection.
     */
    static Optional<String> unsafeReason(long roleId, long staffRoleId, boolean publicRole,
            boolean managed, Collection<Permission> permissions) {
        if (publicRole) {
            return Optional.of("@everyone ist keine Self Role.");
        }
        if (managed) {
            return Optional.of("Die Rolle wird von Discord oder einer Integration verwaltet.");
        }
        if (roleId == staffRoleId) {
            return Optional.of("Die Staff-Rolle kann keine Self Role sein.");
        }
        String privileged = permissions.stream()
                .filter(PRIVILEGED::contains)
                .map(Permission::getName)
                .sorted()
                .collect(Collectors.joining(", "));
        if (!privileged.isEmpty()) {
            return Optional.of("Die Rolle hat privilegierte Rechte: " + privileged + ".");
        }
        return Optional.empty();
    }

    /** Submitted values are raw strings from the client and may be anything at all. */
    static Set<Long> parseIds(Collection<String> values) {
        Set<Long> ids = new java.util.HashSet<>();
        for (String value : values) {
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException e) {
                log.warn("Ignoring non-numeric self role value '{}'", value);
            }
        }
        return ids;
    }

    static String summary(List<String> added, List<String> removed) {
        StringBuilder text = new StringBuilder();
        if (!added.isEmpty()) {
            text.append("Hinzugefügt: ").append(String.join(", ", added));
        }
        if (!removed.isEmpty()) {
            text.append(text.isEmpty() ? "" : "\n").append("Entfernt: ").append(String.join(", ", removed));
        }
        return text.isEmpty() ? "Nichts geändert." : text.toString();
    }

    private static List<String> names(List<Role> roles) {
        return roles.stream().map(Role::getName).toList();
    }

    static String blockedNote(List<String> blocked) {
        return blocked.isEmpty() ? ""
                : "\nNicht verfügbar: " + String.join(", ", blocked) + ". Das Team wurde informiert.";
    }
}
