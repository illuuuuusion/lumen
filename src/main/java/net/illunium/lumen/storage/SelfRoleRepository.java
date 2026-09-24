package net.illunium.lumen.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-assignable roles.
 *
 * <p>Reference implementation for the repository layer: SQL lives here, never in a command
 * or event handler. The repositories for the remaining tables follow this shape and arrive
 * with the phase that needs them.
 */
public final class SelfRoleRepository {

    /** A role users may give themselves. {@code enabled} false hides it without losing the label. */
    public record SelfRole(long roleId, String label, boolean enabled) {
    }

    private final Database database;

    public SelfRoleRepository(Database database) {
        this.database = database;
    }

    /** Every enabled self role, ordered by label so panels are stable across restarts. */
    public List<SelfRole> enabled() {
        return query("SELECT role_id, label, enabled FROM self_roles WHERE enabled = 1 ORDER BY label");
    }

    /** Every self role including the disabled ones, for staff to see what is configured. */
    public List<SelfRole> all() {
        return query("SELECT role_id, label, enabled FROM self_roles ORDER BY label");
    }

    private List<SelfRole> query(String sql) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            List<SelfRole> roles = new ArrayList<>();
            while (result.next()) {
                roles.add(new SelfRole(result.getLong(1), result.getString(2), result.getBoolean(3)));
            }
            return List.copyOf(roles);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read self roles", e);
        }
    }

    /** Inserts the role or updates its label and enabled flag. */
    public void put(long roleId, String label, boolean enabled) {
        String sql = """
                INSERT INTO self_roles (role_id, label, enabled) VALUES (?, ?, ?)
                ON CONFLICT (role_id) DO UPDATE SET label = excluded.label, enabled = excluded.enabled
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, roleId);
            statement.setString(2, label);
            statement.setBoolean(3, enabled);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot store self role " + roleId, e);
        }
    }

    /**
     * Turns the role off without forgetting its label, so re-enabling it later keeps the
     * name staff chose. Returns {@code false} if it was not stored or already off, which
     * makes a repeated call a no-op rather than an error.
     */
    public boolean disable(long roleId) {
        String sql = "UPDATE self_roles SET enabled = 0 WHERE role_id = ? AND enabled = 1";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, roleId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot disable self role " + roleId, e);
        }
    }
}
