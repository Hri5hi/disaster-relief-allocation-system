package com.groupa13.disasterrelief.dao.core;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.groupa13.disasterrelief.model.core.ResourceCategory;

public final class ResourceCategoryDao {

    private static final String TREE_QUERY = """
            WITH RECURSIVE category_tree AS (
                SELECT category_id, parent_id, name, 0 AS depth,
                       ARRAY[category_id] AS id_path,
                       ARRAY[name::text] AS name_path
                FROM resource_categories
                WHERE parent_id IS NULL
                UNION ALL
                SELECT child.category_id, child.parent_id, child.name, tree.depth + 1,
                       tree.id_path || child.category_id,
                       tree.name_path || child.name::text
                FROM resource_categories child
                JOIN category_tree tree ON child.parent_id = tree.category_id
                WHERE NOT child.category_id = ANY(tree.id_path)
            )
            SELECT category_id, parent_id, name, depth,
                   array_to_string(name_path, ' > ') AS category_path
            FROM category_tree
            """;

    public ResourceCategory create(Connection connection, String name, Integer parentId)
            throws SQLException {
        int categoryId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO resource_categories (name, parent_id)
                VALUES (?, ?)
                RETURNING category_id
                """)) {
            statement.setString(1, name);
            JdbcSupport.setNullableInteger(statement, 2, parentId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                categoryId = rows.getInt(1);
            }
        }
        return findById(connection, categoryId).orElseThrow();
    }

    public List<ResourceCategory> findTree(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                TREE_QUERY + " ORDER BY id_path"); ResultSet rows = statement.executeQuery()) {
            List<ResourceCategory> categories = new ArrayList<>();
            while (rows.next()) {
                categories.add(map(rows));
            }
            return List.copyOf(categories);
        }
    }

    public Optional<ResourceCategory> findById(Connection connection, int categoryId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                TREE_QUERY + " WHERE category_id = ?")) {
            statement.setInt(1, categoryId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    public boolean wouldCreateCycle(Connection connection, int categoryId, int parentId)
            throws SQLException {
        if (categoryId == parentId) {
            return true;
        }
        // Serialize hierarchy moves so two concurrent, individually valid moves cannot
        // commit a cycle that neither transaction observed before its update.
        try (PreparedStatement lock = connection.prepareStatement("""
                SELECT category_id
                FROM resource_categories
                ORDER BY category_id
                FOR UPDATE
                """); ResultSet lockedRows = lock.executeQuery()) {
            while (lockedRows.next()) {
                // Consuming the result holds a row lock until the service transaction ends.
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                WITH RECURSIVE descendants AS (
                    SELECT category_id
                    FROM resource_categories
                    WHERE parent_id = ?
                    UNION ALL
                    SELECT child.category_id
                    FROM resource_categories child
                    JOIN descendants parent ON child.parent_id = parent.category_id
                )
                SELECT EXISTS (
                    SELECT 1 FROM descendants WHERE category_id = ?
                )
                """)) {
            statement.setInt(1, categoryId);
            statement.setInt(2, parentId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    public Optional<ResourceCategory> update(Connection connection, int categoryId,
            String name, Integer parentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE resource_categories
                SET name = ?, parent_id = ?
                WHERE category_id = ?
                """)) {
            statement.setString(1, name);
            JdbcSupport.setNullableInteger(statement, 2, parentId);
            statement.setInt(3, categoryId);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(connection, categoryId);
    }

    public boolean delete(Connection connection, int categoryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM resource_categories WHERE category_id = ?")) {
            statement.setInt(1, categoryId);
            return statement.executeUpdate() == 1;
        }
    }

    private static ResourceCategory map(ResultSet rows) throws SQLException {
        int parentId = rows.getInt("parent_id");
        Integer nullableParentId = rows.wasNull() ? null : parentId;
        return new ResourceCategory(
                rows.getInt("category_id"),
                rows.getString("name"),
                nullableParentId,
                rows.getInt("depth"),
                rows.getString("category_path"));
    }
}
