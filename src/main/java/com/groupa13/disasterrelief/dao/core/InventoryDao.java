package com.groupa13.disasterrelief.dao.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.core.ShelterInventory;

public final class InventoryDao {

    private static final String SELECT_BASE = """
            SELECT inventory.shelter_id, shelter.name AS shelter_name,
                   inventory.resource_id, resource.name AS resource_name,
                   inventory.quantity, inventory.reorder_level, inventory.last_updated
            FROM shelter_inventory inventory
            JOIN shelters shelter ON shelter.shelter_id = inventory.shelter_id
            JOIN resources resource ON resource.resource_id = inventory.resource_id
            """;

    /** Replaces the absolute quantity and threshold atomically in one statement. */
    public ShelterInventory upsertReplacement(Connection connection, int shelterId,
            int resourceId, int quantity, int reorderLevel) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shelter_inventory (
                    shelter_id, resource_id, quantity, reorder_level
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (shelter_id, resource_id)
                DO UPDATE SET quantity = EXCLUDED.quantity,
                              reorder_level = EXCLUDED.reorder_level,
                              last_updated = now()
                RETURNING shelter_id, resource_id
                """)) {
            statement.setInt(1, shelterId);
            statement.setInt(2, resourceId);
            statement.setInt(3, quantity);
            statement.setInt(4, reorderLevel);
            statement.executeQuery().close();
        }
        return find(connection, shelterId, resourceId).orElseThrow();
    }

    public List<ShelterInventory> findAll(Connection connection) throws SQLException {
        return queryList(connection, SELECT_BASE
                + " ORDER BY shelter.name, resource.name, inventory.resource_id");
    }

    public List<ShelterInventory> findLowStock(Connection connection) throws SQLException {
        return queryList(connection, SELECT_BASE
                + " WHERE inventory.quantity < inventory.reorder_level"
                + " ORDER BY (inventory.reorder_level - inventory.quantity) DESC,"
                + " shelter.name, resource.name");
    }

    public Optional<ShelterInventory> find(Connection connection, int shelterId,
            int resourceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SELECT_BASE + " WHERE inventory.shelter_id = ? AND inventory.resource_id = ?")) {
            statement.setInt(1, shelterId);
            statement.setInt(2, resourceId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public boolean delete(Connection connection, int shelterId, int resourceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM shelter_inventory
                WHERE shelter_id = ? AND resource_id = ?
                """)) {
            statement.setInt(1, shelterId);
            statement.setInt(2, resourceId);
            return statement.executeUpdate() == 1;
        }
    }

    private static List<ShelterInventory> queryList(Connection connection, String sql)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            List<ShelterInventory> inventory = new ArrayList<>();
            while (rows.next()) {
                inventory.add(map(rows));
            }
            return List.copyOf(inventory);
        }
    }

    private static ShelterInventory map(ResultSet rows) throws SQLException {
        return new ShelterInventory(
                rows.getInt("shelter_id"),
                rows.getString("shelter_name"),
                rows.getInt("resource_id"),
                rows.getString("resource_name"),
                rows.getInt("quantity"),
                rows.getInt("reorder_level"),
                rows.getObject("last_updated", java.time.OffsetDateTime.class));
    }
}
