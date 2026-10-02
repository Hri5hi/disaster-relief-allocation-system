package com.groupa13.disasterrelief.dao.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.core.ActivePeriod;
import com.groupa13.disasterrelief.model.core.Disaster;
import com.groupa13.disasterrelief.model.core.DisasterStatus;
import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Severity;

public final class DisasterDao {

    private static final String COLUMNS = """
            disaster_id, name, type, severity::text AS severity,
            lower(active_period) AS active_start,
            upper(active_period) AS active_end,
            status::text AS status, created_at
            """;

    public Disaster create(Connection connection, String name, String type, Severity severity,
            ActivePeriod activePeriod, DisasterStatus status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO disasters (name, type, severity, active_period, status)
                VALUES (?, ?, ?::severity_level, ?, ?::disaster_status)
                RETURNING disaster_id, name, type, severity::text AS severity,
                          lower(active_period) AS active_start,
                          upper(active_period) AS active_end,
                          status::text AS status, created_at
                """)) {
            bind(statement, name, type, severity, activePeriod, status);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return map(rows);
            }
        }
    }

    public List<Disaster> findAll(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM disasters ORDER BY created_at DESC, disaster_id DESC");
                ResultSet rows = statement.executeQuery()) {
            List<Disaster> disasters = new ArrayList<>();
            while (rows.next()) {
                disasters.add(map(rows));
            }
            return List.copyOf(disasters);
        }
    }

    public List<EntityOption> findOpenOptions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT disaster_id, name
                FROM disasters
                WHERE status IN ('active'::disaster_status, 'monitoring'::disaster_status)
                ORDER BY name, disaster_id
                """); ResultSet rows = statement.executeQuery()) {
            List<EntityOption> options = new ArrayList<>();
            while (rows.next()) {
                int id = rows.getInt("disaster_id");
                options.add(new EntityOption(id, "D" + id, rows.getString("name")));
            }
            return List.copyOf(options);
        }
    }

    public Optional<Disaster> findById(Connection connection, int disasterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM disasters WHERE disaster_id = ?")) {
            statement.setInt(1, disasterId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public Optional<Disaster> update(Connection connection, int disasterId, String name,
            String type, Severity severity, ActivePeriod activePeriod, DisasterStatus status)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE disasters
                SET name = ?, type = ?, severity = ?::severity_level,
                    active_period = ?, status = ?::disaster_status
                WHERE disaster_id = ?
                RETURNING disaster_id, name, type, severity::text AS severity,
                          lower(active_period) AS active_start,
                          upper(active_period) AS active_end,
                          status::text AS status, created_at
                """)) {
            bind(statement, name, type, severity, activePeriod, status);
            statement.setInt(6, disasterId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public boolean delete(Connection connection, int disasterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM disasters WHERE disaster_id = ?")) {
            statement.setInt(1, disasterId);
            return statement.executeUpdate() == 1;
        }
    }

    private static void bind(PreparedStatement statement, String name, String type,
            Severity severity, ActivePeriod activePeriod, DisasterStatus status)
            throws SQLException {
        statement.setString(1, name);
        JdbcSupport.setNullableText(statement, 2, type);
        statement.setString(3, severity.databaseValue());
        JdbcSupport.setActivePeriod(statement, 4, activePeriod);
        statement.setString(5, status.databaseValue());
    }

    private static Disaster map(ResultSet rows) throws SQLException {
        return new Disaster(
                rows.getInt("disaster_id"),
                rows.getString("name"),
                rows.getString("type"),
                Severity.fromDatabase(rows.getString("severity")),
                JdbcSupport.activePeriod(rows),
                DisasterStatus.fromDatabase(rows.getString("status")),
                rows.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
