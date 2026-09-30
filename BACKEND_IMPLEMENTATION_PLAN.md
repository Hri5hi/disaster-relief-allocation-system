# Backend Implementation Plan — Disaster Relief Resource Allocation System

**Status:** Ready for implementation  
**Scope:** PostgreSQL database, JDBC/Hikari persistence layer, authentication, service/DAO integration, and backend tests. The existing FXML and CSS remain the presentation layer.  
**Source contracts:** `project_description_v2.md`, `docs/3.3_relational_schema.md`, and `UI_IMPLEMENTATION_PLAN.md`.

---

## 1. Outcome and non-negotiable architecture

The current application is a JavaFX prototype: its controllers use sample lists, authentication accepts any non-empty password, and neither a database nor persistence layer exists. This plan turns it into the PostgreSQL-first system described in the project documents.

The completed architecture is:

```text
FXML controller -> service -> DAO (prepared JDBC) -> PostgreSQL 15+
                     |                              |
                     +-- transaction/session scope   +-- constraints, functions,
                                                        triggers, RLS, views
```

Rules:

- Controllers never contain SQL or call `DriverManager`.
- DAOs execute parameterized SQL only; services define transaction boundaries and translate domain errors for controllers.
- PostgreSQL owns the critical business rules: allocation, stock integrity, priority calculation, occupancy, audit history, shift conflicts, RLS, and low-stock notification.
- Flyway migrations are immutable once merged. A correction is always a new migration; never edit an applied migration.
- Every database change has a Testcontainers integration test before it is merged.

### Required implementation decision: application identity

Add an `app_users` support table, linked to `people`, with `username`, a BCrypt password hash, role, optional `assigned_zone_id`, and active status. This is required for real authentication and is already anticipated by `LoginController`'s “credentials table (or dedicated `app_users` table)” comment. It makes the physical database 15 tables: 14 operational/audit tables in the current schema document plus one application-identity table. Dev C updates the schema count in the README and project documents in the same pull request.

## 2. What remains to be built

| Area | Current state | Required finished state |
|---|---|---|
| Runtime/configuration | No database configuration; `docker-compose.yml` is empty | Local PostgreSQL service, secret-free example configuration, PostgreSQL JDBC, HikariCP, Flyway, and test dependencies |
| Schema | ER diagram and prose only | Versioned, repeatable PostgreSQL 15+ migrations for all tables, indexes, extensions, checks, foreign keys, partitions, and seed data |
| Domain persistence | Empty `dao/` and `service/` packages | DAO/service modules for core data, people, and operations; all calls use prepared statements |
| Authentication/security | Placeholder accepts every non-empty credential | BCrypt verification, active-user checks, role/zone session context, RLS policies, controller navigation enforcement |
| Core operations | UI-local CRUD/sample data | Real database CRUD for disasters, zones, shelters, catalog, inventory, volunteers, shifts, and affected people |
| Allocation workflow | No allocation | Atomic `fn_allocate_resources(request_id, source_shelter_id)` with locking, stock update, request state transition, and distribution log |
| Donations/alerts | UI item builder only | JSONB donation record, atomic inventory UPSERT, low-stock trigger, and a managed `LISTEN/NOTIFY` listener |
| Analytics/audit | Sample charts and rows | Views, materialized views, deficit function, audit trigger, read-only audit/analytics DAOs |
| Verification | No test sources | Migration, DAO/service, security, concurrency, and end-to-end integration tests |

## 3. Migration map and database design

