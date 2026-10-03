-- V008__development_seed.sql
-- Owner: Dev C
-- Dependencies: V001 through V007
-- Seeds coherent demonstration data across all 5 user roles, operations, requests,
-- distributions, and donations. Idempotent across multiple runs.

-- Standard demonstration password for all seed accounts: Password123!
-- Verified BCrypt hash (log rounds 10):
--   $2a$10$HaUWHrSnxmG74qamG.rU/eJgyb57/emdR4f45/JjpeTtdvLD/wfr.

-- ── 1. Seed People for System Accounts ──────────────────────────────────────
-- people has no external FK dependencies: safe to insert first.
INSERT INTO people (person_id, name, contact, email)
VALUES
    (101, 'Chief Coordinator Admin', '+91 98765 00001', 'admin@dras.org'),
    (102, 'North Zone Officer',       '+91 98765 00002', 'zone.coord@dras.org'),
    (103, 'Central Warehouse Lead',  '+91 98765 00003', 'warehouse@dras.org'),
    (104, 'Volunteer Field Marshal', '+91 98765 00004', 'volunteers@dras.org'),
    (105, 'Disaster Data Analyst',   '+91 98765 00005', 'analytics@dras.org')
ON CONFLICT (person_id) DO NOTHING;

-- Reset sequence if needed
SELECT setval(pg_get_serial_sequence('people', 'person_id'), GREATEST(105, (SELECT COALESCE(MAX(person_id), 1) FROM people)));


-- ── 2. Seed Base Operational Entities (if not present) ──────────────────────
-- ORDERING CONTRACT: disasters → shelters → zones must precede app_users
-- because zones.disaster_id → disasters and zones.primary_shelter_id → shelters,
-- and app_users.assigned_zone_id → zones.

INSERT INTO disasters (disaster_id, name, type, severity, active_period, status)
VALUES (1, 'Kerala Monsoon Floods 2026', 'Flood', 'high'::severity_level, daterange('2026-07-01', '2026-11-30', '[]'), 'active'::disaster_status)
ON CONFLICT (disaster_id) DO NOTHING;

INSERT INTO shelters (shelter_id, name, address, location, capacity, current_occupancy, status)
VALUES
    (1, 'Central Relief Camp Alpha', 'Meppadi Community Hall, Wayanad', ROW(11.5540, 76.1280)::coordinates, 500, 0, 'operational'::shelter_status),
    (2, 'Government Higher Secondary Shelter', 'Civil Station Road, Kalpetta', ROW(11.6103, 76.0827)::coordinates, 350, 0, 'operational'::shelter_status),
    (3, 'St. Joseph Relief Center', 'Mananthavady North, Wayanad', ROW(11.8025, 76.0035)::coordinates, 200, 0, 'operational'::shelter_status)
ON CONFLICT (shelter_id) DO NOTHING;

INSERT INTO zones (zone_id, disaster_id, name, severity, primary_shelter_id, coordinator_contact)
VALUES
    (1, 1, 'Wayanad North Sector', 'critical'::severity_level, 1, '+91 98765 00002'),
    (2, 1, 'Wayanad South Valley', 'high'::severity_level,     2, '+91 98765 00010'),
    (3, 1, 'Meppadi High Altitude', 'medium'::severity_level,  3, '+91 98765 00020')
ON CONFLICT (zone_id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('disasters', 'disaster_id'), GREATEST(1, (SELECT COALESCE(MAX(disaster_id), 1) FROM disasters)));
SELECT setval(pg_get_serial_sequence('shelters', 'shelter_id'),   GREATEST(3, (SELECT COALESCE(MAX(shelter_id), 1)   FROM shelters)));
SELECT setval(pg_get_serial_sequence('zones', 'zone_id'),         GREATEST(3, (SELECT COALESCE(MAX(zone_id), 1)      FROM zones)));


-- ── 3. Seed Demonstration App Users (5 Roles) ───────────────────────────────
-- Inserted AFTER zones exist: fk_app_users_assigned_zone (assigned_zone_id → zones.zone_id)
-- and fk_app_users_person (person_id → people.person_id) are now both satisfiable.
INSERT INTO app_users (user_id, person_id, username, password_hash, role, assigned_zone_id, is_active)
VALUES
    (1, 101, 'admin',           '$2a$10$HaUWHrSnxmG74qamG.rU/eJgyb57/emdR4f45/JjpeTtdvLD/wfr.', 'SYSTEM_ADMIN',          NULL, true),
    (2, 102, 'zone_coord',      '$2a$10$HaUWHrSnxmG74qamG.rU/eJgyb57/emdR4f45/JjpeTtdvLD/wfr.', 'ZONE_COORDINATOR',      1,    true),
    (3, 103, 'warehouse_mgr',   '$2a$10$HaUWHrSnxmG74qamG.rU/eJgyb57/emdR4f45/JjpeTtdvLD/wfr.', 'WAREHOUSE_MANAGER',     NULL, true),
    (4, 104, 'volunteer_coord', '$2a$10$HaUWHrSnxmG74qamG.rU/eJgyb57/emdR4f45/JjpeTtdvLD/wfr.', 'VOLUNTEER_COORDINATOR', NULL, true),
    (5, 105, 'analytics_user',   '$2a$10$HaUWHrSnxmG74qamG.rU/eJgyb57/emdR4f45/JjpeTtdvLD/wfr.', 'ANALYTICS_USER',       NULL, true)
