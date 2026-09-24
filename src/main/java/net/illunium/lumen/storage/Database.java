package net.illunium.lumen.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the SQLite connection and brings the schema up to date on startup.
 *
 * <p>The applied schema version lives in SQLite's built-in {@code PRAGMA user_version},
 * so migrations need no bookkeeping table and no migration framework.
 */
public final class Database implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private static final String DEFAULT_PATH = "data/lumen.db";

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Opens the file from {@code LUMEN_DATABASE_PATH}, defaulting to {@code data/lumen.db}. */
    public static Database open() {
        return open(Path.of(System.getenv().getOrDefault("LUMEN_DATABASE_PATH", DEFAULT_PATH)));
    }

    /** Opens (and creates, if missing) the database at {@code file} and migrates it. */
    public static Database open(Path file) {
        Path absolute = file.toAbsolutePath();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create database directory for " + absolute, e);
        }

        // ponytail: one connection, no pool. SQLite is a single file and a Discord bot's
        // write volume is tiny; WAL plus a busy timeout covers concurrent readers. Move to
        // a pool (or HikariCP) only if contention ever shows up in the logs.
        Connection connection;
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database: " + absolute, e);
        }

        Database database = new Database(connection);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            database.closeQuietly();
            throw new IllegalStateException("Cannot configure database: " + absolute, e);
        }

        try {
            database.migrate();
        } catch (SQLException e) {
            database.closeQuietly();
            throw new IllegalStateException("Migration failed for " + absolute, e);
        }
        return database;
    }

    /** Applies every migration above the stored {@code user_version}, each in one transaction. */
    private void migrate() throws SQLException {
        int applied = version();
        if (applied > Schema.MIGRATIONS.size()) {
            throw new SQLException("Database is at schema version " + applied
                    + " but this build only knows " + Schema.MIGRATIONS.size()
                    + " (downgrade is not supported)");
        }
        for (int index = applied; index < Schema.MIGRATIONS.size(); index++) {
            applyMigration(index);
            log.info("Applied schema migration {}", index + 1);
        }
    }

    private void applyMigration(int index) throws SQLException {
        List<String> statements = Schema.MIGRATIONS.get(index);
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            // user_version takes no bind parameter; the value is a loop index, not input.
            statement.execute("PRAGMA user_version = " + (index + 1));
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** Schema version currently stored in the file; {@code 0} for a fresh database. */
    public int version() throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    /** Shared connection for the repositories in this package. */
    Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot close database", e);
        }
    }

    private void closeQuietly() {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Cannot close database after failed startup", e);
        }
    }
}
