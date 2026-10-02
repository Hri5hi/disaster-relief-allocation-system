-- V006__operations_logic.sql
-- Owner: Dev C
-- Dependencies: V001 (types), V002 (core schema), V003 (constraints),
--               V004 (people, volunteers, affected_persons, shifts),
--               V005 (resource_requests, distribution_log, donation_records, audit_log)
-- Creates:
--   1. fn_allocate_resources (atomic allocation with advisory lock & inventory movement)
--   2. fn_process_donation (JSONB donation intake with atomic inventory UPSERT)
--   3. create_monthly_distribution_partition (dynamic partition creation helper)
--   4. fn_register_volunteer & fn_schedule_volunteer_shift (domain helper functions)
--   5. trg_low_stock_alert (LISTEN/NOTIFY channel 'low_stock_alert' on threshold crossing)
--   6. trg_cascade_disaster_close (cancels pending requests on resolved/closed disaster)
--   7. trg_audit_log (captures INSERT/UPDATE/DELETE across the 10 mutable operational tables)

-- ── 1. fn_allocate_resources ────────────────────────────────────────────────
-- Atomically fulfills a resource request: locks row, validates stock, decrements
-- source inventory, increments destination inventory, marks request fulfilled,
-- logs the movement in distribution_log, and returns the transfer details.

CREATE OR REPLACE FUNCTION fn_allocate_resources(
    p_request_id        INTEGER,
    p_source_shelter_id INTEGER
)
RETURNS TABLE (
    log_id          INTEGER,
    request_id      INTEGER,
    source_shelter  INTEGER,
    dest_shelter    INTEGER,
    resource_id     INTEGER,
    quantity        INTEGER,
    fulfilled_at    TIMESTAMPTZ
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_zone_id               INTEGER;
    v_resource_id           INTEGER;
    v_dest_shelter_id       INTEGER;
    v_qty_requested         INTEGER;
    v_status                VARCHAR(20);
    v_available_qty         INTEGER;
    v_fulfilled_at          TIMESTAMPTZ;
    v_log_id                INTEGER;
BEGIN
    -- Acquire transaction-scoped advisory lock for the request
    PERFORM pg_advisory_xact_lock(hashtext('allocation_lock'), p_request_id);

    -- Lock and retrieve resource request
    SELECT r.zone_id, r.resource_id, r.destination_shelter_id, r.quantity_requested, r.status
    INTO v_zone_id, v_resource_id, v_dest_shelter_id, v_qty_requested, v_status
    FROM resource_requests r
    WHERE r.request_id = p_request_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Resource request % does not exist', p_request_id
            USING ERRCODE = 'P0002';
    END IF;

    IF v_status <> 'pending' THEN
        RAISE EXCEPTION 'Resource request % is already %', p_request_id, v_status
            USING ERRCODE = '22000';
    END IF;

    IF p_source_shelter_id = v_dest_shelter_id THEN
        RAISE EXCEPTION 'Source shelter and destination shelter cannot be identical'
            USING ERRCODE = '22000';
    END IF;

    -- Lock and inspect source shelter inventory
    SELECT si.quantity
    INTO v_available_qty
    FROM shelter_inventory si
    WHERE si.shelter_id = p_source_shelter_id AND si.resource_id = v_resource_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Source shelter % does not stock resource %', p_source_shelter_id, v_resource_id
            USING ERRCODE = '22000';
    END IF;

    IF v_available_qty < v_qty_requested THEN
        RAISE EXCEPTION 'Insufficient stock at source shelter: requested %, available %',
            v_qty_requested, v_available_qty
            USING ERRCODE = '22000';
    END IF;

    -- 1. Decrement source shelter inventory
    UPDATE shelter_inventory
    SET quantity = quantity - v_qty_requested,
        last_updated = now()
    WHERE shelter_id = p_source_shelter_id AND resource_id = v_resource_id;

    -- 2. Increment destination shelter inventory (UPSERT)
    INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level, last_updated)
    VALUES (v_dest_shelter_id, v_resource_id, v_qty_requested, 10, now())
    ON CONFLICT (shelter_id, resource_id)
    DO UPDATE SET
        quantity = shelter_inventory.quantity + EXCLUDED.quantity,
        last_updated = now();

    -- 3. Mark request as fulfilled
    v_fulfilled_at := now();
    UPDATE resource_requests
    SET status = 'fulfilled',
        fulfilled_at = v_fulfilled_at
    WHERE request_id = p_request_id;

    -- 4. Record append-only entry in partitioned distribution_log
    INSERT INTO distribution_log (source_shelter, dest_shelter, resource_id, quantity, distributed_at)
    VALUES (p_source_shelter_id, v_dest_shelter_id, v_resource_id, v_qty_requested, v_fulfilled_at)
    RETURNING distribution_log.log_id INTO v_log_id;

    RETURN QUERY SELECT v_log_id, p_request_id, p_source_shelter_id, v_dest_shelter_id, v_resource_id, v_qty_requested, v_fulfilled_at;