ON CONFLICT (username) DO NOTHING;

SELECT setval(pg_get_serial_sequence('app_users', 'user_id'), GREATEST(5, (SELECT COALESCE(MAX(user_id), 1) FROM app_users)));


-- ── 4. Seed Resource Categories and Resources ────────────────────────────────
INSERT INTO resource_categories (category_id, name, parent_id)
VALUES
    (1, 'Food & Water', NULL),
    (2, 'Medical Supplies', NULL),
    (3, 'Shelter & Clothing', NULL),
    (4, 'First Aid Essentials', 2)
ON CONFLICT (category_id) DO NOTHING;

INSERT INTO resources (resource_id, name, category_id, unit)
VALUES
    (1, 'Packaged Drinking Water (20L Drum)', 1, 'drums'),
    (2, 'Rice Ration Pack (10kg)',           1, 'packets'),
    (3, 'Emergency First Aid Kit',           4, 'kits'),
    (4, 'Woolen Blankets',                   3, 'pieces'),
    (5, 'Paracetamol Tablets 500mg',         2, 'strips')
ON CONFLICT (resource_id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('resource_categories', 'category_id'), GREATEST(4, (SELECT COALESCE(MAX(category_id), 1) FROM resource_categories)));
SELECT setval(pg_get_serial_sequence('resources', 'resource_id'),           GREATEST(5, (SELECT COALESCE(MAX(resource_id), 1)  FROM resources)));

-- Seed initial stock in shelter 1 and 2
INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level, last_updated)
VALUES
    (1, 1, 150, 20, now()),
    (1, 2, 200, 30, now()),
    (1, 3, 40,  10, now()),
    (1, 4, 300, 50, now()),
    (2, 1, 80,  20, now()),
    (2, 2, 100, 25, now()),
    (2, 5, 25,  30, now())   -- below reorder level to exercise low stock alert
ON CONFLICT (shelter_id, resource_id) DO NOTHING;


-- ── 5. Seed Operational Resource Requests ───────────────────────────────────
INSERT INTO resource_requests (request_id, zone_id, resource_id, destination_shelter_id, quantity_requested, urgency, status, requested_at, fulfilled_at)
VALUES
    (1, 1, 1, 1, 100, 'critical', 'fulfilled', now() - INTERVAL '2 days', now() - INTERVAL '1 day'),
    (2, 1, 3, 1, 25,  'high',     'pending',   now() - INTERVAL '1 day',  NULL),
    (3, 1, 4, 1, 150, 'medium',   'pending',   now() - INTERVAL '4 hours', NULL),
    (4, 2, 2, 2, 80,  'high',     'pending',   now() - INTERVAL '6 hours', NULL),
    (5, 3, 5, 3, 40,  'low',      'pending',   now() - INTERVAL '2 hours', NULL)
ON CONFLICT (request_id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('resource_requests', 'request_id'), GREATEST(5, (SELECT COALESCE(MAX(request_id), 1) FROM resource_requests)));


-- ── 6. Seed Historical Distribution Logs ────────────────────────────────────
INSERT INTO distribution_log (log_id, source_shelter, dest_shelter, resource_id, quantity, distributed_at)
VALUES
    (1, 2, 1, 1, 100, now() - INTERVAL '1 day'),
    (2, 1, 2, 4, 50,  now() - INTERVAL '3 days')
ON CONFLICT (log_id, distributed_at) DO NOTHING;


-- ── 7. Seed Incoming Donation Record ────────────────────────────────────────
INSERT INTO donation_records (donation_id, donor_name, items, receiving_shelter, received_at)
VALUES
    (1, 'Rotary Club Emergency Relief', '[{"resource_id": 1, "quantity": 50}, {"resource_id": 4, "quantity": 100}]'::JSONB, 1, now() - INTERVAL '12 hours')
ON CONFLICT (donation_id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('donation_records', 'donation_id'), GREATEST(1, (SELECT COALESCE(MAX(donation_id), 1) FROM donation_records)));


-- ── 8. Refresh Materialized Views with Seed Baseline ────────────────────────
REFRESH MATERIALIZED VIEW mv_zone_resource_summary;
REFRESH MATERIALIZED VIEW mv_daily_distribution_stats;
