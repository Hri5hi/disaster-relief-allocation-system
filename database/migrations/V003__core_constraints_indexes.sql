ALTER TABLE disasters
    ADD CONSTRAINT chk_disasters_name_nonblank
        CHECK (btrim(name) <> ''),
    ADD CONSTRAINT chk_disasters_type_nonblank
        CHECK (type IS NULL OR btrim(type) <> ''),
    ADD CONSTRAINT chk_disasters_active_period_nonempty
        CHECK (active_period IS NULL OR NOT isempty(active_period));

ALTER TABLE zones
    ADD CONSTRAINT chk_zones_name_nonblank
        CHECK (btrim(name) <> ''),
    ADD CONSTRAINT chk_zones_coordinator_contact_nonblank
        CHECK (coordinator_contact IS NULL OR btrim(coordinator_contact) <> ''),
    ADD CONSTRAINT uq_zones_disaster_name
        UNIQUE (disaster_id, name);

ALTER TABLE shelters
    ADD CONSTRAINT chk_shelters_name_nonblank
        CHECK (btrim(name) <> ''),
    ADD CONSTRAINT chk_shelters_address_nonblank
        CHECK (btrim(address) <> ''),
    ADD CONSTRAINT chk_shelters_location_bounds
        CHECK (
            location IS NULL
            OR (
                (location).latitude IS NOT NULL
                AND (location).longitude IS NOT NULL
                AND (location).latitude BETWEEN -90.0 AND 90.0
                AND (location).longitude BETWEEN -180.0 AND 180.0
            )
        ),
    ADD CONSTRAINT chk_shelters_capacity_positive
        CHECK (capacity > 0),
    ADD CONSTRAINT chk_shelters_occupancy_nonnegative
        CHECK (current_occupancy >= 0),
    ADD CONSTRAINT chk_shelters_occupancy_within_capacity
        CHECK (current_occupancy <= capacity);

ALTER TABLE resource_categories
    ADD CONSTRAINT chk_resource_categories_name_nonblank
        CHECK (btrim(name) <> ''),
    ADD CONSTRAINT chk_resource_categories_not_self_parent
        CHECK (parent_id IS NULL OR parent_id <> category_id);

ALTER TABLE resources
    ADD CONSTRAINT chk_resources_name_nonblank
        CHECK (btrim(name) <> ''),
    ADD CONSTRAINT chk_resources_unit_nonblank
        CHECK (unit IS NULL OR btrim(unit) <> '');

ALTER TABLE shelter_inventory
    ADD CONSTRAINT chk_shelter_inventory_quantity_nonnegative
        CHECK (quantity >= 0),
    ADD CONSTRAINT chk_shelter_inventory_reorder_nonnegative
        CHECK (reorder_level >= 0);

CREATE INDEX idx_zones_disaster_id
    ON zones (disaster_id);

CREATE INDEX idx_zones_primary_shelter_id
    ON zones (primary_shelter_id)
    WHERE primary_shelter_id IS NOT NULL;

CREATE INDEX idx_resource_categories_parent_id
    ON resource_categories (parent_id);

CREATE INDEX idx_resources_category_id
    ON resources (category_id);

CREATE INDEX idx_shelter_inventory_resource_id
    ON shelter_inventory (resource_id);

CREATE INDEX idx_disasters_open
    ON disasters (disaster_id)
    WHERE status IN ('active'::disaster_status, 'monitoring'::disaster_status);

CREATE INDEX idx_shelters_operational
    ON shelters (shelter_id)
    WHERE status = 'operational'::shelter_status;