END;
$$;

COMMENT ON FUNCTION fn_allocate_resources(INTEGER, INTEGER) IS
    'Performs atomic resource allocation from source shelter to fulfill a request with row-level locks and inventory updates.';


-- ── 2. fn_process_donation ──────────────────────────────────────────────────
-- Records an incoming donation with JSONB line items and performs atomic UPSERT
-- across shelter_inventory lines.

CREATE OR REPLACE FUNCTION fn_process_donation(
    p_donor_name            VARCHAR(100),
    p_receiving_shelter_id  INTEGER,
    p_items                 JSONB
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_donation_id   INTEGER;
    v_item          JSONB;
    v_res_id        INTEGER;
    v_qty           INTEGER;
BEGIN
    IF p_items IS NULL OR jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) = 0 THEN
        RAISE EXCEPTION 'Donation must contain a non-empty items JSON array'
            USING ERRCODE = '22000';
    END IF;

    -- Verify receiving shelter exists
    PERFORM 1 FROM shelters WHERE shelter_id = p_receiving_shelter_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Receiving shelter % does not exist', p_receiving_shelter_id
            USING ERRCODE = 'P0002';
    END IF;

    -- Insert donation intake record
    INSERT INTO donation_records (donor_name, items, receiving_shelter, received_at)
    VALUES (p_donor_name, p_items, p_receiving_shelter_id, now())
    RETURNING donation_id INTO v_donation_id;

    -- Unpack JSONB array and atomically UPSERT shelter inventory
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items)
    LOOP
        v_res_id := (v_item->>'resource_id')::INTEGER;
        v_qty    := (v_item->>'quantity')::INTEGER;

        IF v_res_id IS NULL OR v_qty IS NULL OR v_qty <= 0 THEN
            RAISE EXCEPTION 'Each donation item must contain valid resource_id and positive quantity'
                USING ERRCODE = '22000';
        END IF;

        PERFORM 1 FROM resources WHERE resource_id = v_res_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Resource % does not exist in master catalog', v_res_id
                USING ERRCODE = 'P0002';
        END IF;

        INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level, last_updated)
        VALUES (p_receiving_shelter_id, v_res_id, v_qty, 10, now())
        ON CONFLICT (shelter_id, resource_id)
        DO UPDATE SET
            quantity = shelter_inventory.quantity + EXCLUDED.quantity,
            last_updated = now();
    END LOOP;

    RETURN v_donation_id;
END;
$$;

COMMENT ON FUNCTION fn_process_donation(VARCHAR, INTEGER, JSONB) IS
    'Processes a donation record and performs atomic stock UPSERT for each resource line.';


-- ── 3. Monthly Partition Creation Helper ────────────────────────────────────
CREATE OR REPLACE FUNCTION create_monthly_distribution_partition(target_date DATE)
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    v_start             TIMESTAMPTZ;
    v_end               TIMESTAMPTZ;
    v_partition_name    TEXT;
    v_sql               TEXT;
BEGIN
    v_start := date_trunc('month', target_date::TIMESTAMPTZ);
    v_end   := v_start + INTERVAL '1 month';
    v_partition_name := 'distribution_log_' || to_char(v_start, 'YYYY_MM');

    v_sql := format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF distribution_log FOR VALUES FROM (%L) TO (%L)',
        v_partition_name, v_start, v_end
    );
    EXECUTE v_sql;
    RETURN v_partition_name;
END;
$$;

COMMENT ON FUNCTION create_monthly_distribution_partition(DATE) IS
    'Creates a monthly range partition for distribution_log for the target date.';


