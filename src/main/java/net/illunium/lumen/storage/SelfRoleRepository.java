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
        String sql = "SELECT role_id, label, enabled FROM self_roles WHERE enabled = 1 ORDER BY label";
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

    /** Removes the role. Returns {@code false} if it was not stored, so callers stay idempotent. */
    public boolean remove(long roleId) {
        try (PreparedStatement statement =
                database.connection().prepareStatement("DELETE FROM self_roles WHERE role_id = ?")) {
            statement.setLong(1, roleId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot remove self role " + roleId, e);
        }
    }
}
