package com.groupa13.disasterrelief.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.db.FlywayMigrator;
import com.groupa13.disasterrelief.support.PostgresTestSupport;
import com.groupa13.disasterrelief.util.UserSession;
import com.zaxxer.hikari.HikariDataSource;

@Testcontainers
class CorePlatformIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            PostgresTestSupport.newContainer();

    private static DataSource testDataSource;

    @BeforeAll
    static void migrateFreshDatabaseAndInitializePool() {
        testDataSource = PostgresTestSupport.dataSource(POSTGRES);
        FlywayMigrator.migrate(testDataSource);
        Database.initialize(PostgresTestSupport.databaseConfig(POSTGRES));
    }

    @AfterAll
    static void closeApplicationPool() {
        UserSession.getInstance().clear();
        Database.shutdown();
    }

    @BeforeEach
    void resetCoreData() throws SQLException {
        UserSession.getInstance().clear();
        PostgresTestSupport.truncateCoreTables(testDataSource);
    }

    @Test
    void freshDatabaseContainsTheCompleteV001ToV003Contract() throws SQLException {
        assertEquals(
                Set.of("001", "002", "003"),
                querySet("""
                        SELECT version
                        FROM flyway_schema_history
                        WHERE success AND version IN ('001', '002', '003')
                        """));

        assertEquals(
                Set.of("btree_gist", "pgcrypto"),
                querySet("""
                        SELECT extname
                        FROM pg_extension
                        WHERE extname IN ('btree_gist', 'pgcrypto')
                        """));

        assertEquals(
                Set.of("coordinates", "severity_level", "disaster_status", "shelter_status"),
                querySet("""
                        SELECT typname
                        FROM pg_type
                        WHERE typname IN (
                            'coordinates', 'severity_level', 'disaster_status', 'shelter_status'
                        )
                        """));

        assertTrue(querySet("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                """).containsAll(Set.of(
                        "disasters",
                        "zones",
                        "shelters",
                        "resource_categories",
                        "resources",
                        "shelter_inventory")));

        assertEquals("NO", queryString("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'shelters'
                  AND column_name = 'address'
                """));
        assertEquals("YES", queryString("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'shelters'
                  AND column_name = 'location'
                """));

        Set<String> constraints = querySet("""
                SELECT conname
                FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace
                """);
        assertTrue(constraints.containsAll(Set.of(
                "fk_zones_disaster",
                "fk_zones_primary_shelter",
                "chk_shelters_address_nonblank",
                "chk_shelters_location_bounds",
                "chk_shelters_occupancy_within_capacity",
                "uq_zones_disaster_name",
                "pk_shelter_inventory")));

        Set<String> indexes = querySet("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                """);
        assertTrue(indexes.containsAll(Set.of(
                "idx_zones_disaster_id",
                "idx_zones_primary_shelter_id",
                "idx_resource_categories_parent_id",
                "idx_resources_category_id",
                "idx_shelter_inventory_resource_id",
                "idx_disasters_open",
                "idx_shelters_operational")));
    }

    @Test
    void shelterContractSupportsOfflineAndGeocodedRegistration() throws SQLException {
        int offlineId;
        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO shelters (name, address, capacity)
                        VALUES (?, ?, ?)
                        RETURNING shelter_id
                        """)) {
            statement.setString(1, "Offline Relief Camp");
            statement.setString(2, "Government School Hall, Wayanad");
            statement.setInt(3, 250);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                offlineId = rows.getInt(1);
            }
        }

        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT address, location IS NULL, current_occupancy, status::text
                        FROM shelters
                        WHERE shelter_id = ?
                        """)) {
            statement.setInt(1, offlineId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("Government School Hall, Wayanad", rows.getString(1));
                assertTrue(rows.getBoolean(2));
                assertEquals(0, rows.getInt(3));
                assertEquals("operational", rows.getString(4));
            }
        }

        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO shelters (name, address, location, capacity)
                        VALUES (?, ?, ROW(?, ?)::coordinates, ?)
                        RETURNING (location).latitude, (location).longitude
                        """)) {
            statement.setString(1, "Mapped Relief Camp");
            statement.setString(2, "Meppadi, Wayanad");
            statement.setDouble(3, 11.5564);
            statement.setDouble(4, 76.1384);
            statement.setInt(5, 100);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(11.5564, rows.getDouble(1), 0.000001);
                assertEquals(76.1384, rows.getDouble(2), 0.000001);
            }
        }
    }

    @Test
    void namedChecksRejectInvalidCoreValues() {
        assertSqlState("23502", () -> executeUpdate("""
                INSERT INTO shelters (name, address, capacity)
                VALUES ('Missing Address', NULL, 20)
                """));
        assertConstraint("chk_shelters_address_nonblank", () -> executeUpdate("""
                INSERT INTO shelters (name, address, capacity)
                VALUES ('Blank Address', '   ', 20)
                """));
        assertConstraint("chk_shelters_capacity_positive", () -> executeUpdate("""
                INSERT INTO shelters (name, address, capacity)
                VALUES ('Invalid Capacity', 'Wayanad', 0)
                """));
        assertConstraint("chk_shelters_occupancy_within_capacity", () -> executeUpdate("""
                INSERT INTO shelters (name, address, capacity, current_occupancy)
                VALUES ('Over Capacity', 'Wayanad', 20, 21)
                """));
        assertConstraint("chk_shelters_location_bounds", () -> executeUpdate("""
                INSERT INTO shelters (name, address, location, capacity)
                VALUES ('Bad Latitude', 'Wayanad', ROW(91, 76)::coordinates, 20)
                """));
        assertConstraint("chk_shelters_location_bounds", () -> executeUpdate("""
                INSERT INTO shelters (name, address, location, capacity)
                VALUES ('Partial Coordinates', 'Wayanad', ROW(NULL, 76)::coordinates, 20)
                """));
        assertConstraint("chk_disasters_name_nonblank", () -> executeUpdate("""
                INSERT INTO disasters (name, severity)
                VALUES ('   ', 'low')
                """));
        assertConstraint("chk_disasters_active_period_nonempty", () -> executeUpdate("""
                INSERT INTO disasters (name, severity, active_period)
                VALUES ('Empty Period', 'low', 'empty'::daterange)
                """));
    }

    @Test
    void foreignKeysUniquenessAndDeleteRestrictionsProtectCoreRelationships()
            throws SQLException {
        int disasterId = insertDisaster("Flood 2026");
        int shelterId = insertShelter("Central Camp", "Kalpetta", 300);
        int zoneId = insertZone(disasterId, "North Zone");

        executeUpdate(
                "UPDATE zones SET primary_shelter_id = ? WHERE zone_id = ?",
                shelterId,
                zoneId);

        assertConstraint("uq_zones_disaster_name", () -> insertZone(disasterId, "North Zone"));
        assertConstraint("fk_zones_primary_shelter", () -> executeUpdate(
                "UPDATE zones SET primary_shelter_id = ? WHERE zone_id = ?",
                999999,
                zoneId));
        assertConstraint("fk_zones_disaster", () -> executeUpdate(
                "DELETE FROM disasters WHERE disaster_id = ?", disasterId));
        assertConstraint("fk_zones_primary_shelter", () -> executeUpdate(
                "DELETE FROM shelters WHERE shelter_id = ?", shelterId));
        executeUpdate(
                "UPDATE zones SET primary_shelter_id = NULL WHERE zone_id = ?", zoneId);

        int parentCategoryId = insertCategory("Medical", null);
        int childCategoryId = insertCategory("First Aid", parentCategoryId);
        int resourceId = insertResource("Bandages", childCategoryId, "boxes");

        assertConstraint("chk_resource_categories_not_self_parent", () -> executeUpdate(
                "UPDATE resource_categories SET parent_id = ? WHERE category_id = ?",
                parentCategoryId,
                parentCategoryId));
        assertConstraint("fk_resource_categories_parent", () -> executeUpdate(
                "DELETE FROM resource_categories WHERE category_id = ?", parentCategoryId));
        assertConstraint("fk_resources_category", () -> executeUpdate(
                "DELETE FROM resource_categories WHERE category_id = ?", childCategoryId));

        executeUpdate("""
                INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level)
                VALUES (?, ?, 30, 10)
                """, shelterId, resourceId);

        assertConstraint("pk_shelter_inventory", () -> executeUpdate("""
                INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level)
                VALUES (?, ?, 40, 10)
                """, shelterId, resourceId));
        assertConstraint("chk_shelter_inventory_quantity_nonnegative", () -> executeUpdate("""
                UPDATE shelter_inventory SET quantity = -1
                WHERE shelter_id = ? AND resource_id = ?
                """, shelterId, resourceId));
        assertConstraint("chk_shelter_inventory_reorder_nonnegative", () -> executeUpdate("""
                UPDATE shelter_inventory SET reorder_level = -1
                WHERE shelter_id = ? AND resource_id = ?
                """, shelterId, resourceId));
        assertConstraint("fk_shelter_inventory_resource", () -> executeUpdate(
                "DELETE FROM resources WHERE resource_id = ?", resourceId));
        assertConstraint("fk_shelter_inventory_shelter", () -> executeUpdate(
                "DELETE FROM shelters WHERE shelter_id = ?", shelterId));
    }

    @Test
    void transactionHelperCommitsRollsBackMapsErrorsAndDoesNotLeakZoneContext()
            throws SQLException {
        HikariDataSource pool = (HikariDataSource) Database.dataSource();
        assertEquals("dras-pool", pool.getPoolName());
        assertEquals(1, pool.getMaximumPoolSize());

        int committedId = Database.inTransaction(connection ->
                insertDisaster(connection, "Committed Disaster"));
        assertTrue(committedId > 0);
        assertEquals(1, queryLong(
                "SELECT count(*) FROM disasters WHERE name = 'Committed Disaster'"));

        assertThrows(IllegalStateException.class, () -> Database.inTransaction(connection -> {
            insertDisaster(connection, "Rolled Back Disaster");
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(0, queryLong(
                "SELECT count(*) FROM disasters WHERE name = 'Rolled Back Disaster'"));

        DatabaseException integrity = assertThrows(DatabaseException.class,
                () -> Database.inTransaction(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO disasters (name, severity)
                            VALUES ('   ', 'low')
                            """)) {
                        statement.executeUpdate();
                    }
                    return null;
                }));
        assertTrue(integrity.isIntegrityViolation());
        assertEquals("23514", integrity.sqlState());
        assertEquals("chk_disasters_name_nonblank", integrity.constraintName());

        int zoneDisasterId = insertDisaster("Zone Context Disaster");
        int zoneId = insertZone(zoneDisasterId, "Scoped Zone");
        UserSession session = UserSession.getInstance();
        session.setAssignedZoneId(zoneId);
        try {
            String inside = Database.inTransaction(session,
                    connection -> queryString(connection,
                            "SELECT current_setting('app.current_zone_id', true)"));
            assertEquals(Integer.toString(zoneId), inside);
        } finally {
            session.clear();
        }

        String after = Database.inTransaction(null,
                connection -> queryString(connection,
                        "SELECT current_setting('app.current_zone_id', true)"));
        assertTrue(after == null || after.isEmpty());

        try (Connection connection = Database.dataSource().getConnection()) {
            assertTrue(connection.getAutoCommit());
        }
    }

    private static int insertDisaster(String name) throws SQLException {
        try (Connection connection = testDataSource.getConnection()) {
            return insertDisaster(connection, name);
        }
    }

    private static int insertDisaster(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO disasters (name, type, severity)
                VALUES (?, 'flood', 'high')
                RETURNING disaster_id
                """)) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static int insertShelter(String name, String address, int capacity)
            throws SQLException {
        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO shelters (name, address, capacity)
                        VALUES (?, ?, ?)
                        RETURNING shelter_id
                        """)) {
            statement.setString(1, name);
            statement.setString(2, address);
            statement.setInt(3, capacity);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static int insertZone(int disasterId, String name) throws SQLException {
        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO zones (disaster_id, name, severity)
                        VALUES (?, ?, 'high')
                        RETURNING zone_id
                        """)) {
            statement.setInt(1, disasterId);
            statement.setString(2, name);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static int insertCategory(String name, Integer parentId) throws SQLException {
        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO resource_categories (name, parent_id)
                        VALUES (?, ?)
                        RETURNING category_id
                        """)) {
            statement.setString(1, name);
            if (parentId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, parentId);
            }
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static int insertResource(String name, int categoryId, String unit)
            throws SQLException {
        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO resources (name, category_id, unit)
                        VALUES (?, ?, ?)
                        RETURNING resource_id
                        """)) {
            statement.setString(1, name);
            statement.setInt(2, categoryId);
            statement.setString(3, unit);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static void executeUpdate(String sql, Object... parameters) throws SQLException {
        try (Connection connection = testDataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = testDataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static String queryString(String sql) throws SQLException {
        try (Connection connection = testDataSource.getConnection()) {
            return queryString(connection, sql);
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private static Set<String> querySet(String sql) throws SQLException {
        Set<String> values = new HashSet<>();
        try (Connection connection = testDataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private static void assertSqlState(String sqlState, SqlAction action) {
        SQLException exception = assertThrows(SQLException.class, action::execute);
        assertEquals(sqlState, exception.getSQLState());
    }

    private static void assertConstraint(String constraintName, SqlAction action) {
        SQLException exception = assertThrows(SQLException.class, action::execute);
        assertTrue(exception instanceof PSQLException);
        PSQLException postgresException = (PSQLException) exception;
        assertEquals(constraintName,
                postgresException.getServerErrorMessage() == null
                        ? null
                        : postgresException.getServerErrorMessage().getConstraint());
    }

    @FunctionalInterface
    private interface SqlAction {
        void execute() throws SQLException;
    }
}
