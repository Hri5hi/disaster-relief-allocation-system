package com.groupa13.disasterrelief.dao.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.core.Severity;
import com.groupa13.disasterrelief.model.core.Zone;

public final class ZoneDao {

    private static final String SELECT_BASE = """
            SELECT z.zone_id, z.disaster_id, d.name AS disaster_name, z.name,
                   z.severity::text AS severity, z.primary_shelter_id,
                   s.name AS primary_shelter_name, z.coordinator_contact
            FROM zones z
            JOIN disasters d ON d.disaster_id = z.disaster_id
            LEFT JOIN shelters s ON s.shelter_id = z.primary_shelter_id
            """;

    public Zone create(Connection connection, int disasterId, String name, Severity severity,
            Integer primaryShelterId, String coordinatorContact) throws SQLException {
        int zoneId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO zones (
                    disaster_id, name, severity, primary_shelter_id, coordinator_contact
                ) VALUES (?, ?, ?::severity_level, ?, ?)
                RETURNING zone_id
                """)) {
            bind(statement, disasterId, name, severity, primaryShelterId, coordinatorContact);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                zoneId = rows.getInt(1);
            }
        }
        return findById(connection, zoneId).orElseThrow();
    }

    public List<Zone> findAll(Connection connection) throws SQLException {
        return queryList(connection, SELECT_BASE + " ORDER BY d.name, z.name, z.zone_id", null);
    }

    public List<Zone> findByDisaster(Connection connection, int disasterId) throws SQLException {
        return queryList(connection, SELECT_BASE
                + " WHERE z.disaster_id = ? ORDER BY z.name, z.zone_id", disasterId);
    }

    public Optional<Zone> findById(Connection connection, int zoneId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SELECT_BASE + " WHERE z.zone_id = ?")) {
            statement.setInt(1, zoneId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public Optional<Zone> update(Connection connection, int zoneId, String name,
            Severity severity, Integer primaryShelterId, String coordinatorContact)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE zones
                SET name = ?, severity = ?::severity_level,
                    primary_shelter_id = ?, coordinator_contact = ?
                WHERE zone_id = ?
                """)) {
            statement.setString(1, name);
            statement.setString(2, severity.databaseValue());
            JdbcSupport.setNullableInteger(statement, 3, primaryShelterId);
            JdbcSupport.setNullableText(statement, 4, coordinatorContact);
            statement.setInt(5, zoneId);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(connection, zoneId);
    }

    public Optional<Zone> assignPrimaryShelter(Connection connection, int zoneId,
            Integer primaryShelterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE zones SET primary_shelter_id = ? WHERE zone_id = ?
                """)) {
            JdbcSupport.setNullableInteger(statement, 1, primaryShelterId);
            statement.setInt(2, zoneId);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(connection, zoneId);
    }

    public boolean delete(Connection connection, int zoneId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM zones WHERE zone_id = ?")) {
            statement.setInt(1, zoneId);
            return statement.executeUpdate() == 1;
        }
    }

    private static List<Zone> queryList(Connection connection, String sql, Integer parameter)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setInt(1, parameter);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<Zone> zones = new ArrayList<>();
                while (rows.next()) {
                    zones.add(map(rows));
                }
                return List.copyOf(zones);
            }
        }
    }

    private static void bind(PreparedStatement statement, int disasterId, String name,
            Severity severity, Integer primaryShelterId, String coordinatorContact)
            throws SQLException {
        statement.setInt(1, disasterId);
        statement.setString(2, name);
        statement.setString(3, severity.databaseValue());
        JdbcSupport.setNullableInteger(statement, 4, primaryShelterId);
        JdbcSupport.setNullableText(statement, 5, coordinatorContact);
    }

    private static Zone map(ResultSet rows) throws SQLException {
        int primaryShelterId = rows.getInt("primary_shelter_id");
        Integer nullablePrimaryShelterId = rows.wasNull() ? null : primaryShelterId;
        return new Zone(
                rows.getInt("zone_id"),
                rows.getInt("disaster_id"),
                rows.getString("disaster_name"),
                rows.getString("name"),
                Severity.fromDatabase(rows.getString("severity")),
                nullablePrimaryShelterId,
                rows.getString("primary_shelter_name"),
                rows.getString("coordinator_contact"));
    }
}
