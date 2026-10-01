package com.groupa13.disasterrelief.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.groupa13.disasterrelief.util.UserSession;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Owns the application connection pool and the transaction boundary shared by
 * all backend services.
 */
public final class Database {

    private static final Logger LOGGER = Logger.getLogger(Database.class.getName());
    private static final Object LIFECYCLE_LOCK = new Object();

    private static volatile HikariDataSource dataSource;

    private Database() {
    }

    /** Returns the lazily initialized, migrated application datasource. */
    public static DataSource dataSource() {
        HikariDataSource current = dataSource;
        if (current == null) {
            synchronized (LIFECYCLE_LOCK) {
                current = dataSource;
                if (current == null) {
                    current = createDataSource(DatabaseConfig.load());
                    dataSource = current;
                }
            }
        }
        return current;
    }

    /**
     * Initializes the pool from an explicit configuration. Intended for
     * application bootstrap and isolated integration tests.
     */
    public static void initialize(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        synchronized (LIFECYCLE_LOCK) {
            if (dataSource != null) {
                throw new IllegalStateException("Database has already been initialized");
            }
            dataSource = createDataSource(config);
        }
    }

    public static <T> T inTransaction(SqlWork<T> work) {
        return inTransaction(null, work);
    }

    public static <T> T inTransaction(UserSession session, SqlWork<T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = dataSource().getConnection()) {
            return executeTransaction(connection, session, work);
        } catch (SQLException exception) {
            DatabaseException wrapped = DatabaseException.fromSql(
                    "Database transaction failed", exception);
            LOGGER.log(Level.SEVERE,
                    () -> "Database transaction failed [sqlState=" + wrapped.sqlState()
                            + ", constraint=" + wrapped.constraintName() + "]");
            throw wrapped;
        }
    }

    /** Closes the pool; safe to call during application or test shutdown. */
    public static void shutdown() {
        synchronized (LIFECYCLE_LOCK) {
            HikariDataSource current = dataSource;
            dataSource = null;
            if (current != null) {
                current.close();
            }
        }
    }

    private static HikariDataSource createDataSource(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.maximumPoolSize());
        hikari.setConnectionTimeout(config.connectionTimeoutMs());
        hikari.setInitializationFailTimeout(config.connectionTimeoutMs());
        hikari.setAutoCommit(true);
        hikari.setPoolName("dras-pool");

        HikariDataSource candidate = null;
        try {
            FlywayMigrator.migrate(migrationDataSource(config), config.flywayLocations());

            candidate = new HikariDataSource(hikari);
            try (Connection ignored = candidate.getConnection()) {
                // Hikari's fail-fast initialization plus this borrow verifies connectivity.
            }
            return candidate;
        } catch (RuntimeException | SQLException exception) {
            if (candidate != null) {
                candidate.close();
            }
            if (exception instanceof DatabaseException databaseException) {
                throw databaseException;
            }
            throw new DatabaseException(
                    "Could not initialize PostgreSQL; verify config/application.properties",
                    exception);
        }
    }

    private static DataSource migrationDataSource(DatabaseConfig config) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(config.jdbcUrl());
        dataSource.setUser(config.username());
        dataSource.setPassword(config.password());
        return dataSource;
    }

    private static <T> T executeTransaction(Connection connection, UserSession session,
            SqlWork<T> work) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        Throwable failure = null;
        try {
            connection.setAutoCommit(false);
            applyZoneContext(connection, session);
            T result = work.execute(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException exception) {
            failure = exception;
            rollback(connection, exception);
            throw exception;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException restoreFailure) {
                if (failure != null) {
                    failure.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
    }

    private static void applyZoneContext(Connection connection, UserSession session)
            throws SQLException {
        if (session == null || session.getAssignedZoneId() == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.current_zone_id', ?, true)")) {
            statement.setString(1, Integer.toString(session.getAssignedZoneId()));
            statement.execute();
        }
    }

    private static void rollback(Connection connection, Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }
}