-- ── 4. Volunteer Domain Helpers ─────────────────────────────────────────────
CREATE OR REPLACE FUNCTION fn_register_volunteer(
    p_name          VARCHAR(100),
    p_contact       VARCHAR(20),
    p_email         VARCHAR(100),
    p_skills        TEXT[],
    p_availability  TSRANGE,
    p_assigned_zone INTEGER
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_person_id INTEGER;
BEGIN
    INSERT INTO volunteers (name, contact, email, skills, availability, assigned_zone)
    VALUES (p_name, p_contact, p_email, p_skills, p_availability, p_assigned_zone)
    RETURNING person_id INTO v_person_id;
    RETURN v_person_id;
END;
$$;

CREATE OR REPLACE FUNCTION fn_schedule_volunteer_shift(
    p_volunteer_id  INTEGER,
    p_zone_id       INTEGER,
    p_duty_period   TSRANGE
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_shift_id INTEGER;
BEGIN
    INSERT INTO volunteer_shifts (volunteer_id, zone_id, duty_period)
    VALUES (p_volunteer_id, p_zone_id, p_duty_period)
    RETURNING shift_id INTO v_shift_id;
    RETURN v_shift_id;
END;
$$;


-- ── 5. Trigger: trg_low_stock_alert ─────────────────────────────────────────
-- Fires on inventory modification. Emits JSON notification strictly when stock
-- transitions from at/above reorder level to below it (avoiding notification storms).
-- Format matches Dev A's LowStockAlert Java model contract:
--   {"shelter_id": X, "resource_id": Y, "quantity": Z, "reorder_level": W}

CREATE OR REPLACE FUNCTION fn_low_stock_alert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (TG_OP = 'INSERT' AND NEW.quantity < NEW.reorder_level)
       OR (TG_OP = 'UPDATE' AND OLD.quantity >= OLD.reorder_level AND NEW.quantity < NEW.reorder_level) THEN
        PERFORM pg_notify(
            'low_stock_alert',
            json_build_object(
                'shelter_id', NEW.shelter_id,
                'resource_id', NEW.resource_id,
                'quantity', NEW.quantity,
                'reorder_level', NEW.reorder_level
            )::text
        );
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_low_stock_alert
    AFTER INSERT OR UPDATE OF quantity, reorder_level
    ON shelter_inventory
    FOR EACH ROW
    EXECUTE FUNCTION fn_low_stock_alert();

COMMENT ON TRIGGER trg_low_stock_alert ON shelter_inventory IS
    'Emits LISTEN/NOTIFY alert on channel low_stock_alert when quantity falls below reorder_level.';


-- ── 6. Trigger: trg_cascade_disaster_close ──────────────────────────────────
-- When a disaster transitions to 'resolved' or 'closed', automatically cancels
-- all associated pending resource requests.

CREATE OR REPLACE FUNCTION fn_cascade_disaster_close()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status IN ('resolved'::disaster_status, 'closed'::disaster_status)
       AND OLD.status NOT IN ('resolved'::disaster_status, 'closed'::disaster_status) THEN
        UPDATE resource_requests
        SET status = 'cancelled'
        WHERE status = 'pending'
          AND zone_id IN (
              SELECT zone_id FROM zones WHERE disaster_id = NEW.disaster_id
          );
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cascade_disaster_close
    AFTER UPDATE OF status
    ON disasters
    FOR EACH ROW
    EXECUTE FUNCTION fn_cascade_disaster_close();

COMMENT ON TRIGGER trg_cascade_disaster_close ON disasters IS
    'Automatically cancels pending resource requests when a disaster is marked resolved or closed.';


-- ── 7. Trigger: trg_audit_log (Across 10 Mutable Tables) ────────────────────
-- Populates audit_log with JSON snapshots of OLD and NEW rows on all mutations.

CREATE OR REPLACE FUNCTION fn_audit_log()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_old JSONB := NULL;
    v_new JSONB := NULL;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_new := to_jsonb(NEW);
    ELSIF TG_OP = 'DELETE' THEN
        v_old := to_jsonb(OLD);
    ELSIF TG_OP = 'UPDATE' THEN
        v_old := to_jsonb(OLD);
        v_new := to_jsonb(NEW);
    END IF;

    INSERT INTO audit_log (table_name, operation, old_values, new_values, changed_by, changed_at)
    VALUES (TG_TABLE_NAME, TG_OP, v_old, v_new, CURRENT_USER, now());

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$;

COMMENT ON FUNCTION fn_audit_log() IS
    'Trigger function recording full JSONB state diffs in audit_log on INSERT, UPDATE, or DELETE.';

-- Attach audit trigger to all 10 mutable operational tables:
CREATE TRIGGER trg_audit_disasters
    AFTER INSERT OR UPDATE OR DELETE ON disasters
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_zones
    AFTER INSERT OR UPDATE OR DELETE ON zones
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_shelters
    AFTER INSERT OR UPDATE OR DELETE ON shelters
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_resources
    AFTER INSERT OR UPDATE OR DELETE ON resources
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_shelter_inventory
    AFTER INSERT OR UPDATE OR DELETE ON shelter_inventory
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_volunteers
    AFTER INSERT OR UPDATE OR DELETE ON volunteers
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_affected_persons
    AFTER INSERT OR UPDATE OR DELETE ON affected_persons
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_resource_requests
    AFTER INSERT OR UPDATE OR DELETE ON resource_requests
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_volunteer_shifts
    AFTER INSERT OR UPDATE OR DELETE ON volunteer_shifts
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();

CREATE TRIGGER trg_audit_donation_records
    AFTER INSERT OR UPDATE OR DELETE ON donation_records
    FOR EACH ROW EXECUTE FUNCTION fn_audit_log();
