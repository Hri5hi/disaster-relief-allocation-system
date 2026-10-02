package com.groupa13.disasterrelief.people;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.groupa13.disasterrelief.dao.people.VolunteerDao;
import com.groupa13.disasterrelief.db.FlywayMigrator;
import com.groupa13.disasterrelief.model.Volunteer;
import com.groupa13.disasterrelief.support.PostgresTestSupport;

/**
 * Integration tests for VolunteerDao against a real PostgreSQL 15 Testcontainer.
 * Covers: TEXT[] skills round-trip, tsrange availability, @> containment search,
 * COALESCE-based partial update, delete, and DB-level tsrange rejection.
 */
@Testcontainers
class VolunteerDaoTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = PostgresTestSupport.newContainer();

    private static DataSource dataSource;

    private final VolunteerDao dao = new VolunteerDao();

    @BeforeAll
    static void migrateFreshDatabase() {
        dataSource = PostgresTestSupport.dataSource(POSTGRES);
        FlywayMigrator.migrate(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource = null;
    }

    @BeforeEach
    void resetTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE TABLE
                        people, volunteers, affected_persons, volunteer_shifts,
                        shelter_inventory, resources, resource_categories,
                        zones, shelters, disasters
                    RESTART IDENTITY CASCADE
                    """);
        }
    }

    // ── Test 1: INSERT + SELECT round-trip with INHERITS ──────────────────────

    @Test
    void testInsertAndFindById_basic() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int id = dao.insert(conn, "Alice", "9990001111", "alice@example.org",
                    List.of("first_aid"), "[2026-07-09 08:00, 2026-07-09 20:00)", null);
            assertTrue(id > 0);

            Optional<Volunteer> found = dao.findById(conn, id);
            assertTrue(found.isPresent());
            Volunteer v = found.get();
            assertEquals("Alice", v.getName());
            assertEquals("9990001111", v.getContact());
            assertEquals("alice@example.org", v.getEmail());
            assertTrue(v.getSkills().contains("first_aid"));
            assertNotNull(v.getAvailability());
            assertTrue(v.getAvailability().contains("2026-07-09"));
            assertNull(v.getAssignedZone());
        }
    }

    // ── Test 2: nullable tsrange ───────────────────────────────────────────────

    @Test
    void testInsertWithNullAvailability() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int id = dao.insert(conn, "Bob", null, null, List.of("driving"), null, null);

            Optional<Volunteer> found = dao.findById(conn, id);
            assertTrue(found.isPresent());
            assertNull(found.get().getAvailability());
        }
    }

    // ── Test 3: empty TEXT[] must map to empty list, not null ─────────────────

    @Test
    void testInsertWithEmptySkills() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int id = dao.insert(conn, "Carol", null, null, List.of(), null, null);

            Optional<Volunteer> found = dao.findById(conn, id);
            assertTrue(found.isPresent());
            assertNotNull(found.get().getSkills());
            assertTrue(found.get().getSkills().isEmpty());
            assertEquals("", found.get().getSkillsDisplay());
        }
    }

    // ── Test 4: @> with single-element array ──────────────────────────────────

    @Test
    void testFindBySkills_singleSkill() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, "Dave", null, null, List.of("first_aid", "driving"), null, null);

            List<Volunteer> results = dao.findBySkills(conn, List.of("first_aid"));
            assertEquals(1, results.size());
            assertEquals("Dave", results.get(0).getName());
        }
    }

    // ── Test 5: @> requires ALL skills present ────────────────────────────────

    @Test
    void testFindBySkills_multiSkill() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, "Dave", null, null, List.of("first_aid", "driving"), null, null);
            dao.insert(conn, "Eve", null, null, List.of("first_aid"), null, null);

            List<Volunteer> results = dao.findBySkills(conn, List.of("first_aid", "driving"));
            assertEquals(1, results.size());
            assertEquals("Dave", results.get(0).getName());
        }
    }

    // ── Test 6: non-matching array returns empty ──────────────────────────────

    @Test
    void testFindBySkills_noMatch() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            dao.insert(conn, "Dave", null, null, List.of("first_aid", "driving"), null, null);

            List<Volunteer> results = dao.findBySkills(conn, List.of("translation"));
            assertTrue(results.isEmpty());
        }
    }

    // ── Test 7: COALESCE-based partial update ─────────────────────────────────

    @Test
    void testUpdate_partialFields() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int id = dao.insert(conn, "Frank", "111", "frank@example.org",
                    List.of("cooking"), "[2026-07-09 08:00, 2026-07-09 20:00)", null);

            boolean changed = dao.update(conn, id, null, null, null, null,
                    "[2026-08-01 09:00, 2026-08-05 18:00)", null);
            assertTrue(changed);

            Volunteer v = dao.findById(conn, id).orElseThrow();
            assertEquals("Frank", v.getName());           // unchanged
            assertEquals("111", v.getContact());          // unchanged
            assertEquals("frank@example.org", v.getEmail()); // unchanged
            assertEquals(List.of("cooking"), v.getSkills()); // unchanged
            assertTrue(v.getAvailability().contains("2026-08-01")); // changed
        }
    }

    // ── Test 8: deletion ──────────────────────────────────────────────────────

    @Test
    void testDelete() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int id = dao.insert(conn, "Grace", null, null, null, null, null);

            assertTrue(dao.delete(conn, id));
            assertTrue(dao.findById(conn, id).isEmpty());
            assertFalse(dao.delete(conn, id)); // second delete reports no row
        }
    }

    // ── AC-2: malformed tsrange is rejected by PostgreSQL itself ──────────────

    @Test
    void testInsert_malformedTsrange_rejectedByDatabase() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            SQLException exception = assertThrows(SQLException.class,
                    () -> dao.insert(conn, "BadRange", null, null, null, "bad-range", null));
            assertEquals("22P02", exception.getSQLState());
        }
    }
}
