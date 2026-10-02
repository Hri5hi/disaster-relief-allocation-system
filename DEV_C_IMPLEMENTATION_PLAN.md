# Dev C — Complete Implementation Plan
## Disaster Relief Resource Allocation System (DRRAS) — Operations, Security, Analytics, and Integration

**Produced by:** Senior Software Architect / Technical Lead  
**For execution by:** Developer C (`feature/dev-c`)  
**Scope:** Operations Schema & Logic, Authentication, RBAC, Allocations, Donations, Partitions, Audit Trail, Analytics, Views/MVs, RLS, Documentation, and Integration Testing  
**Current Milestone:** Wave 2 Starting Line (Dev A Wave 2 merged, Dev B V004 merged)  

---

# 1. Executive Summary

Dev C is responsible for the core operational lifecycle, security architecture, and system intelligence of DRRAS. While Dev A has built the core geography/shelter/inventory foundation and Dev B builds the people/volunteer domain, Dev C transforms these individual entities into a fully operational, auditable, and secure disaster response platform.

### Core Responsibilities:
1. **Database Operations Migrations (`database/migrations/`):**
   * **`V005__operations_schema.sql` (Wave 2):** `resource_requests` (with `destination_shelter_id` and generated `priority_score`), range-partitioned `distribution_log` (active, next-month, and default partitions), `donation_records`, and `audit_log`.
   * **`V006__operations_logic.sql` (Wave 3):** Atomic `fn_allocate_resources`, atomic `fn_process_donation`, partition creation helper, `trg_low_stock_alert` (integrating with Dev A's listener), `trg_cascade_disaster_close`, and `trg_audit_log` across all 10 mutable tables.
   * **`V007__security_views_analytics.sql` (Wave 3):** `app_users` table (linked to `people`), Row-Level Security (RLS) policies scoped by `app.current_zone_id`, 4 views, 2 materialized views (with unique indexes for concurrent refresh), and `fn_zone_deficit_report`.
   * **`V008__development_seed.sql` (Wave 3):** Idempotent seed data for all 5 roles with pre-computed BCrypt hashes and coherent operational test data.
2. **Authentication & Security (`auth/` & `util/`):**
   * Real BCrypt password verification via `org.mindrot.jbcrypt.BCrypt`.
   * Role and assigned zone loading from database (client-side ComboBox selection is ignored during authentication).
   * Central RBAC screen-to-role navigation enforcement in `Utility.changeScene`.
   * Session security with `set_config('app.current_zone_id', ?, true)` transaction scoping.
3. **Persistence & Business Layer (`dao/operations/` & `service/operations/`):**
   * Models, DAOs, and transactional services for resource requests, allocations, donations, distribution logs, audit logs, and analytics.
   * Parameterized JDBC only (no concatenated SQL, no `DriverManager` calls).
   * Safe domain error mapping to `ServiceResult<T>`.
4. **Presentation Wiring (7 Controllers):**
   * Asynchronous service execution via JavaFX `Task<ServiceResult<T>>` for `LoginController`, `DashboardController`, `ResourceRequestController`, `DonationController`, `DistributionLogController`, `AuditLogController`, and `AnalyticsController`.
5. **Documentation & Diagrams:**
   * Update schema count to 15 physical tables (`app_users` as the 15th table) across all docs, update ER diagrams, and update `README.md`.
6. **Integration & Concurrency Testing:**
   * Concurrency allocation tests proving no double fulfillment or inventory overdraft, RLS isolation tests, partition pruning validation, and audit trail verification.

---

# 2. Strict Ownership Boundaries & Conflict Prevention

To guarantee zero negative overlap with Dev A and Dev B:

### 2.1 Owned Files (Dev C ONLY)
```text
database/migrations/V005__operations_schema.sql
database/migrations/V006__operations_logic.sql
database/migrations/V007__security_views_analytics.sql
database/migrations/V008__development_seed.sql
src/main/java/com/groupa13/disasterrelief/auth/**
src/main/java/com/groupa13/disasterrelief/model/ResourceRequest.java
src/main/java/com/groupa13/disasterrelief/model/DistributionLog.java
src/main/java/com/groupa13/disasterrelief/model/AuditLog.java
src/main/java/com/groupa13/disasterrelief/model/operations/**
src/main/java/com/groupa13/disasterrelief/dao/operations/**
src/main/java/com/groupa13/disasterrelief/service/operations/**
src/main/java/com/groupa13/disasterrelief/util/UserRole.java
src/main/java/com/groupa13/disasterrelief/util/UserSession.java
src/main/java/com/groupa13/disasterrelief/util/Utility.java
src/main/java/com/groupa13/disasterrelief/controller/LoginController.java
src/main/java/com/groupa13/disasterrelief/controller/DashboardController.java
src/main/java/com/groupa13/disasterrelief/controller/ResourceRequestController.java
src/main/java/com/groupa13/disasterrelief/controller/DonationController.java
src/main/java/com/groupa13/disasterrelief/controller/DistributionLogController.java
src/main/java/com/groupa13/disasterrelief/controller/AuditLogController.java
src/main/java/com/groupa13/disasterrelief/controller/AnalyticsController.java
src/test/java/com/groupa13/disasterrelief/operations/**
src/test/java/com/groupa13/disasterrelief/integration/**
README.md
project_description_v2.md
docs/3.1_detailed_description.md
docs/3.2_er_diagram.md
docs/3.3_relational_schema.md
docs/3.4_normalization_and_fd_analysis.md
database/er-diagram/drras_er_diagram.drawio
```

### 2.2 Files You Must NEVER Modify
* **Dev A Files:** `build.gradle`, `docker-compose.yml`, `config/application.properties.example`, `database/migrations/V001*`, `V002*`, `V003*`, `src/main/java/.../db/**`, `src/main/java/.../model/core/**`, `src/main/java/.../dao/core/**`, `src/main/java/.../service/core/**`, `DisasterController.java`, `ZoneController.java`, `ShelterController.java`, `ResourceController.java`, `InventoryController.java`, and `src/test/java/.../core/**`.
* **Dev B Files:** `database/migrations/V004*`, `src/main/java/.../model/Person.java`, `AffectedPerson.java`, `Volunteer.java`, `VolunteerShift.java`, `src/main/java/.../dao/people/**`, `src/main/java/.../service/people/**`, `AffectedPersonController.java`, `VolunteerController.java`, `VolunteerShiftController.java`, and `src/test/java/.../people/**`.
* **FXML & CSS Files:** The presentation layout in `src/main/resources/fxml/` and `src/main/resources/css/` is frozen; connect to existing fx:id fields without renaming FXML components.

---

# 3. Step-by-Step Implementation Roadmap

---

## Phase 1: Wave 2 — Operations Schema Migration (`V005`)

### 1.1 Objectives
Deliver `V005__operations_schema.sql` defining:
* `resource_requests`
* `distribution_log` (range partitioned by month)
* `donation_records`
* `audit_log`

### 1.2 Specifications & Constraints
1. **`resource_requests`**:
   * Columns: `request_id` (IDENTITY/SERIAL PK), `zone_id` (INT NOT NULL FK $\rightarrow$ `zones`), `resource_id` (INT NOT NULL FK $\rightarrow$ `resources`), `destination_shelter_id` (INT NOT NULL FK $\rightarrow$ `shelters`), `quantity_requested` (INT NOT NULL CHECK $>0$), `urgency` (VARCHAR(10) CHECK `'critical'`, `'high'`, `'medium'`, `'low'`), `priority_score` (INT GENERATED ALWAYS AS ... STORED), `status` (VARCHAR(20) DEFAULT `'pending'` CHECK `'pending'`, `'fulfilled'`, `'cancelled'`), `requested_at` (TIMESTAMPTZ DEFAULT now()), `fulfilled_at` (TIMESTAMPTZ NULL).
   * **Generated Column Expression:**
     ```sql
     priority_score INTEGER GENERATED ALWAYS AS (
         (CASE urgency
             WHEN 'critical' THEN 40
             WHEN 'high' THEN 30
             WHEN 'medium' THEN 20
             WHEN 'low' THEN 10
             ELSE 0
          END) + (quantity_requested / 10)
     ) STORED
     ```
2. **`distribution_log`**:
   * Range-partitioned by `distributed_at`.
   * Columns: `log_id` (INT GENERATED BY DEFAULT AS IDENTITY), `source_shelter` (INT NOT NULL FK $\rightarrow$ `shelters`), `dest_shelter` (INT NOT NULL FK $\rightarrow$ `shelters`), `resource_id` (INT NOT NULL FK $\rightarrow$ `resources`), `quantity` (INT NOT NULL CHECK $>0$), `distributed_at` (TIMESTAMPTZ NOT NULL DEFAULT now()).
   * **Critical PostgreSQL Rule:** Composite Primary Key `PRIMARY KEY (log_id, distributed_at)` is mandatory for partitioned tables.
   * Child Partitions:
     * `distribution_log_2026_09` (historical baseline)
     * `distribution_log_2026_10` (active month)
     * `distribution_log_2026_11` (next month)
     * `distribution_log_default` (default catch-all partition)
3. **`donation_records`**:
   * Columns: `donation_id` (IDENTITY/SERIAL PK), `donor_name` (VARCHAR(100)), `items` (JSONB NOT NULL), `received_at` (TIMESTAMPTZ DEFAULT now()), `receiving_shelter` (INT REFERENCES `shelters`(shelter_id) ON DELETE RESTRICT).
   * GIN Index on `items`: `CREATE INDEX idx_donation_records_items ON donation_records USING GIN (items);`
4. **`audit_log`**:
   * Columns: `log_id` (IDENTITY/SERIAL PK), `table_name` (VARCHAR(50) NOT NULL), `operation` (VARCHAR(10) NOT NULL CHECK `'INSERT'`, `'UPDATE'`, `'DELETE'`), `old_values` (JSONB), `new_values` (JSONB), `changed_by` (VARCHAR(100)), `changed_at` (TIMESTAMPTZ DEFAULT now()).
   * GIN Indexes on `old_values` and `new_values` for diff search.

---

## Phase 2: Wave 3 — Operations Stored Procedures & Triggers (`V006`)

### 2.1 Stored Functions
1. **`fn_allocate_resources(p_request_id INT, p_source_shelter_id INT)`**:
   * Obtains an application-level advisory lock `pg_advisory_xact_lock(p_request_id)`.
   * Selects and locks the request row: `SELECT ... FROM resource_requests WHERE request_id = p_request_id FOR UPDATE;`.
   * Validates: request must exist, status must be `'pending'`.
   * Selects and locks source inventory: `SELECT quantity FROM shelter_inventory WHERE shelter_id = p_source_shelter_id AND resource_id = v_resource_id FOR UPDATE;`.
   * Validates stock: `v_available_qty >= v_requested_qty`. If insufficient, raises user-friendly exception: `'Insufficient stock at source shelter'`.
   * Decrements source inventory: `UPDATE shelter_inventory SET quantity = quantity - v_requested_qty WHERE ...`.
   * Increments / UPSERTs destination shelter inventory:
     ```sql
     INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level, last_updated)
     VALUES (v_destination_shelter_id, v_resource_id, v_requested_qty, 10, now())
     ON CONFLICT (shelter_id, resource_id)
     DO UPDATE SET quantity = shelter_inventory.quantity + EXCLUDED.quantity, last_updated = now();
     ```
   * Updates request state: `status = 'fulfilled'`, `fulfilled_at = now()`.
   * Inserts into `distribution_log` recording `source_shelter`, `dest_shelter`, `resource_id`, `quantity`, `now()`.
   * Returns a structured composite/table result: `(log_id INT, request_id INT, fulfilled_at TIMESTAMPTZ)`.
2. **`fn_process_donation(p_donor_name VARCHAR, p_receiving_shelter_id INT, p_items JSONB)`**:
   * Validates `p_items` is a JSON array and contains at least 1 element.
   * Inserts donation into `donation_records(donor_name, receiving_shelter, items)` RETURNING `donation_id`.
   * Iterates through `p_items` using `jsonb_array_elements`:
     * Extracts `resource_id` and `quantity`.
     * Validates quantity $> 0$.
     * UPSERTs into `shelter_inventory`:
       ```sql
       INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level, last_updated)
       VALUES (p_receiving_shelter_id, (item->>'resource_id')::INT, (item->>'quantity')::INT, 10, now())
       ON CONFLICT (shelter_id, resource_id)
       DO UPDATE SET quantity = shelter_inventory.quantity + EXCLUDED.quantity, last_updated = now();
       ```
   * Returns `donation_id`.
3. **`create_monthly_distribution_partition(target_date DATE)`**:
   * Maintenance helper calculating the start and end of the target month and executing dynamic DDL `CREATE TABLE IF NOT EXISTS ... PARTITION OF distribution_log FOR VALUES FROM (...) TO (...)`.

### 2.2 Triggers
1. **`trg_low_stock_alert` on `shelter_inventory`**:
   * Emits notification strictly matching Dev A's `LowStockAlert` contract:
     ```sql
     PERFORM pg_notify('low_stock_alert', json_build_object(
         'shelter_id', NEW.shelter_id,
         'resource_id', NEW.resource_id,
         'quantity', NEW.quantity,
         'reorder_level', NEW.reorder_level
     )::text);
     ```
   * Fired `AFTER UPDATE OR INSERT`: emits alert **only** when crossing threshold from $\ge$ `reorder_level` to $<$ `reorder_level` (preventing alert spam).
2. **`trg_cascade_disaster_close` on `disasters`**:
   * Fired `AFTER UPDATE OF status ON disasters`.
   * When `NEW.status IN ('resolved', 'closed')`:
     ```sql
     UPDATE resource_requests
     SET status = 'cancelled'
     WHERE status = 'pending'
       AND zone_id IN (SELECT zone_id FROM zones WHERE disaster_id = NEW.disaster_id);
     ```
3. **`trg_audit_log`**:
   * Generic audit trigger function capturing `TG_TABLE_NAME`, `TG_OP`, `to_jsonb(OLD)`, `to_jsonb(NEW)`, `session_user`, and `now()`.
   * Attached `AFTER INSERT OR UPDATE OR DELETE` across the **10 mutable tables**:
     `disasters`, `zones`, `shelters`, `resources`, `shelter_inventory`, `volunteers`, `affected_persons`, `resource_requests`, `volunteer_shifts`, and `donation_records`.

---

## Phase 3: Wave 3 — Security, RLS, Views & Analytics (`V007`)

### 3.1 `app_users` Table (Physical Table #15)
```sql
CREATE TABLE app_users (
    user_id SERIAL PRIMARY KEY,
    person_id INTEGER NOT NULL REFERENCES people(person_id) ON DELETE CASCADE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN (
        'SYSTEM_ADMIN', 'ZONE_COORDINATOR', 'WAREHOUSE_MANAGER',
        'VOLUNTEER_COORDINATOR', 'ANALYTICS_USER'
    )),
    assigned_zone_id INTEGER REFERENCES zones(zone_id) ON DELETE SET NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_zone_coordinator_zone CHECK (
        (role = 'ZONE_COORDINATOR' AND assigned_zone_id IS NOT NULL) OR
        (role <> 'ZONE_COORDINATOR')
    )
);
```

### 3.2 Row-Level Security (RLS)
Enable RLS on tables accessed by Zone Coordinators:
```sql
ALTER TABLE resource_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE affected_persons ENABLE ROW LEVEL SECURITY;

CREATE POLICY rls_resource_requests_zone_coord ON resource_requests
    FOR ALL
    USING (
        current_setting('app.current_zone_id', true) IS NULL
        OR current_setting('app.current_zone_id', true) = ''
        OR zone_id = NULLIF(current_setting('app.current_zone_id', true), '')::INTEGER
    );
```
*(Note: System admins and warehouse managers execute with `app.current_zone_id` unset or null, granting full access).*

### 3.3 Views & Materialized Views
1. **Views:**
   * `v_shelter_dashboard`: Aggregates shelter occupancy %, remaining capacity, and distinct resource count.
   * `v_pending_requests_ranked`: Ranks pending requests descending by `priority_score`, then ascending by `requested_at`.
   * `v_volunteer_availability`: Active volunteers with their skills, current shift count, and zone assignment.
   * `v_active_disasters`: Active disasters joined with affected zones and emergency shelter count.
2. **Materialized Views:**
   * `mv_zone_resource_summary`: Zone-level totals of requested vs. allocated resources.
   * `mv_daily_distribution_stats`: Daily breakdown of resource transfer volumes.
   * **Mandatory:** Create unique indexes on both MVs (`idx_mv_zone_resource_summary_pk`, `idx_mv_daily_dist_pk`) to allow `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
3. **Stored Function `fn_zone_deficit_report(p_disaster_id INT)`**:
   * Evaluates total quantity requested per resource per zone vs. total stock currently available in that zone's primary/assigned shelters.
   * Computes `required`, `available`, `deficit = GREATEST(0, required - available)`, and deficit severity.

---

## Phase 4: Wave 3 — Development Seed Data (`V008`)

Idempotent seed data inserting:
1. `people` base rows for system users and sample citizens.
2. Seed users in `app_users` with standard pre-hashed passwords (`BCrypt.hashpw("Password123!", BCrypt.gensalt(10))`):
   * `admin` $\rightarrow$ `SYSTEM_ADMIN`
   * `zone_coord` $\rightarrow$ `ZONE_COORDINATOR` (assigned to Zone 1)
   * `warehouse_mgr` $\rightarrow$ `WAREHOUSE_MANAGER`
   * `volunteer_coord` $\rightarrow$ `VOLUNTEER_COORDINATOR`
   * `analytics_user` $\rightarrow$ `ANALYTICS_USER`
3. Sample pending/fulfilled requests in `resource_requests`.
4. Sample initial distributions in `distribution_log`.
5. Sample donation in `donation_records`.

---

## Phase 5: Java Authentication, Session & Navigation Guard

### 5.1 Architecture
```text
Login Screen (userId, password)
      ↓ (Task<ServiceResult<UserSession>>)
AuthService.authenticate(username, password)
      ↓ (Prepared SQL)
AppUserDao.findByUsername(username)
      ↓ (BCrypt.checkpw)
Populate UserSession (userId, userName, role, assignedZoneId)
      ↓
Utility.changeScene("dashboard", "Dashboard")
```

### 5.2 Implementation Tasks
1. **`auth/PasswordHasher.java`**:
   * Wrapper for `org.mindrot.jbcrypt.BCrypt`.
   * Methods: `String hash(String plaintext)` and `boolean verify(String plaintext, String hash)`.
2. **`model/operations/AppUser.java`**:
   * Record/Model representing `app_users`.
3. **`dao/operations/AppUserDao.java`**:
   * Method `Optional<AppUser> findByUsername(Connection conn, String username)`.
4. **`auth/AuthService.java`**:
   * Authenticates credentials:
     * Check if user exists.
     * Check `is_active == true`.
     * Verify BCrypt hash.
     * Populate `UserSession.getInstance()`.
5. **Update `LoginController.java`**:
   * Remove `authenticatePlaceholder()`.
   * Run `AuthService.authenticate()` inside an asynchronous JavaFX Task.
   * Remove ComboBox role override (role is determined strictly from the database).
6. **Update `Utility.java`**:
   * Centralize the Screen-to-Allowed-Roles map:
     * `dashboard` $\rightarrow$ All authenticated roles.
     * `resource_request` $\rightarrow$ `SYSTEM_ADMIN`, `ZONE_COORDINATOR`, `WAREHOUSE_MANAGER`.
     * `donation` $\rightarrow$ `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`.
     * `distribution_log` $\rightarrow$ `SYSTEM_ADMIN`, `WAREHOUSE_MANAGER`, `ANALYTICS_USER`.
     * `audit_log` $\rightarrow$ `SYSTEM_ADMIN`.
     * `analytics` $\rightarrow$ `SYSTEM_ADMIN`, `ZONE_COORDINATOR`, `ANALYTICS_USER`.
     * Geography/Shelter/Inventory $\rightarrow$ Roles as specified by Dev A.
     * People/Volunteer $\rightarrow$ Roles as specified by Dev B.
   * Enforce checks before FXML loading in `changeScene()`.

---

## Phase 6: Java Operations Domain (Models, DAOs, Services)

### 6.1 Models
* **`ResourceRequest.java`**: Update with `int destinationShelterId` and `String destinationShelterName`.
* **`DistributionLog.java`**: Maintain consistency with schema.
* **`AuditLog.java`**: Maintain consistency with schema.
* **`model/operations/DonationRecord.java`**: Represents `donation_records` row and parsed items.
* **`model/operations/DonationItem.java`**: DTO for individual donation line items.
* **`model/operations/ZoneDeficit.java`**: DTO for `fn_zone_deficit_report` results.
* **`model/operations/DashboardMetrics.java`**: DTO for role-specific dashboard stat cards.

### 6.2 DAOs (`dao/operations/`)
All DAOs accept an active `Connection` and run prepared statements:
* **`ResourceRequestDao`**:
  * `create(Connection, ResourceRequest)`
  * `findById(Connection, int)`
  * `findAll(Connection, Integer zoneId, String status)`
  * `updateStatus(Connection, int requestId, String status)`
  * `callAllocateResources(Connection, int requestId, int sourceShelterId)`
* **`DonationDao`**:
  * `callProcessDonation(Connection, String donorName, int receivingShelterId, String itemsJson)`
  * `findAll(Connection)`
* **`DistributionLogDao`**:
  * `findByFilter(Connection, LocalDate from, LocalDate to, Integer logId)`
* **`AuditLogDao`**:
  * `findByFilter(Connection, String tableName, String operation, String changedBy, LocalDate from, LocalDate to, Integer logId)`
* **`AnalyticsDao`**:
  * `getZoneDeficitReport(Connection, int disasterId)`
  * `refreshMaterializedViews(Connection)`
  * `getDashboardMetrics(Connection, UserSession session)`

### 6.3 Services (`service/operations/`)
All services execute within transactions via `Database.inTransaction(session, work)`:
* **`ResourceRequestService`**:
  * `submitRequest(...)`: Validates quantity $>0$, urgency non-null.
  * `listRequests(Integer zoneFilter, String statusFilter)`: Zone-aware filtering.
  * `allocateResource(int requestId, int sourceShelterId)`: Calls allocation procedure.
* **`DonationService`**:
  * `processDonation(String donorName, int shelterId, List<DonationItem> items)`: Serializes items to JSON and executes atomic donation intake.
* **`DistributionLogService`**: Read-only date-ranged query execution.
* **`AuditLogService`**: Read-only filtered queries.
* **`AnalyticsService`**: Calls deficit reports and executes MV refreshes.

---

## Phase 7: Controller Wiring

Replace sample lists and placeholder logic in Dev C controllers with asynchronous calls:
1. **`ResourceRequestController.java`**:
   * Populate Zone, Shelter, and Resource dropdowns via existing core services.
   * For `ZONE_COORDINATOR`: lock Zone selector to `session.getAssignedZoneId()`.
   * For `WAREHOUSE_MANAGER`: enable Fulfill button with shelter selection modal/dialog.
2. **`DonationController.java`**:
   * Dynamic item table builder; on submission calls `DonationService.processDonation`.
3. **`DistributionLogController.java`**:
   * Date picker and log ID filter wired to `DistributionLogService`.
4. **`AuditLogController.java`**:
   * Table filtering and JSON diff formatting (displaying `old_values` vs `new_values` side-by-side).
5. **`AnalyticsController.java`**:
   * Tabular deficit report from `fn_zone_deficit_report`, chart population from views/MVs, and admin "Refresh Views" button.
6. **`DashboardController.java`**:
   * Connect `loadStats(role)` to `AnalyticsDao.getDashboardMetrics()`.

---

## Phase 8: Documentation Updates

As mandated by Wave 0 Decision 6:
1. Update `README.md`, `project_description_v2.md`, and all documents in `docs/` to reflect **15 physical tables** (`app_users` added).
2. Clarify that `app_users` supports authentication, RBAC, and zone tenancy.
3. Update `database/er-diagram/drras_er_diagram.drawio` to include `app_users`.

---

## Phase 9: Verification & Integration Test Suite

Build comprehensive integration tests in `src/test/java/com/groupa13/disasterrelief/operations/` and `integration/` using Testcontainers PostgreSQL 15:
* **`OperationsMigrationTest`**: Verifies migrations V001 through V008 apply cleanly from zero.
* **`AllocationConcurrencyTest`**: Spawns concurrent threads attempting to fulfill the same resource request or deplete limited inventory; verifies exactly one succeeds and stock never goes below zero.
* **`DonationProcessingTest`**: Verifies atomic UPSERT of multiple stock items and immutable donation record creation.
* **`RlsSecurityIntegrationTest`**: Verifies Zone Coordinator 1 cannot read or modify requests belonging to Zone 2; verifies Hikari connection reset leaves no zone context leakage.
* **`AuditLogTriggerTest`**: Modifies each of the 10 mutable tables and verifies corresponding `audit_log` rows with JSON old/new snapshots.
* **`EndToEndRoleFlowTest`**: Simulates complete end-to-end operational flow across all 5 roles.

---

# 4. Dev C Deliverable Checklist

- [ ] **Phase 1 (Wave 2):** `database/migrations/V005__operations_schema.sql`
- [ ] **Phase 2 (Wave 3):** `database/migrations/V006__operations_logic.sql`
- [ ] **Phase 3 (Wave 3):** `database/migrations/V007__security_views_analytics.sql`
- [ ] **Phase 4 (Wave 3):** `database/migrations/V008__development_seed.sql`
- [ ] **Phase 5 (Wave 3):** Authentication, BCrypt hasher, `app_users` DAO, `LoginController`, `Utility.changeScene` RBAC guard
- [ ] **Phase 6 (Wave 3):** Models, DAOs, and Services for Operations, Allocations, Donations, Audit, Analytics
- [ ] **Phase 7 (Wave 3):** Connect all 6 operational controllers asynchronously via `Task<ServiceResult<T>>`
- [ ] **Phase 8 (Wave 4):** Documentation updates (15 physical tables, ER diagram, README demo credentials)
- [ ] **Phase 9 (Wave 4):** Testcontainers integration tests & concurrency tests passing with `./gradlew clean test build`
