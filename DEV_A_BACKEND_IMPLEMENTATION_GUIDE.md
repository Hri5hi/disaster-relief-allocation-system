# Dev A Backend Implementation Guide

## Purpose

This document is Dev A's implementation contract for the PostgreSQL-backed Disaster Relief Resource Allocation System (DRRAS). It translates the ownership and requirements in [BACKEND_IMPLEMENTATION_PLAN.md](BACKEND_IMPLEMENTATION_PLAN.md) into an ordered, testable work plan.

Dev A delivers the backend foundation that Dev B and Dev C build on:

```text
JavaFX core controllers
        │ asynchronous service call
        ▼
core services ── transaction boundary ── Database / HikariCP ── PostgreSQL 15+
        │                                      │
        ▼                                      ▼
core DAOs (prepared SQL)                 Flyway migrations V001–V003
```

The required outcome is a clean checkout that can start PostgreSQL, migrate an empty database through V003, and run the core Testcontainers suite without relying on locally created tables or UI sample data.

## Relationship to the team waves

The **waves** are the shared team schedule from the backend implementation plan. The **phases** below are the detailed sequence for Dev A's work. They overlap as follows:

| Team wave | Dev A work in this guide | Expected result |
|---|---|---|
| **Wave 0 — agree contracts** | Baseline readiness for Phases 1, 2, and 5: lock decisions, dependency versions, configuration names, migration/type names, and test approach. | A written, unambiguous contract; **no source changes in this wave**. |
| **Wave 1 — Dev A platform baseline** | Execute Phases 1 and 2, create the shared Testcontainers support from Phase 5, and add migration/constraint tests. | Docker, Gradle dependencies, Hikari/Flyway infrastructure, V001–V003, and a clean-database test gate are merged. |
| **Wave 2 — parallel domain work** | Execute Phases 3 and 4; complete inventory test coverage and the listener boundary in Phase 5. | Core persistence and the five database-backed controllers are ready while Dev B/C work in their owned areas. |
| **Wave 3 — business logic and security** | Review Dev C's inventory/function/notification integration; fix only Dev A-owned code when required. | Inventory APIs and listener boundary remain compatible with operations logic. |
| **Wave 4 — end-to-end hardening** | Run the shared suite and resolve defects in Dev A-owned files. | Dev A's core layer supports the final clean-container build. |

### Wave 0 — completed baseline readiness checklist (no code changes)

The following planning items were completed before Wave 1. They define the platform baseline; Wave 0 itself made no Gradle, migration, or controller changes.

| Related phase | Wave 0 decision / preparation | Must be agreed before Wave 1 |
|---|---|---|
| **Phase 1: runtime baseline** | PostgreSQL 15 image, local database name, non-production database user, host/port, example-property names, Hikari defaults, and selected versions of JDBC/Hikari/Flyway/BCrypt/JUnit/Testcontainers. | The exact configuration contract and a secret-free `.env.example` format. |
| **Phase 2: migrations V001–V003** | Migration naming convention, `coordinates` type shape, severity/status type strategy, integer-ID/display-ID rule, shelter address decision, delete behavior, and the zone/shelter circular-FK creation order. | The V001–V003 schema contract and named constraints/indexes to test. |
| **Phase 3: core APIs** | Java package layout; model names; DAO/service naming; `ServiceResult<T>` fields; `Database.inTransaction`/`SqlWork` signature; SQL-error message vocabulary; stable lookup methods required by Dev B/C. | A public Java contract that other developers can compile against after the baseline merges. |
| **Phase 5: test baseline** | PostgreSQL Testcontainers version, test lifecycle/isolation approach, migration runner location (`filesystem:database/migrations`), and required success/failure cases. | A repeatable fresh-database merge gate. |

### Agreed Wave 0 contract (2026-10-01)

The team accepted the recommended examples. This record is Dev A's working contract for Wave 1; it contains no production credentials.

