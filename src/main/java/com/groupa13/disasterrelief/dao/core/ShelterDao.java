package com.groupa13.disasterrelief.dao.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.core.Coordinates;
import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Shelter;
import com.groupa13.disasterrelief.model.core.ShelterStatus;

public final class ShelterDao {

    private static final String COLUMNS = """
            shelter_id, name, address,
            (location).latitude AS latitude,
            (location).longitude AS longitude,
            capacity, current_occupancy, status::text AS status
            """;

    public Shelter create(Connection connection, String name, String address,
            Coordinates location, int capacity, ShelterStatus status) throws SQLException {
        String sql = location == null ? """
                INSERT INTO shelters (name, address, location, capacity, status)
                VALUES (?, ?, NULL, ?, ?::shelter_status)
                RETURNING shelter_id
                """ : """
                INSERT INTO shelters (name, address, location, capacity, status)
                VALUES (?, ?, ROW(?, ?)::coordinates, ?, ?::shelter_status)
                RETURNING shelter_id
                """;
        int shelterId;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, address);
            if (location == null) {
                statement.setInt(3, capacity);
                statement.setString(4, status.databaseValue());
            } else {
                statement.setDouble(3, location.latitude());
                statement.setDouble(4, location.longitude());
                statement.setInt(5, capacity);
                statement.setString(6, status.databaseValue());
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                shelterId = rows.getInt(1);
            }
        }
        return findById(connection, shelterId).orElseThrow();
    }

    public List<Shelter> findAll(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM shelters ORDER BY name, shelter_id");
                ResultSet rows = statement.executeQuery()) {
            List<Shelter> shelters = new ArrayList<>();
            while (rows.next()) {
                shelters.add(map(rows));
            }
            return List.copyOf(shelters);
        }
    }

    public List<EntityOption> findOptions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT shelter_id, name FROM shelters ORDER BY name, shelter_id
                """); ResultSet rows = statement.executeQuery()) {
            List<EntityOption> options = new ArrayList<>();
            while (rows.next()) {
                int id = rows.getInt("shelter_id");
                options.add(new EntityOption(id, "S" + id, rows.getString("name")));
            }
            return List.copyOf(options);
        }
    }

    public Optional<Shelter> findById(Connection connection, int shelterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM shelters WHERE shelter_id = ?")) {
            statement.setInt(1, shelterId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public Optional<Shelter> update(Connection connection, int shelterId, String name,
            String address, Coordinates location, int capacity, ShelterStatus status)
            throws SQLException {
        String locationSql = location == null ? "location = NULL" :
                "location = ROW(?, ?)::coordinates";
        String sql = "UPDATE shelters SET name = ?, address = ?, " + locationSql
                + ", capacity = ?, status = ?::shelter_status WHERE shelter_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, address);
            int index = 3;
            if (location != null) {
                statement.setDouble(index++, location.latitude());
                statement.setDouble(index++, location.longitude());
            }
            statement.setInt(index++, capacity);
            statement.setString(index++, status.databaseValue());
            statement.setInt(index, shelterId);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(connection, shelterId);
    }

    public boolean delete(Connection connection, int shelterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM shelters WHERE shelter_id = ? AND current_occupancy = 0")) {
            statement.setInt(1, shelterId);
            return statement.executeUpdate() == 1;
        }
    }

    private static Shelter map(ResultSet rows) throws SQLException {
        Double latitude = rows.getObject("latitude", Double.class);
        Double longitude = rows.getObject("longitude", Double.class);
        Coordinates coordinates = latitude == null || longitude == null
                ? null : new Coordinates(latitude, longitude);
        return new Shelter(
                rows.getInt("shelter_id"),
                rows.getString("name"),
                rows.getString("address"),
                coordinates,
                rows.getInt("capacity"),
                rows.getInt("current_occupancy"),
                ShelterStatus.fromDatabase(rows.getString("status")));
    }
}
