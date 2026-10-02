package com.groupa13.disasterrelief.dao.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.operations.AppUser;
import com.groupa13.disasterrelief.util.UserRole;

public final class AppUserDao {

    private static final String COLUMNS = """
            user_id, person_id, username, password_hash, role, assigned_zone_id, is_active
            """;

    public Optional<AppUser> findByUsername(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT user_id, person_id, username, password_hash, role, assigned_zone_id, is_active
                FROM app_users
                WHERE username = ?
                """)) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.of(map(rows));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<AppUser> findById(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT user_id, person_id, username, password_hash, role, assigned_zone_id, is_active
                FROM app_users
                WHERE user_id = ?
                """)) {
            statement.setInt(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.of(map(rows));
                }
                return Optional.empty();
            }
        }
    }

    public List<AppUser> findAll(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM app_users ORDER BY user_id ASC");
                ResultSet rows = statement.executeQuery()) {
            List<AppUser> users = new ArrayList<>();
            while (rows.next()) {
                users.add(map(rows));
            }
            return List.copyOf(users);
        }
    }

    public AppUser create(Connection connection, int personId, String username, String passwordHash,
            UserRole role, Integer assignedZoneId, boolean isActive) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_users (person_id, username, password_hash, role, assigned_zone_id, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING user_id, person_id, username, password_hash, role, assigned_zone_id, is_active
                """)) {
            statement.setInt(1, personId);
            statement.setString(2, username);
            statement.setString(3, passwordHash);
            statement.setString(4, role.name());
            if (assignedZoneId != null) {
                statement.setInt(5, assignedZoneId);
            } else {
                statement.setNull(5, Types.INTEGER);
            }
            statement.setBoolean(6, isActive);

            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return map(rows);
            }
        }
    }

    private static AppUser map(ResultSet rows) throws SQLException {
        int userId = rows.getInt("user_id");
        int personId = rows.getInt("person_id");
        String username = rows.getString("username");
        String passwordHash = rows.getString("password_hash");
        String roleStr = rows.getString("role");
        UserRole role = UserRole.valueOf(roleStr);
        int zoneIdVal = rows.getInt("assigned_zone_id");
        Integer assignedZoneId = rows.wasNull() ? null : zoneIdVal;
        boolean isActive = rows.getBoolean("is_active");

        return new AppUser(userId, personId, username, passwordHash, role, assignedZoneId, isActive);
    }
}
