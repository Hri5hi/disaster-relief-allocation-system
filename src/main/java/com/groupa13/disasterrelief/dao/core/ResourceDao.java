package com.groupa13.disasterrelief.dao.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.core.EntityOption;
import com.groupa13.disasterrelief.model.core.Resource;

public final class ResourceDao {

    private static final String SELECT_BASE = """
            WITH RECURSIVE category_tree AS (
                SELECT category_id, parent_id, ARRAY[name::text] AS name_path,
                       ARRAY[category_id] AS id_path
                FROM resource_categories
                WHERE parent_id IS NULL
                UNION ALL
                SELECT child.category_id, child.parent_id,
                       tree.name_path || child.name::text,
                       tree.id_path || child.category_id
                FROM resource_categories child
                JOIN category_tree tree ON child.parent_id = tree.category_id
                WHERE NOT child.category_id = ANY(tree.id_path)
            )
            SELECT r.resource_id, r.name, r.category_id,
                   array_to_string(tree.name_path, ' > ') AS category_path,
                   r.unit
            FROM resources r
            JOIN category_tree tree ON tree.category_id = r.category_id
            """;

    public Resource create(Connection connection, String name, int categoryId, String unit)
            throws SQLException {
        int resourceId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO resources (name, category_id, unit)
                VALUES (?, ?, ?)
                RETURNING resource_id
                """)) {
            statement.setString(1, name);
            statement.setInt(2, categoryId);
            JdbcSupport.setNullableText(statement, 3, unit);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                resourceId = rows.getInt(1);
            }
        }
        return findById(connection, resourceId).orElseThrow();
    }

    public List<Resource> findAll(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SELECT_BASE + " ORDER BY r.name, r.resource_id");
                ResultSet rows = statement.executeQuery()) {
            List<Resource> resources = new ArrayList<>();
            while (rows.next()) {
                resources.add(map(rows));
            }
            return List.copyOf(resources);
        }
    }

    public List<EntityOption> findOptions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT resource_id, name FROM resources ORDER BY name, resource_id
                """); ResultSet rows = statement.executeQuery()) {
            List<EntityOption> options = new ArrayList<>();
            while (rows.next()) {
                int id = rows.getInt("resource_id");
                options.add(new EntityOption(id, "R" + id, rows.getString("name")));
            }
            return List.copyOf(options);
        }
    }

    public Optional<Resource> findById(Connection connection, int resourceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                SELECT_BASE + " WHERE r.resource_id = ?")) {
            statement.setInt(1, resourceId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public Optional<Resource> update(Connection connection, int resourceId, String name,
            int categoryId, String unit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE resources
                SET name = ?, category_id = ?, unit = ?
                WHERE resource_id = ?
                """)) {
            statement.setString(1, name);
            statement.setInt(2, categoryId);
            JdbcSupport.setNullableText(statement, 3, unit);
            statement.setInt(4, resourceId);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(connection, resourceId);
    }

    public boolean delete(Connection connection, int resourceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM resources WHERE resource_id = ?")) {
            statement.setInt(1, resourceId);
            return statement.executeUpdate() == 1;
        }
    }

    private static Resource map(ResultSet rows) throws SQLException {
        return new Resource(
                rows.getInt("resource_id"),
                rows.getString("name"),
                rows.getInt("category_id"),
                rows.getString("category_path"),
                rows.getString("unit"));
    }
}
