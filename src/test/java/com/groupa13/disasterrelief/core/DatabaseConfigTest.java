package com.groupa13.disasterrelief.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import com.groupa13.disasterrelief.db.DatabaseConfig;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.db.FlywayMigrator;

class DatabaseConfigTest {

    @Test
    void parsesTheRatifiedConfigurationContractAndRedactsThePassword() {
        Properties properties = validProperties();

        DatabaseConfig config = DatabaseConfig.from(properties);

        assertEquals("jdbc:postgresql://localhost:5432/dras", config.jdbcUrl());
        assertEquals("dras_app", config.username());
        assertEquals("local-test-secret", config.password());
        assertEquals(10, config.maximumPoolSize());
        assertEquals(10_000, config.connectionTimeoutMs());
        assertEquals("filesystem:database/migrations", config.flywayLocations());
        assertEquals("dras.config", DatabaseConfig.CONFIG_PATH_PROPERTY);
        assertFalse(config.toString().contains("local-test-secret"));
        assertTrue(config.toString().contains("password=<redacted>"));
    }

    @Test
    void rejectsMissingOrInvalidConfigurationWithActionableMessages() {
        Properties missingPassword = validProperties();
        missingPassword.remove("db.password");
        DatabaseException missing = assertThrows(
                DatabaseException.class, () -> DatabaseConfig.from(missingPassword));
        assertTrue(missing.getMessage().contains("db.password"));

        Properties invalidUrl = validProperties();
        invalidUrl.setProperty("db.jdbcUrl", "jdbc:h2:mem:test");
        DatabaseException url = assertThrows(
                DatabaseException.class, () -> DatabaseConfig.from(invalidUrl));
        assertTrue(url.getMessage().contains("PostgreSQL JDBC URL"));

        Properties invalidPool = validProperties();
        invalidPool.setProperty("db.pool.maximumSize", "0");
        DatabaseException pool = assertThrows(
                DatabaseException.class, () -> DatabaseConfig.from(invalidPool));
        assertTrue(pool.getMessage().contains("at least 1"));

        Properties invalidTimeout = validProperties();
        invalidTimeout.setProperty("db.pool.connectionTimeoutMs", "249");
        DatabaseException timeout = assertThrows(
                DatabaseException.class, () -> DatabaseConfig.from(invalidTimeout));
        assertTrue(timeout.getMessage().contains("at least 250"));
    }

    @Test
    void reportsTheMissingLocalConfigurationPathWithoutExposingCredentials() {
        Path missingPath = Path.of("build", "definitely-missing", "application.properties");

        DatabaseException exception = assertThrows(
                DatabaseException.class, () -> DatabaseConfig.load(missingPath));

        assertTrue(exception.getMessage().contains("Database configuration file not found"));
        assertTrue(exception.getMessage().contains("application.properties.example"));
        assertFalse(exception.getMessage().contains("password="));
    }

    @Test
    void rejectsMissingMigrationInputsBeforeAttemptingAConnection() {
        DatabaseException missingDataSource = assertThrows(
                DatabaseException.class, () -> FlywayMigrator.migrate(null));
        assertTrue(missingDataSource.getMessage().contains("datasource is required"));

        DatabaseException missingDirectory = assertThrows(
                DatabaseException.class,
                () -> FlywayMigrator.migrate(
                        new PGSimpleDataSource(),
                        "filesystem:build/definitely-missing/migrations"));
        assertTrue(missingDirectory.getMessage().contains("migration directory not found"));
    }

    private static Properties validProperties() {
        Properties properties = new Properties();
        properties.setProperty("db.jdbcUrl", "jdbc:postgresql://localhost:5432/dras");
        properties.setProperty("db.username", "dras_app");
        properties.setProperty("db.password", "local-test-secret");
        properties.setProperty("db.pool.maximumSize", "10");
        properties.setProperty("db.pool.connectionTimeoutMs", "10000");
        properties.setProperty("flyway.locations", "filesystem:database/migrations");
        return properties;
    }
}