| Area | Agreed contract |
|---|---|
| Local PostgreSQL and configuration | PostgreSQL 15 at `localhost:5432`, database `dras`, and example non-production application user `dras_app`. Keep passwords only in ignored `.env` and `config/application.properties` files. Use `db.jdbcUrl`, `db.username`, `db.password`, `db.pool.maximumSize`, `db.pool.connectionTimeoutMs`, and `flyway.locations`; the example URL is `jdbc:postgresql://localhost:5432/dras`, pool size is 10, connection timeout is 10,000 ms, and migrations load from `filesystem:database/migrations`. |
| Branches and dependency baseline | Dev A, Dev B, and Dev C use `feature/dev-a`, `feature/dev-b`, and `feature/dev-c`. Wave 1 pins PostgreSQL JDBC 42.7.13, HikariCP 7.1.0, Flyway 13.8.0, jBCrypt 0.4, JUnit Jupiter 5.12.2, and Testcontainers 2.0.5 with Java 21. |
| Migration and test baseline | Use immutable, zero-padded Flyway migrations with descriptive snake_case names, beginning `V001__extensions_and_types.sql` through `V003__core_constraints_indexes.sql` for Dev A. Testcontainers runs PostgreSQL 15 and applies migrations from the explicit filesystem location to a fresh database; a correction is always a new migration. |
| Shared Java API | Use package root `com.groupa13.disasterrelief`, with core types under `model.core`, `dao.core`, and `service.core`. Preserve `ServiceResult<T>(boolean success, T value, String message)` and `Database.inTransaction(UserSession, SqlWork<T>)` as the shared contracts. |
| IDs and core data | Database primary/foreign keys are integers; controllers format display-only values such as `D1001`. V005 includes `destination_shelter_id`, so every request identifies the exact shelter that receives an allocation. |
| User-visible errors | Return concise safe messages such as “Insufficient stock at source shelter” and “Invalid input”. Never expose SQL, SQLState values, credentials, or stack traces in the UI. |
| Cross-team downstream contracts | V008 will seed the development-only accounts `admin`, `zone_coord`, `warehouse_mgr`, `shelter_mgr`, and `logistics_officer`, one per role, with BCrypt hashes. `fn_allocate_resources` returns fulfilled-request, distribution-log, source/destination shelter, resource, and allocated-quantity values; `fn_process_donation` returns donation ID plus affected inventory lines; `fn_zone_deficit_report` returns zone/resource/requested/available/deficit values. Their SQL column names are fixed when introduced. |
| Operations conventions affecting Dev A | Development data remains until the local volume is reset; maintenance creates current and next monthly distribution partitions. Audit includes disasters, zones, shelters, resources, inventory, volunteers, affected persons, resource requests, volunteer shifts, and donations; audit rows are immutable. |

