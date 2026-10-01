package com.groupa13.disasterrelief.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/** Applies and validates versioned database migrations for a supplied datasource. */
public final class FlywayMigrator {

    public static final String DEFAULT_LOCATION = "filesystem:database/migrations";

    private FlywayMigrator() {
    }

    public static MigrateResult migrate(DataSource dataSource) {
        return migrate(dataSource, DEFAULT_LOCATION);
    }

    public static MigrateResult migrate(DataSource dataSource, String configuredLocations) {
        if (dataSource == null) {
            throw new DatabaseException("A datasource is required to run migrations");
        }

        String[] locations = parseLocations(configuredLocations);
        validateFilesystemLocations(locations);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations)
                    .validateMigrationNaming(true)
                    .load();
            MigrateResult result = flyway.migrate();
            flyway.validate();
            return result;
        } catch (RuntimeException exception) {
            throw new DatabaseException(
                    "Database migration failed; verify PostgreSQL and flyway.locations", exception);
        }
    }

    private static String[] parseLocations(String configuredLocations) {
        if (configuredLocations == null || configuredLocations.isBlank()) {
            throw new DatabaseException("Missing required database setting: flyway.locations");
        }
        String[] locations = Arrays.stream(configuredLocations.split(","))
                .map(String::trim)
                .filter(location -> !location.isEmpty())
                .toArray(String[]::new);
        if (locations.length == 0) {
            throw new DatabaseException("flyway.locations must contain at least one location");
        }
        return locations;
    }

    private static void validateFilesystemLocations(String[] locations) {
        for (String location : locations) {
            if (!location.startsWith("filesystem:")) {
                continue;
            }
            String pathValue = location.substring("filesystem:".length());
            if (pathValue.isBlank() || !Files.isDirectory(Path.of(pathValue))) {
                throw new DatabaseException("Flyway migration directory not found: " + pathValue);
            }
        }
    }
}
