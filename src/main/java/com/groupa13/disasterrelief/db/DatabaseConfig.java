package com.groupa13.disasterrelief.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Immutable, validated database and migration configuration. */
public record DatabaseConfig(
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        long connectionTimeoutMs,
        String flywayLocations) {

    public static final String CONFIG_PATH_PROPERTY = "dras.config";
    public static final Path DEFAULT_CONFIG_PATH = Path.of("config", "application.properties");

    public DatabaseConfig {
        jdbcUrl = requireText(jdbcUrl, "db.jdbcUrl");
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new DatabaseException("db.jdbcUrl must be a PostgreSQL JDBC URL");
        }
        username = requireText(username, "db.username");
        password = requireText(password, "db.password");
        flywayLocations = requireText(flywayLocations, "flyway.locations");
        if (maximumPoolSize < 1) {
            throw new DatabaseException("db.pool.maximumSize must be at least 1");
        }
        if (connectionTimeoutMs < 250) {
            throw new DatabaseException("db.pool.connectionTimeoutMs must be at least 250");
        }
    }

    public static DatabaseConfig load() {
        String configuredPath = System.getProperty(CONFIG_PATH_PROPERTY);
        Path path = configuredPath == null || configuredPath.isBlank()
                ? DEFAULT_CONFIG_PATH
                : Path.of(configuredPath);
        return load(path);
    }

    public static DatabaseConfig load(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new DatabaseException(
                    "Database configuration file not found at " + path.toAbsolutePath()
                            + "; copy config/application.properties.example to "
                            + DEFAULT_CONFIG_PATH);
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new DatabaseException(
                    "Could not read database configuration at " + path.toAbsolutePath(), exception);
        }
        return from(properties);
    }

    public static DatabaseConfig from(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        return new DatabaseConfig(
                properties.getProperty("db.jdbcUrl"),
                properties.getProperty("db.username"),
                properties.getProperty("db.password"),
                parsePositiveInt(properties, "db.pool.maximumSize"),
                parsePositiveLong(properties, "db.pool.connectionTimeoutMs"),
                properties.getProperty("flyway.locations"));
    }

    @Override
    public String toString() {
        return "DatabaseConfig[jdbcUrl=" + jdbcUrl + ", username=" + username
                + ", password=<redacted>, maximumPoolSize=" + maximumPoolSize
                + ", connectionTimeoutMs=" + connectionTimeoutMs
                + ", flywayLocations=" + flywayLocations + "]";
    }

    private static int parsePositiveInt(Properties properties, String key) {
        String value = requireText(properties.getProperty(key), key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new DatabaseException(key + " must be an integer", exception);
        }
    }

    private static long parsePositiveLong(Properties properties, String key) {
        String value = requireText(properties.getProperty(key), key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new DatabaseException(key + " must be an integer", exception);
        }
    }

    private static String requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new DatabaseException("Missing required database setting: " + key);
        }
        return value.trim();
    }
}