The canonical full-team record is also maintained in [BACKEND_IMPLEMENTATION_PLAN.md](BACKEND_IMPLEMENTATION_PLAN.md#9-ratified-wave-0-decisions-recorded-before-wave-1).

## Scope, ownership, and boundaries

### Files owned by Dev A

```text
build.gradle
docker-compose.yml
.env.example
config/application.properties.example
database/migrations/V001__extensions_and_types.sql
database/migrations/V002__core_schema.sql
database/migrations/V003__core_constraints_indexes.sql
src/main/java/com/groupa13/disasterrelief/db/**
src/main/java/com/groupa13/disasterrelief/model/core/**
src/main/java/com/groupa13/disasterrelief/dao/core/**
src/main/java/com/groupa13/disasterrelief/service/core/**
src/main/java/com/groupa13/disasterrelief/controller/DisasterController.java
src/main/java/com/groupa13/disasterrelief/controller/ZoneController.java
src/main/java/com/groupa13/disasterrelief/controller/ShelterController.java
src/main/java/com/groupa13/disasterrelief/controller/ResourceController.java
src/main/java/com/groupa13/disasterrelief/controller/InventoryController.java
src/test/java/com/groupa13/disasterrelief/support/**
src/test/java/com/groupa13/disasterrelief/core/**
```

### Explicitly out of scope

Do not implement or edit Dev B's people/volunteer/shift work or Dev C's authentication, requests, allocation, donations, audit, analytics, RLS, and dashboard work. In particular:

- Do not create `people`, `app_users`, `resource_requests`, `distribution_log`, or donation/audit tables.
- Do not implement occupancy or low-stock database triggers. Dev B owns occupancy in V004; Dev C owns operational triggers and `LISTEN/NOTIFY` production logic in V006.
- Do not add authorization decisions to the five core controllers. They will consume Dev C's authenticated `UserSession` later through service calls.
- Do not update a migration owned by another developer. A schema correction after a migration is merged is a new, numbered migration owned by the developer responsible for the new dependency.

### Inputs and downstream contracts

| Producer / consumer | Contract Dev A provides or uses |
|---|---|
| Dev A → Dev B | V001–V003; `Database` transaction API; lookups for zones and shelters; integer IDs in Java contracts. |
| Dev A → Dev C | Core DAO/service lookups, atomic inventory UPSERT API, low-stock listener abstraction, and a transaction helper compatible with `UserSession`. |
| Dev C → Dev A | `UserSession` is the only source of role and assigned-zone context. Dev A must not trust values selected in the UI as authorization data. |
| PostgreSQL → all services | Foreign keys, checks, unique constraints, and later triggers/functions are authoritative. Java validates for usability but never replaces database enforcement. |

## Ratified details Dev A must not change silently

These decisions complete the Dev A portion of the Wave 0 contract:

1. Use integer database primary keys; only format display IDs such as `D1001` in the UI.
2. Use database `dras`, example application user `dras_app`, `localhost:5432`, and the five seed usernames recorded above. Never commit a real password.
3. Use `coordinates(latitude DOUBLE PRECISION, longitude DOUBLE PRECISION)`; severity values `low`, `medium`, `high`, `critical`; disaster statuses `active`, `monitoring`, `resolved`, `closed`; and shelter statuses `operational`, `full`, `closed`.
4. Store a required human-readable `address VARCHAR(255)` for every shelter and allow `location coordinates` to be null so offline registration can succeed. The UI displays the address; coordinates are only for geocoding, maps, and proximity work.
5. Apply `ON DELETE RESTRICT` to core references. A resource category can be deleted only when it has no children and no resources.
6. Enforce `0 <= current_occupancy <= capacity`; Dev B's occupancy trigger must reject a reassignment that would exceed capacity.
7. Create `zones` with a nullable `primary_shelter_id`, create `shelters`, and then add the circular foreign key. V005 separately adds required `resource_requests.destination_shelter_id` for the selected allocation destination.

## Deliverables and implementation order

### Phase 1 — Runtime and local database baseline

#### 1. Gradle

Extend `build.gradle` while keeping the JavaFX application configuration intact.

Required production dependencies:

- PostgreSQL JDBC driver (`org.postgresql:postgresql`)
- HikariCP (`com.zaxxer:HikariCP`)
- Flyway PostgreSQL support (`org.flywaydb:flyway-core` and the PostgreSQL database module appropriate to the selected Flyway version)
- BCrypt (`org.mindrot:jbcrypt` or the team-approved equivalent; Dev C uses it for login)

Required test dependencies:

- JUnit Jupiter 5 with `useJUnitPlatform()`
- Testcontainers JUnit Jupiter and PostgreSQL modules

Required build behavior:

- Compile and test with Java 21.
- Make `test` discover JUnit 5 tests.
- Keep secrets out of Gradle properties and source files.
- Document the canonical checks: `./gradlew test` during development and `./gradlew clean test build` before merge.

#### 2. Docker Compose and configuration

Create a PostgreSQL 15 service in `docker-compose.yml` with:

- a named persistent volume;
- a health check using `pg_isready`;
- port and database/user/password values read from `.env` with safe development defaults or required-variable syntax;
- no production credentials in committed files.

Add `.env.example` with placeholder/sample development values and ensure `.env` remains ignored. Add `config/application.properties.example` with the JDBC URL, username, password placeholder, pool sizing/timeouts, and the explicit Flyway migration location:

```properties
db.jdbcUrl=jdbc:postgresql://localhost:5432/dras
db.username=dras_app
db.password=replace-with-local-secret
db.pool.maximumSize=10
db.pool.connectionTimeoutMs=10000
flyway.locations=filesystem:database/migrations
```

The exact property names are Dev A's public configuration contract. Keep the example secret-free and make the real local properties file ignored.

#### 3. Database infrastructure package

Implement the shared `db` package before adding DAOs. It must expose these stable contracts:

```java
// Callers acquire and close the connection with try-with-resources.
DataSource Database.dataSource();

// Services use this for role-bound or multi-query work.
<T> T Database.inTransaction(UserSession session, SqlWork<T> work);

record ServiceResult<T>(boolean success, T value, String message) {}
```

Recommended package responsibilities:

| Class / interface | Responsibility |
|---|---|
| `Database` | Load config once, create/close the Hikari datasource, expose `dataSource()` and transaction execution. |
| `DatabaseConfig` | Parse and validate local configuration; identify missing/invalid values in an actionable error. |
| `SqlWork<T>` | Functional interface accepting the transaction-scoped `Connection`. |
| `FlywayMigrator` | Configure Flyway with `filesystem:database/migrations` and migrate/validate a supplied datasource. |
| `DatabaseException` or error mapper | Preserve SQLState and constraint name in logs; turn expected integrity errors into safe service results. |
| `LowStockNotificationListener` interface | Define Dev A's listener boundary without implementing Dev C's trigger or alert UI. |

Rules for `Database.inTransaction`:

1. Borrow one connection, save its current auto-commit state, and set `autoCommit(false)`.
2. If a non-null `UserSession` has an assigned zone, call `SELECT set_config('app.current_zone_id', ?, true)` on this same connection before application SQL. The `true` argument makes it transaction-local.
3. Run the work, commit exactly once on success, and roll back on every exception.
4. Restore connection state and close the connection in `finally` so Hikari can safely reuse it.
5. Do not allow a connection to escape the DAO/service call.

For the baseline, `UserSession` may not yet exist in the branch. Either compile against the existing utility contract without changing Dev C-owned files, or provide a narrowly named overload for unscoped transactions and add the session-aware overload when Dev C's API is available by agreed interface. Coordinate the signature before Dev C begins auth work.

Fail fast if configuration is absent, the datasource cannot connect, or Flyway validation/migration fails. The user-facing startup message should say what to fix; detailed exception data goes to logs, not JavaFX dialogs.

## Phase 2 — Flyway migrations V001–V003

Migrations live only in `database/migrations/`, run in lexical order, and are immutable after merge. Each must work on an empty PostgreSQL 15 database after its predecessors. Do not place executable SQL in `database/procedures/` or `database/seed/`.

### V001 — extensions and shared types

File: `V001__extensions_and_types.sql`

Required content:

- `CREATE EXTENSION IF NOT EXISTS btree_gist;` for Dev B's later GiST exclusion constraint.
- `CREATE EXTENSION IF NOT EXISTS pgcrypto;` for Dev C's later password/security work.
- Define the `coordinates` composite type used by `shelters.location`, containing `latitude DOUBLE PRECISION` and `longitude DOUBLE PRECISION`.
- Define shared enum/domain/check-support types agreed during Wave 0. They must cover the documented severity and disaster/shelter status vocabularies without changing UI wording unexpectedly.
- Use migration-safe creation patterns where PostgreSQL permits them. Avoid hiding mistakes with broad exception handlers.

### V002 — the six core tables

File: `V002__core_schema.sql`

Create the tables in an order that handles the `zones.primary_shelter_id` ↔ `shelters` dependency:

1. `disasters`
2. `zones` without its `primary_shelter_id` foreign key (the nullable column may be present)
3. `shelters`
4. `ALTER TABLE zones` to add the `primary_shelter_id` foreign key
5. `resource_categories`
6. `resources`
7. `shelter_inventory`

Use integer identity/serial-compatible primary keys consistently; Java treats every ID as `int`.

| Table | Required columns and behavior |
|---|---|
| `disasters` | `disaster_id` PK; nonblank `name`; optional `type`; severity; optional `daterange active_period`; status; `created_at TIMESTAMPTZ DEFAULT now()`. |
| `zones` | `zone_id` PK; required FK `disaster_id`; nonblank `name`; severity; nullable FK `primary_shelter_id`; optional coordinator contact. A zone must always belong to a disaster. |
| `shelters` | `shelter_id` PK; nonblank name; `location coordinates`; positive capacity; derived `current_occupancy INTEGER NOT NULL DEFAULT 0`; status. Occupancy is read-only to Java and later maintained by Dev B's trigger. |
| `resource_categories` | `category_id` PK; nonblank name; nullable self-FK `parent_id`. It represents an arbitrary-depth tree, not a fixed three-level hierarchy. |
| `resources` | `resource_id` PK; nonblank name; required FK `category_id`; optional nonblank `unit`. It is the catalog, not stock. |
| `shelter_inventory` | Composite PK `(shelter_id, resource_id)`; FKs to shelter and resource; nonnegative `quantity`; nonnegative `reorder_level` defaulting to 10; `last_updated TIMESTAMPTZ DEFAULT now()`. |

Use `ON DELETE` behavior deliberately. At minimum, do not allow deletion of a disaster with zones, a shelter/resource with inventory rows, or a category with dependent categories/resources by accident. The schema should reject deletion and let the service display a safe “record is still in use” message.

### V003 — constraints and indexes

File: `V003__core_constraints_indexes.sql`

Add named constraints and indexes so application error mapping and tests can identify failures. At a minimum:

- Severity/status vocabulary enforcement (unless it is fully enforced by V001 types).
- `capacity > 0`, `current_occupancy >= 0`, and—if approved—`current_occupancy <= capacity`.
- `quantity >= 0` and `reorder_level >= 0`.
- Required/meaningful text checks using `btrim(column) <> ''` where appropriate.
- A unique zone name within its disaster: `(disaster_id, name)`.
- The composite primary key/unique inventory identity `(shelter_id, resource_id)`.
- Indexes supporting FKs and primary screen queries: zones by disaster, resources by category, inventory by resource and/or shelter, and the most common active/operational filters.
- An index on `resource_categories(parent_id)` for recursive-tree traversal.
- A partial index only where the final query predicate warrants it; do not add a speculative index. A good candidate is an operational-shelter query if the core service actually filters on `status = 'operational'`.

All SQL accessed from Java must remain parameterized. Table/column names are static SQL; values are `?` bindings. No DAO may concatenate user input into SQL.

## Phase 3 — Core Java model, DAO, and service layers

### Model package

Create immutable `model.core` records/classes for the six aggregates. Models should use:

- `int` for persisted IDs;
- `OffsetDateTime` or `Instant` for `TIMESTAMPTZ` values;
- `LocalDate` boundaries or a dedicated value object for the `daterange` active period;
- nullable boxed values or `Optional` only at external boundaries;
- a `Coordinates` value object for latitude/longitude rather than passing formatted display strings;
- enums matching the agreed database values for severity and statuses.

Suggested models: `Disaster`, `Zone`, `Shelter`, `Coordinates`, `ResourceCategory`, `Resource`, `ShelterInventory`, and compact list/tree projection DTOs. Do not reuse the controllers' current nested `*Row` sample classes as persistence models.

### DAO rules

Create one focused DAO per aggregate or closely related pair:

```text
dao/core/
  DisasterDao.java
  ZoneDao.java
  ShelterDao.java
  ResourceCategoryDao.java
  ResourceDao.java
  InventoryDao.java
```

Every DAO:

- accepts a datasource/transaction connection by the established package convention;
- uses `PreparedStatement`, generated-key handling, and try-with-resources;
- maps database rows to core models only;
- contains no JavaFX types, alert code, or authorization decisions;
- never opens a `DriverManager` connection;
- does not commit, roll back, or retain a connection.

JDBC specifics:

- Bind `daterange` using a validated parameter/`PGobject` rather than SQL concatenation.
- Insert a composite location with a static cast such as `ROW(?, ?)::coordinates` and bind the numeric values separately.
- Read PostgreSQL composites reliably (for example, with a small parser/value mapping boundary); do not expose raw `PGobject` outside the DAO.
- Use `RETURNING` for generated identifiers and current database values when practical.
- Map SQLState class `23` to a safe validation/not-found/in-use result at the service boundary, while logging the SQLState and named constraint.

### Service rules and operations

Services own validation, transaction boundaries, error translation, and orchestration. They return `ServiceResult<T>` (or the team-agreed equivalent) so controllers show a clear message without examining `SQLException`.

Implement in vertical slices, in this order:

1. **Disaster and zone service** — create, list, find, update status/details, delete only when legal; list zones for a disaster; assign/clear a primary shelter only after validating both IDs exist.
2. **Shelter service** — register, list, find, update editable fields, delete only when unreferenced; expose occupancy as read-only. Validate finite latitude/longitude and sensible geographic bounds before DAO execution.
3. **Category and resource service** — create/list/tree/read/update/delete categories; reject category deletion with children or resources; create/list/update/delete catalog resources. Implement category tree retrieval in one recursive CTE, not N+1 DAO calls.
4. **Inventory service** — list inventory with shelter/resource names, fetch a row, list threshold-breached rows, and atomically insert/update a row. Validate nonnegative input before calling PostgreSQL; PostgreSQL remains the final guard.

For an inventory replacement/update, use a single upsert statement whose intended semantics are explicit:

```sql
INSERT INTO shelter_inventory (shelter_id, resource_id, quantity, reorder_level)
VALUES (?, ?, ?, ?)
ON CONFLICT (shelter_id, resource_id)
DO UPDATE SET quantity = EXCLUDED.quantity,
              reorder_level = EXCLUDED.reorder_level,
              last_updated = now()
RETURNING shelter_id, resource_id, quantity, reorder_level, last_updated;
```

Do not use a read-then-insert/update pattern: it races. Dev C's donation function will own *incremental* inventory UPSERTs; agree an explicit service method name so it cannot accidentally call a replacement-quantity operation.

### Category traversal contract

The resource UI needs a complete hierarchy. The category DAO should provide an ordered flat result with `category_id`, `parent_id`, `name`, depth/path, or a tree DTO produced from one query. A recursive CTE may follow this shape:

```sql
WITH RECURSIVE category_tree AS (
  SELECT category_id, parent_id, name, 0 AS depth,
         ARRAY[category_id] AS path
  FROM resource_categories
  WHERE parent_id IS NULL
  UNION ALL
  SELECT c.category_id, c.parent_id, c.name, t.depth + 1,
         t.path || c.category_id
  FROM resource_categories c
  JOIN category_tree t ON c.parent_id = t.category_id
)
SELECT category_id, parent_id, name, depth
FROM category_tree
ORDER BY path;
```

The service must prevent a category being moved under itself or one of its descendants. This must be checked in the service/DAO transaction or enforced through an agreed database constraint/function; a simple self-FK does not prevent longer cycles.

## Phase 4 — Controller replacement

Replace only the TODO/sample-data paths in the five owned controllers after their services are tested. Controllers remain presentation adapters:

- They validate required fields, integer fields, date boundaries, and coordinates before a service call.
- Each user action creates one JavaFX `Task` that calls exactly one service method.
- `setOnSucceeded` updates the UI on the JavaFX thread; `setOnFailed` displays a safe generic failure and logs the exception.
- Controllers never contain SQL, call a DAO directly, borrow JDBC connections, or print stack traces to users.
- IDs stored in ComboBox values must remain integer IDs/value objects, not parsed display labels such as `"S2001 — Shelter"`.

| Controller | Replace sample behavior with |
|---|---|
| `DisasterController` | Database list, create/edit/delete (where permitted), date-range validation, and refresh after a successful service result. |
| `ZoneController` | Database disaster/shelter selection lists, zone CRUD, and primary-shelter assignment through `ZoneService`. |
| `ShelterController` | Database list and CRUD; validate coordinates/capacity; display `current_occupancy` but never allow it to be edited or sent to the service. |
| `ResourceController` | Database category tree and category CRUD, catalog resource CRUD, and refreshable category ComboBox/tree driven by stable category IDs. |
| `InventoryController` | Database shelter/resource selectors, inventory list/upsert, low-stock-only filtering based on `quantity < reorder_level`, and notification-driven targeted refresh. |

Use `ServiceResult.message()` for expected validation messages. Do not expose raw SQL, credentials, SQLState, or stack traces in labels/alerts.

## Low-stock notification boundary

Dev A owns the Java API and `InventoryController` integration point; Dev C owns `trg_low_stock_alert` and the final alert adapter. Design the boundary now so the later work is non-breaking.

Recommended interface:

```java
public interface LowStockNotificationListener extends AutoCloseable {
    void start(Consumer<LowStockAlert> onAlert);
    @Override void close();
}

public record LowStockAlert(int shelterId, int resourceId, int quantity,
                            int reorderLevel) {}
```

Implementation constraints when the PostgreSQL listener is enabled:

- It uses one dedicated, long-lived, **unpooled** PostgreSQL connection, never a Hikari transaction connection.
- It issues `LISTEN low_stock_alert`, reads notifications in a background worker, parses a bounded JSON payload, and reconnects with bounded backoff.
- It never performs JavaFX scene updates itself. It invokes the callback, and the controller schedules the UI refresh on the JavaFX thread.
- A notification is a refresh hint, not the source of truth. Re-read the affected inventory row before displaying it.
- It has a clean shutdown hook/`close()` to avoid leaving listener threads alive in tests or application exit.

Until Dev C delivers the trigger and payload contract, keep the listener mockable and test it with injected notifications or a fake implementation. Do not fabricate a trigger in a Dev A migration.

## Phase 5 — Testcontainers test suite

### Shared support

Create reusable test support under `src/test/java/com/groupa13/disasterrelief/support/`:

- a PostgreSQL 15 `PostgreSQLContainer` lifecycle base/helper;
- a method that configures and runs Flyway against the container's JDBC URL;
- isolated setup that applies migrations to a fresh database/schema for each suite as needed;
- helpers for test data that use DAOs/services or prepared SQL, never an external local database.

Do not rely on Docker Compose for automated tests. Testcontainers is the merge gate because it proves migrations work from zero.

### Required Dev A tests

| Area | Success case | Failure / edge case |
|---|---|---|
| Migration baseline | Fresh PostgreSQL 15 migrates cleanly through V003 and contains required extensions/types/tables. | A migration/configuration failure is surfaced clearly; no manually pre-created schema is used. |
| Disaster and zones | Create disaster, create zone, list zones by disaster. | Missing parent disaster, invalid severity/status, blank name, duplicate zone name in one disaster are rejected. |
| Shelter | Store/read valid coordinates and capacity. | Zero/negative capacity, invalid occupancy, and deletion while referenced are rejected. |
| Circular relationship | Create zone without primary shelter, create shelter, then assign it. | Assigning a nonexistent shelter or zone fails through an FK/not-found result. |
| Categories | Create multilevel tree and retrieve it in correct parent-before-child order. | Self-parenting, descendant-cycle attempts, and deletion of a non-leaf/in-use category fail safely. |
| Resources | Add resource under a category and list it with category data. | Missing category and invalid required values fail. |
| Inventory | Upsert one `(shelter_id, resource_id)` row and verify exactly one row/current timestamp. | Negative quantity/reorder level and missing FK rows fail. |
| Inventory concurrency | Concurrent writes preserve one composite-key row and a valid nonnegative quantity. | Verify no read-then-write race or duplicate row is possible. |
| Threshold filtering | Query/filter only rows where `quantity < reorder_level`. | Equal-to-threshold rows are not presented as low stock, matching the plan's “below” rule. |

Run at least:

```bash
./gradlew test
./gradlew clean test build
```

Also verify the developer workflow manually from a clean state:

```bash
cp .env.example .env
cp config/application.properties.example config/application.properties
docker compose up -d
```

Use local non-secret values only. Do not commit either copied local configuration file.

## Pull requests and merge gates

Split the work so Dev B and Dev C receive an early, stable baseline:

1. **PR A1 — Platform baseline:** Gradle dependencies, Docker Compose, examples, `.gitignore` updates, database/Flyway infrastructure, Testcontainers support, V001–V003, and migration/constraint tests.
2. **PR A2 — Core persistence:** Core models, DAO/service vertical slices, and DAO/service tests.
3. **PR A3 — Core UI integration:** The five controller conversions and inventory notification abstraction/tests.

If the team prefers a single PR, preserve the same commit order so the baseline can be reviewed and bisected. Before opening each PR:

- confirm only Dev A-owned files changed (other than explicitly approved documentation/config additions);
- run the full Gradle test/build commands;
- rerun migrations in a fresh PostgreSQL 15 container;
- state migration names, service operations, and success/failure tests in the PR description;
- verify there are no credentials, generated `bin/` files, build artifacts, or local config files staged.

## Dev A definition of done

Dev A's work is complete when all statements below are true:

- PostgreSQL 15 starts healthy with Docker Compose using documented local configuration.
- Flyway applies V001, V002, and V003 in order to an empty database.
- The six core tables, shared extensions/types, named integrity constraints, foreign keys, and query-supporting indexes exist.
- `Database` supplies a pooled datasource, safe transaction lifecycle, fail-fast config errors, and a session-aware transaction contract without pool-wide zone leakage.
- Core models, DAOs, and services use prepared JDBC and keep transactions/services separate from controllers.
- The five owned controllers no longer present sample rows as real data and use asynchronous service calls.
- Inventory replacement UPSERT is atomic; core code never permits a negative stock quantity; threshold filtering uses `quantity < reorder_level`.
- The low-stock listener API is mockable, uses no pooled listener connection, and is ready for Dev C's trigger/alert payload.
- Fresh-container migration, constraint, category-tree, inventory, and concurrency tests pass with `./gradlew clean test build`.

At that point Dev B can safely begin V004 and Dev C can begin V005 against Dev A's merged database contract.
