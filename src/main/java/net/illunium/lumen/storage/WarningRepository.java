package net.illunium.lumen.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Warnings handed out by moderators.
 *
 * <p>A warning is a plain append-only record: it is never edited, and the row survives the
 * member leaving the server, so a returning member keeps their history. Rows are keyed by
 * the Discord user ID directly; there is no user table.
 */
public final class WarningRepository {

    public record Warning(long id, long targetId, long moderatorId, String reason,
            Instant createdAt) {
    }

    private final Database database;

    public WarningRepository(Database database) {
        this.database = database;
    }

    /** Records a warning and returns how many the member has in total afterwards. */
    public int add(long targetId, long moderatorId, String reason) {
        String sql = """
                INSERT INTO warnings (target_id, moderator_id, reason, created_at)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = database.connection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, targetId);
            statement.setLong(2, moderatorId);
            statement.setString(3, reason);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot store warning for user " + targetId, e);
        }
        return count(targetId);
    }

    /**
     * The member's warnings, newest first, capped at {@code limit}.
     *
     * <p>Capped because the result is rendered into a single Discord embed, which has a
     * length limit of its own. {@link #count(long)} still reports the true total.
     */
    public List<Warning> of(long targetId, int limit) {
        String sql = """
                SELECT id, target_id, moderator_id, reason, created_at FROM warnings
                WHERE target_id = ? ORDER BY id DESC LIMIT ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, targetId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<Warning> warnings = new ArrayList<>();
                while (result.next()) {
                    warnings.add(new Warning(result.getLong(1), result.getLong(2),
                            result.getLong(3), result.getString(4),
                            Instant.parse(result.getString(5))));
                }
                return List.copyOf(warnings);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read warnings for user " + targetId, e);
        }
    }

    /** How many warnings the member has, ignoring any display cap. */
    public int count(long targetId) {
        try (PreparedStatement statement = database.connection()
                .prepareStatement("SELECT COUNT(*) FROM warnings WHERE target_id = ?")) {
            statement.setLong(1, targetId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot count warnings for user " + targetId, e);
        }
    }
}
