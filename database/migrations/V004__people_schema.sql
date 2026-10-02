-- V004__people_schema.sql
-- Owner: Dev B
-- Dependencies: V001 (btree_gist), V002 (shelters, zones)
-- Creates: people (base), volunteers (INHERITS people), affected_persons (INHERITS people),
--          volunteer_shifts (with GiST exclusion), trg_auto_update_shelter_occupancy

-- ── Table 7: people (superclass / base) ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS people (
    person_id   SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    contact     VARCHAR(20),
    email       VARCHAR(100)
);

COMMENT ON TABLE people IS
    'Superclass for all persons. volunteers and affected_persons INHERIT this table. '
    'SELECT * FROM people returns rows from all three tables combined.';

-- ── Table 8: volunteers (INHERITS people) ────────────────────────────────────
-- person_id is re-declared as SERIAL PRIMARY KEY to give volunteers their own
-- sequence. inherited columns (name, contact, email) are automatically present.
CREATE TABLE IF NOT EXISTS volunteers (
    person_id       SERIAL PRIMARY KEY,
    skills          TEXT[],
    availability    TSRANGE,
    assigned_zone   INTEGER REFERENCES zones(zone_id) ON DELETE SET NULL
) INHERITS (people);

COMMENT ON TABLE volunteers IS
    'Volunteer persons. Inherits person_id, name, contact, email from people. '
    'skills is a PostgreSQL TEXT[] array; availability is a tsrange.';
COMMENT ON COLUMN volunteers.skills IS
    'Array of skill tags, e.g. {first_aid,driving,cooking}. '
    'Searchable with: skills @> ARRAY[''first_aid'']::text[]';
COMMENT ON COLUMN volunteers.availability IS
    'Volunteer availability window as a tsrange, e.g. [2026-07-09 08:00,2026-07-20 18:00). '
    'NULL = not specified.';

-- GIN index on skills for fast @> and && queries
CREATE INDEX IF NOT EXISTS idx_volunteers_skills
    ON volunteers USING GIN (skills);

-- GiST index on availability for range queries
CREATE INDEX IF NOT EXISTS idx_volunteers_availability
    ON volunteers USING GIST (availability);

-- Index on zone assignment for lookup
CREATE INDEX IF NOT EXISTS idx_volunteers_assigned_zone
    ON volunteers (assigned_zone);

-- ── Table 9: affected_persons (INHERITS people) ──────────────────────────────
CREATE TABLE IF NOT EXISTS affected_persons (
    person_id       SERIAL PRIMARY KEY,
    medical_needs   JSONB,
    shelter_id      INTEGER REFERENCES shelters(shelter_id) ON DELETE SET NULL,
    registered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
) INHERITS (people);

COMMENT ON TABLE affected_persons IS
    'Displaced persons registered at shelters. Inherits from people. '
    'medical_needs is JSONB: e.g. {"conditions": ["diabetes"], "wheelchair": true}.';
COMMENT ON COLUMN affected_persons.shelter_id IS
    'FK to shelters. When this is set, shelters.current_occupancy is auto-updated '
    'by trg_auto_update_shelter_occupancy. Java must never write current_occupancy directly.';

-- GIN index on medical_needs for JSONB containment queries
CREATE INDEX IF NOT EXISTS idx_affected_persons_medical_needs
    ON affected_persons USING GIN (medical_needs);

-- Index on shelter for occupancy lookups
CREATE INDEX IF NOT EXISTS idx_affected_persons_shelter_id
    ON affected_persons (shelter_id);

-- ── Table 12: volunteer_shifts ────────────────────────────────────────────────
-- Note: NOT inheriting. Separate join entity.
CREATE TABLE IF NOT EXISTS volunteer_shifts (
    shift_id        SERIAL PRIMARY KEY,
    volunteer_id    INTEGER NOT NULL REFERENCES volunteers(person_id) ON DELETE CASCADE,
    zone_id         INTEGER NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    duty_period     TSRANGE NOT NULL,
    EXCLUDE USING GIST (volunteer_id WITH =, duty_period WITH &&)
);

COMMENT ON TABLE volunteer_shifts IS
    'Assigns volunteers to zones for specific duty periods. '
    'The GiST exclusion constraint prevents the same volunteer from having overlapping shifts.';
COMMENT ON COLUMN volunteer_shifts.duty_period IS
    'Duty window as a tsrange, e.g. [2026-07-09 08:00,2026-07-09 20:00). '
    'Exclusion constraint: no two rows may have equal volunteer_id AND overlapping duty_period.';

-- Index on volunteer for shift lookups by volunteer
CREATE INDEX IF NOT EXISTS idx_volunteer_shifts_volunteer_id
    ON volunteer_shifts (volunteer_id);

-- GiST index on duty_period for range queries (overlap detection)
CREATE INDEX IF NOT EXISTS idx_volunteer_shifts_duty_period
    ON volunteer_shifts USING GIST (duty_period);

-- ── Trigger: trg_auto_update_shelter_occupancy ────────────────────────────────
-- Fires AFTER INSERT, DELETE, or UPDATE OF shelter_id on affected_persons.
-- Recalculates shelters.current_occupancy by counting affected_persons at that shelter.
-- Java NEVER writes shelters.current_occupancy directly.

CREATE OR REPLACE FUNCTION fn_update_shelter_occupancy()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- On INSERT: update new shelter occupancy
    IF TG_OP = 'INSERT' AND NEW.shelter_id IS NOT NULL THEN
        UPDATE shelters
        SET current_occupancy = (
            SELECT COUNT(*) FROM affected_persons WHERE shelter_id = NEW.shelter_id
        )
        WHERE shelter_id = NEW.shelter_id;

    -- On DELETE: update old shelter occupancy
    ELSIF TG_OP = 'DELETE' AND OLD.shelter_id IS NOT NULL THEN
        UPDATE shelters
        SET current_occupancy = (
            SELECT COUNT(*) FROM affected_persons WHERE shelter_id = OLD.shelter_id
        )
        WHERE shelter_id = OLD.shelter_id;

    -- On UPDATE OF shelter_id: update both old and new shelter occupancy
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.shelter_id IS NOT NULL THEN
            UPDATE shelters
            SET current_occupancy = (
                SELECT COUNT(*) FROM affected_persons WHERE shelter_id = OLD.shelter_id
            )
            WHERE shelter_id = OLD.shelter_id;
        END IF;
        IF NEW.shelter_id IS NOT NULL THEN
            UPDATE shelters
            SET current_occupancy = (
                SELECT COUNT(*) FROM affected_persons WHERE shelter_id = NEW.shelter_id
            )
            WHERE shelter_id = NEW.shelter_id;
        END IF;
    END IF;

    RETURN NULL; -- AFTER trigger; return value is ignored
END;
$$;

CREATE TRIGGER trg_auto_update_shelter_occupancy
    AFTER INSERT OR DELETE OR UPDATE OF shelter_id
    ON affected_persons
    FOR EACH ROW
    EXECUTE FUNCTION fn_update_shelter_occupancy();

COMMENT ON TRIGGER trg_auto_update_shelter_occupancy ON affected_persons IS
    'Automatically recalculates shelters.current_occupancy when affected persons '
    'are registered, removed, or reassigned. Never update current_occupancy from Java.';