Migrations live in `database/migrations/` and are run by Flyway in lexical version order (`filesystem:database/migrations` is configured explicitly because this is not Flyway's default classpath location). The database uses PostgreSQL 15+. The current `database/procedures/` and `database/seed/` directories contain only `.gitkeep` placeholders; do not split executable SQL into them—keep it versioned in the migration that introduces it.

| Migration | Owner | Contents |
|---|---|---|
| `V001__extensions_and_types.sql` | Dev A | `btree_gist`, `pgcrypto`, shared enums/domains, and migration-safe helper definitions |
| `V002__core_schema.sql` | Dev A | `disasters`, `zones`, `shelters`, `resource_categories`, `resources`, `shelter_inventory`; resolve the zone/primary-shelter circular FK after both tables exist |
| `V003__core_constraints_indexes.sql` | Dev A | Core checks, foreign keys, composite/partial indexes, recursive category indexes, and inventory uniqueness |
| `V004__people_schema.sql` | Dev B | `people`, `volunteers`, `affected_persons`, `volunteer_shifts`; `TEXT[]`, `JSONB`, `tsrange`, GiST exclusion constraint, and shelter-occupancy trigger |
| `V005__operations_schema.sql` | Dev C | `resource_requests`, monthly range-partitioned `distribution_log`, `donation_records`, and `audit_log`; generated priority score and operational indexes |
| `V006__operations_logic.sql` | Dev C | Allocation, donation, volunteer registration/shift functions; partition-creation helper; low-stock, disaster-close, and audit triggers |
| `V007__security_views_analytics.sql` | Dev C | `app_users`, database roles/grants, RLS policies, regular views, materialized views, deficit report function, and concurrent-refresh indexes |
| `V008__development_seed.sql` | Dev C | Idempotent demonstration users and coherent development data that exercises every role and workflow |

### Required database behavior

- `resource_requests.priority_score` is generated by PostgreSQL from urgency and requested quantity; Java never writes it.
- `volunteer_shifts` uses `EXCLUDE USING gist (volunteer_id WITH =, duty_period WITH &&)` so overlapping shifts cannot be inserted, even concurrently.
- `affected_persons` insert, delete, or shelter reassignment recalculates `shelters.current_occupancy` in `trg_auto_update_shelter_occupancy`.
- Resolving or closing a disaster invokes `trg_cascade_disaster_close` to cancel its pending requests.
- `fn_allocate_resources` obtains an advisory lock scoped to the resource/request, locks the request and source inventory row, validates state and quantity, decrements inventory, fulfills the request, inserts an append-only distribution log, and returns one result object. Any failure rolls back every change.
- `fn_process_donation` validates the donation JSONB array and performs one transaction: insert immutable `donation_records`, UPSERT each inventory line, then return the donation ID and affected stock lines.
- `distribution_log` is range partitioned by `distributed_at`; create the active and next-month partitions during migration/maintenance and retain a default partition only as a safety net.
- `trg_low_stock_alert` emits a JSON payload on `low_stock_alert` only when stock crosses from at/above reorder level to below it. This avoids alert floods on every subsequent update.
- `trg_audit_log` captures `INSERT`, `UPDATE`, and `DELETE` on all mutable operational tables using `to_jsonb(OLD)`/`to_jsonb(NEW)`. Application roles receive no `INSERT`, `UPDATE`, or `DELETE` grant on `audit_log`.
- Views: `v_shelter_dashboard`, `v_pending_requests_ranked`, `v_volunteer_availability`, and `v_active_disasters`. Materialized views: `mv_zone_resource_summary` and `mv_daily_distribution_stats`, each with a unique index required for `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
- RLS applies at minimum to zones, resource requests, affected persons, and their zone-scoped reads. A coordinator connection sets `app.current_zone_id` with `set_config(..., true)` inside the same transaction; never use a pool-wide session variable.

## 4. Shared Java contracts

Dev A owns the shared infrastructure files below. All developers use these contracts unchanged once the baseline pull request is merged.

```java
// Connection lifecycle: callers use try-with-resources; no connection escapes a DAO call.
DataSource Database.dataSource();

// Transaction/session scope, used by services for every role-bound operation.
<T> T Database.inTransaction(UserSession session, SqlWork<T> work);

// Service results: controllers display these messages; they do not inspect SQLState.
record ServiceResult<T>(boolean success, T value, String message) {}
```

Implementation conventions:

- Store timestamps as `OffsetDateTime`/`Instant`, identifiers as `int`, and nullable database fields as boxed types/`Optional` at boundaries.
- Bind `TEXT[]` with `Connection.createArrayOf`; bind JSONB through `PGobject`; pass `tsrange` as a validated `PGobject`/parameter rather than concatenating SQL.
- Map SQLState class `23` to user-safe validation messages, preserve SQLState and constraint name in logs, and never show a stack trace in the UI.
- Each `Task` in a controller calls exactly one service method and uses `setOnSucceeded`/`setOnFailed` on the JavaFX thread.
- The dedicated notification listener has its own unpooled/long-lived PostgreSQL connection. It reconnects with bounded backoff and never borrows a Hikari transaction connection.

## 5. Strict ownership split

No developer edits another developer’s owned files. A shared file is assigned to exactly one owner; other developers request a small follow-up change or supply an interface-level review comment.

### Dev A — platform, geography, shelter, catalog, and inventory

**Owns**

```text
build.gradle
docker-compose.yml
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

**Deliverables**

1. Add PostgreSQL JDBC, HikariCP, Flyway, BCrypt, JUnit 5, and Testcontainers dependencies; configure Java 21 test execution.
2. Supply Docker PostgreSQL 15 with health check, named volume, non-secret sample environment variables, and a `.env.example`; actual credentials remain ignored.
3. Build the pooled `Database`/transaction infrastructure and Flyway startup/test migration utility. It must fail fast with a useful configuration error.
4. Implement the six core tables and their DAOs/services: disasters, zones, shelters, hierarchical resource categories, resources, and shelter inventory.
5. Replace sample data in the five owned controllers with asynchronous service calls. Enforce numeric/date validation before DAO calls; leave occupancy read-only.
6. Implement inventory threshold filtering and the notification-listener API consumed by Dev C’s dashboard/alert adapter.

**Acceptance tests owned by Dev A**

- Fresh PostgreSQL container migrates from zero to V003.
- Core FK/check/index failures are rejected; category hierarchy traversal works.
- Inventory UPSERT is atomic and never makes quantity negative.
- Disaster closure behavior can be tested later after Dev C’s operation migrations are installed.

### Dev B — people, affected-person operations, volunteers, and shifts

**Owns**

```text
database/migrations/V004__people_schema.sql
src/main/java/com/groupa13/disasterrelief/model/AffectedPerson.java
src/main/java/com/groupa13/disasterrelief/model/Volunteer.java
src/main/java/com/groupa13/disasterrelief/model/VolunteerShift.java
src/main/java/com/groupa13/disasterrelief/dao/people/**
src/main/java/com/groupa13/disasterrelief/service/people/**
src/main/java/com/groupa13/disasterrelief/controller/AffectedPersonController.java
src/main/java/com/groupa13/disasterrelief/controller/VolunteerController.java
src/main/java/com/groupa13/disasterrelief/controller/VolunteerShiftController.java
src/test/java/com/groupa13/disasterrelief/people/**
```

**Deliverables**

1. Create `people` as the superclass and `volunteers`/`affected_persons` as the documented PostgreSQL inheritance hierarchy. Persist skills as `TEXT[]`, medical needs as validated `JSONB`, and availability/duty periods as `tsrange`.
2. Implement `VolunteerDao`/`VolunteerService`, `AffectedPersonDao`/`AffectedPersonService`, and `VolunteerShiftDao`/`VolunteerShiftService`.
3. Use database error mapping for exclusion-constraint violations, not a client-side duplicate-shift check. The UI must clearly say that the volunteer has an overlapping shift.
4. Implement affected-person registration, update, deletion, ID lookup, and shelter lookup. The service must not write shelter occupancy directly.
5. Replace all three controllers’ TODO/sample behavior with asynchronous service calls and load selection lists through the appropriate core services.
6. Provide skill search through `skills @> ARRAY[...]` and availability through `v_volunteer_availability`.

**Acceptance tests owned by Dev B**

- Affected-person insert, reassignment, and deletion update occupancy correctly.
- Invalid JSONB and malformed timestamp ranges fail safely.
- Two overlapping shifts for one volunteer fail with the exclusion constraint; adjacent shifts succeed.
- Skill-array search and zone assignments return only correct rows.

### Dev C — authentication, allocation, donations, distribution, audit, analytics, security, and integration

**Owns**

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

**Deliverables**

1. Implement `app_users`, BCrypt login verification, active-user checks, role loading, and zone assignment. Login no longer trusts a role selected by the client; it uses the stored role. Seed one usable account for each of the five roles.
2. Update `Utility.changeScene` so every protected destination has a central screen-to-allowed-roles mapping. Dashboard buttons remain a convenience; the navigation guard becomes the actual enforcement point.
3. Implement request submission/update/cancellation and the one-call allocation service around `fn_allocate_resources`. A zone coordinator can only create/read its assigned-zone requests; warehouse users fulfill but cannot change the request’s zone or priority score.
4. Implement donation processing, low-stock listener/UI adapter, read-only distribution log filtering, and read-only audit-log filtering/diff display.
5. Implement the analytics DAO/service/controller calls for the deficit function, views, materialized views, and refresh action. Refresh permissions belong to an administrator-only service method.
6. Add RLS policies and use `set_config(..., true)` inside each scoped transaction. Ensure a Hikari connection returned to the pool cannot carry one user’s zone context to another user.
7. Update documentation to state 15 physical tables and identify `app_users` as authentication support; remove the stale “setup in progress” status after the backend is complete.

**Acceptance tests owned by Dev C**

- Invalid password, inactive account, and client-selected role mismatch fail; each seed user receives the stored role.
- Allocation rolls back completely when stock is insufficient and succeeds exactly once under concurrent fulfillment attempts.
- Donation JSONB creates one immutable donation record and correctly UPSERTs multiple stock lines.
- RLS denies cross-zone request/affected-person reads and writes; a pooled connection does not leak the former zone context.
- Low-stock crossing sends one payload; audit entries are immutable and contain correct old/new JSON.
- Views, materialized-view refresh, partition-pruned date filtering, and deficit report produce expected rows.

## 6. Cross-team integration contract

| Producer | Consumer | Contract |
|---|---|---|
| Dev A | Dev B/C | `Database` transaction API, core DAOs/services, and migrations V001–V003 must merge before people/operations migrations are run. |
| Dev B | Dev C | `people`, `volunteers`, and `affected_persons` exist before `app_users`, audit triggers, and RLS policies. Expose person IDs as `int` and medical needs as JSON text/DTO. |
| Dev C | Dev A/B | Authenticated `UserSession` is the only source of role and assigned zone. Use service calls; never derive authorization from a ComboBox selection. |
| PostgreSQL functions | Java services | Services call documented function signatures only: `fn_allocate_resources(request_id, source_shelter_id)`, `fn_process_donation(donor_name, receiving_shelter_id, items_jsonb)`, `fn_zone_deficit_report(disaster_id)`, and shift/volunteer functions. |
| All teams | Testcontainers suite | Tests apply all migrations to a fresh PostgreSQL 15 container. No test relies on developer-local data, a pre-existing schema, or test execution order. |

## 7. Implementation sequence

### Wave 0 — agree contracts (one short meeting)

Lock the migration naming convention, Java package layout, error-message vocabulary, seed usernames, and exact function result columns. Record decisions in this document’s “Decisions” section before code begins. No source changes are made in this wave.

### Wave 1 — platform baseline (Dev A only)

Dev A delivers Docker, dependencies, Flyway, Hikari, test support, and V001–V003. The team’s first merge gate is a clean clone running migrations and core tests against PostgreSQL 15.

### Wave 2 — parallel domain work (Dev B + Dev C; Dev A continues core integration)

- Dev B implements V004 and all people/volunteer backend modules.
- Dev C implements V005 operations schema without depending on future audit/RLS code.
- Dev A completes core controller replacement and inventory service/API.

No developer edits a migration owned by another developer. Dependency changes are added as a new migration by the dependent owner.

### Wave 3 — business logic and security (Dev C; Dev B/A supply contract reviews)

Dev C adds V006–V008, authentication, allocation, donation, RLS, analytics, and all operations controllers. Dev B reviews people/occupancy/shift interactions; Dev A reviews inventory transaction and notification interactions.

### Wave 4 — end-to-end integration and hardening (Dev C owns test files)

Run the integration suite against a clean container, then manually exercise all five seeded roles. Dev A and Dev B fix only files in their own ownership areas; Dev C updates the integration test only.

## 8. Definition of done

The backend is complete only when all of the following are true:

- `docker compose up -d` yields a healthy PostgreSQL instance; a fresh database migrates cleanly through V008.
- All 15 physical tables, partitions, extensions, constraints, indexes, functions, triggers, RLS policies, views, and materialized views exist and match the documented contract.
- Every existing controller has no placeholder CRUD/authentication/sample-data path remaining; it calls its assigned service asynchronously.
- All five roles authenticate using stored credentials. Direct navigation and direct DAO calls cannot bypass role or zone restrictions.
- A concurrency test proves that allocation cannot overspend inventory or fulfill one request twice.
- Constraint, trigger, audit, notification, RLS, and analytics tests pass on PostgreSQL 15 via Testcontainers.
- `./gradlew clean test build` succeeds from a clean clone, with a README that documents configuration, migration, test, and demo-login steps.

## 9. Decisions to record before Wave 1

- PostgreSQL database name, non-production user names, and seed usernames (never commit production passwords).
- Whether disaster IDs displayed in the UI remain formatted (`D1001`) while database keys are integers, or whether all UI identifiers move to integer IDs. The recommended choice is integer database IDs with display formatting in the controller.
- Development-data retention policy and monthly `distribution_log` partition maintenance cadence.
- The operational definition of a request’s destination: this plan assumes a zone request is fulfilled to that zone’s primary shelter. If multiple destination shelters must be selectable, add `destination_shelter_id` to `resource_requests` in a new migration before V006 is implemented.
- Which mutable tables are included in `trg_audit_log`; recommended: disasters, zones, shelters, resources, inventory, volunteers, affected persons, requests, shifts, and donations.
