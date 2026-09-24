package net.illunium.lumen.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;

/**
 * Ticket lifecycle.
 *
 * <p>The row, not the Discord channel, is the authority for claim and close state, so both
 * survive a restart. Every state change is a conditional update that reports whether it
 * changed anything: running claim or close twice is a no-op instead of an error, which is
 * what makes the buttons safe to press again.
 */
public final class TicketRepository {

    /** What the ticket is about. The values match the CHECK constraint on {@code tickets.type}. */
    public enum Type {
        SUPPORT,
        REPORT;

        /** The value as a member sees it, e.g. in the channel topic. */
        public String label() {
            return this == SUPPORT ? "Support" : "Report";
        }
    }

    /** Lifecycle state. The values match the CHECK constraint on {@code tickets.status}. */
    public enum Status {
        OPEN,
        CLAIMED,
        CLOSED
    }

    /** {@code claimedBy} is null while nobody has taken the ticket. */
    public record Ticket(long id, long channelId, long creatorId, Type type, Status status,
            Long claimedBy) {

        /** The persistent ticket ID, which is also the channel name: {@code ticket-0042}. */
        public String name() {
            return "ticket-%04d".formatted(id);
        }

        public boolean closed() {
            return status == Status.CLOSED;
        }
    }

    private static final String SELECT =
            "SELECT id, channel_id, creator_id, type, status, claimed_by FROM tickets ";

    private final Database database;

    public TicketRepository(Database database) {
        this.database = database;
    }

    /** Stores a new open ticket and returns it, including the generated ticket ID. */
    public Ticket open(long channelId, long creatorId, Type type) {
        String sql = """
                INSERT INTO tickets (channel_id, creator_id, type, status, created_at)
                VALUES (?, ?, ?, 'OPEN', ?)
                """;
        try (PreparedStatement statement = database.connection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, channelId);
            statement.setLong(2, creatorId);
            statement.setString(3, type.name());
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Ticket for channel " + channelId + " got no ID");
                }
                return new Ticket(keys.getLong(1), channelId, creatorId, type, Status.OPEN, null);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open ticket for channel " + channelId, e);
        }
    }

    /** The ticket belonging to a channel, empty if the channel is not a ticket. */
    public Optional<Ticket> byChannel(long channelId) {
        return findOne(SELECT + "WHERE channel_id = ?", channelId);
    }

    /**
     * The member's ticket that is still open, empty if they have none. Used to keep one
     * member from opening a second ticket while the first is unanswered.
     */
    public Optional<Ticket> openByCreator(long creatorId) {
        return findOne(SELECT + "WHERE creator_id = ? AND status <> 'CLOSED' ORDER BY id LIMIT 1",
                creatorId);
    }

    private Optional<Ticket> findOne(String sql, long key) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read ticket " + key, e);
        }
    }

    private static Ticket map(ResultSet result) throws SQLException {
        long id = result.getLong(1);
        long channelId = result.getLong(2);
        long creatorId = result.getLong(3);
        Type type = Type.valueOf(result.getString(4));
        Status status = Status.valueOf(result.getString(5));
        // wasNull() reports on the column read last, so claimed_by is read on its own.
        long claimedBy = result.getLong(6);
        return new Ticket(id, channelId, creatorId, type, status,
                result.wasNull() ? null : claimedBy);
    }

    /** Assigns the ticket to a staff member. {@code false} if it was already claimed or closed. */
    public boolean claim(long channelId, long staffId) {
        String sql = "UPDATE tickets SET status = 'CLAIMED', claimed_by = ? "
                + "WHERE channel_id = ? AND status = 'OPEN'";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, staffId);
            statement.setLong(2, channelId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot claim ticket in channel " + channelId, e);
        }
    }

    /**
     * Closes the ticket and stores who closed it and when. {@code closedBy} is null when
     * nobody closed it explicitly, e.g. when the channel was deleted by hand.
     *
     * <p>Returns {@code false} if the ticket was already closed, which is what makes close
     * idempotent: a second press changes nothing and deletes nothing twice.
     */
    public boolean close(long channelId, Long closedBy) {
        String sql = "UPDATE tickets SET status = 'CLOSED', closed_by = ?, closed_at = ? "
                + "WHERE channel_id = ? AND status <> 'CLOSED'";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            if (closedBy == null) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setLong(1, closedBy);
            }
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, channelId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot close ticket in channel " + channelId, e);
        }
    }
}
