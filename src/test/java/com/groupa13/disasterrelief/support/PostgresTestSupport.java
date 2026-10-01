package com.groupa13.disasterrelief.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.groupa13.disasterrelief.db.DatabaseConfig;
import com.groupa13.disasterrelief.db.FlywayMigrator;

/** Shared PostgreSQL 15 container and fresh-schema helpers for backend tests. */
public final class PostgresTestSupport {

    public static final String IMAGE = "postgres:15-alpine";
    public static final String DATABASE = "dras";
    public static final String USERNAME = "dras_app";
    private static final String PASSWORD = "wave1-test-only-password";

    private PostgresTestSupport() {
    }

    public static PostgreSQLContainer newContainer() {
        return new PostgreSQLContainer(IMAGE)
                .withDatabaseName(DATABASE)
                .withUsername(USERNAME)
                .withPassword(PASSWORD);
    }

    public static DataSource dataSource(PostgreSQLContainer container) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(container.getJdbcUrl());
        dataSource.setUser(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }

    public static DatabaseConfig databaseConfig(PostgreSQLContainer container) {
        return new DatabaseConfig(
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword(),
                1,
                10_000,
                FlywayMigrator.DEFAULT_LOCATION);
    }

    /** Removes test data while retaining the migrated schema and Flyway history. */
    public static void truncateCoreTables(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE
                        shelter_inventory,
                        resources,
                        resource_categories,
                        zones,
                        shelters,
                        disasters
                    RESTART IDENTITY CASCADE
                    """);
        }
    }
}
