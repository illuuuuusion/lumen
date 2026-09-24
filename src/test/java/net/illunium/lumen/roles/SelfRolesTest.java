package net.illunium.lumen.roles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.Permission;
import org.junit.jupiter.api.Test;

class SelfRolesTest {

    private static final long STAFF = 42L;
    private static final long HARMLESS = 7L;

    private static Optional<String> check(long roleId, Permission... permissions) {
        return SelfRolesCommand.unsafeReason(roleId, STAFF, false, false, Set.of(permissions));
    }

    @Test
    void aPlainCosmeticRoleIsAllowed() {
        assertTrue(check(HARMLESS).isEmpty());
        assertTrue(check(HARMLESS, Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL).isEmpty(),
                "ordinary chat permissions must not block a self role");
    }

    @Test
    void theStaffRoleIsNeverSelfAssignable() {
        assertTrue(check(STAFF).isPresent());
    }

    @Test
    void everyoneAndIntegrationRolesAreRefused() {
        assertTrue(SelfRolesCommand.unsafeReason(HARMLESS, STAFF, true, false, Set.of()).isPresent(),
                "@everyone");
        assertTrue(SelfRolesCommand.unsafeReason(HARMLESS, STAFF, false, true, Set.of()).isPresent(),
                "managed role");
    }

    @Test
    void anyPrivilegedPermissionBlocksTheRole() {
        List<Permission> privileged = List.of(
                Permission.ADMINISTRATOR,
                Permission.MANAGE_ROLES,
                Permission.MANAGE_SERVER,
                Permission.MANAGE_CHANNEL,
                Permission.MANAGE_PERMISSIONS,
                Permission.MANAGE_WEBHOOKS,
                Permission.BAN_MEMBERS,
                Permission.KICK_MEMBERS,
                Permission.MODERATE_MEMBERS,
                Permission.MESSAGE_MANAGE,
                Permission.MESSAGE_MENTION_EVERYONE,
                Permission.VIEW_AUDIT_LOGS,
                Permission.NICKNAME_MANAGE);
        for (Permission permission : privileged) {
            Optional<String> reason = check(HARMLESS, permission);
            assertTrue(reason.isPresent(), permission + " must block a self role");
            assertTrue(reason.get().contains(permission.getName()), reason.get());
        }
    }

    @Test
    void privilegedPermissionHiddenAmongHarmlessOnesStillBlocks() {
        assertTrue(check(HARMLESS, Permission.MESSAGE_SEND, Permission.VIEW_CHANNEL,
                Permission.BAN_MEMBERS, Permission.MESSAGE_HISTORY).isPresent());
    }

    @Test
    void forgedSelectionValuesAreDiscarded() {
        Set<Long> ids = SelfRolesCommand.parseIds(
                List.of("123", "nicht-eine-id", "", "456", "9999999999999999999999"));
        assertEquals(Set.of(123L, 456L), ids,
                "only well-formed IDs survive; the caller still intersects them with the DB");
    }

    @Test
    void summaryNamesBothDirections() {
        assertEquals("Hinzugefügt: Dev", SelfRolesCommand.summary(List.of("Dev"), List.of()));
        assertEquals("Entfernt: Events", SelfRolesCommand.summary(List.of(), List.of("Events")));
        assertEquals("Hinzugefügt: Dev, Minecraft\nEntfernt: Events",
                SelfRolesCommand.summary(List.of("Dev", "Minecraft"), List.of("Events")));
        assertEquals("Nichts geändert.", SelfRolesCommand.summary(List.of(), List.of()));
    }

    @Test
    void blockedRolesAreReportedOnlyWhenThereAreAny() {
        assertEquals("", SelfRolesCommand.blockedNote(List.of()));
        assertTrue(SelfRolesCommand.blockedNote(List.of("Dev")).contains("Dev"));
    }

    @Test
    void panelIdIsPrefixedSoTheRouterFindsTheCommand() {
        assertTrue(SelfRolesCommand.PICK.startsWith("roles:"), SelfRolesCommand.PICK);
    }

    @Test
    void adminSubcommandsRequireStaffAndPickDoesNot() {
        Set<String> staffOnly = new SelfRolesCommand(null, null, null).staffOnlySubcommands();
        assertTrue(staffOnly.contains("panel create"));
        assertTrue(staffOnly.contains("allow"));
        assertTrue(staffOnly.contains("deny"));
        assertFalse(staffOnly.contains("pick"), "picking roles is for every member");
    }
}
