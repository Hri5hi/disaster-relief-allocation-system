# Dev B — Complete Implementation Plan
## Disaster Relief Resource Allocation System (DRRAS) — People / Volunteer Domain

**Produced by:** Senior Software Architect / Technical Lead  
**For execution by:** Lower-capability coding model  
**Branch:** `feature/dev-b`  
**Wave:** Wave 2 (begins after Dev A's Wave 1 baseline merges)

---

# 1. Executive Summary

Dev B owns the people domain of the DRRAS backend. This means:

- The PostgreSQL migration `V004__people_schema.sql` that creates the `people`, `volunteers`, `affected_persons`, and `volunteer_shifts` tables using PostgreSQL inheritance, `TEXT[]`, `JSONB`, and `tsrange` range types.
- The Java model classes `Volunteer.java`, `AffectedPerson.java`, and `VolunteerShift.java` (already stubbed — need completion/review).
- A new `Person.java` base model.
- DAO classes for volunteers, affected persons, and volunteer shifts.
- Service classes for volunteers, affected persons, and volunteer shifts.
- Wiring the three placeholder controllers (`VolunteerController`, `AffectedPersonController`, `VolunteerShiftController`) to those services.
- Integration tests proving the database behavior is correct.

**Nothing Dev B builds is independent.** Dev B starts only after Dev A merges the Wave 1 baseline (V001–V003, `Database` infrastructure, HikariCP, Flyway). Dev C cannot finalize `app_users`, RLS, and audit triggers until Dev B's `people` table exists.

Dev B's entire output provides the `people`, `volunteers`, and `affected_persons` tables that are foreign-key prerequisites for Dev C's `app_users` (linked to `people`) and for audit triggers that fire on all mutable tables including `volunteers`, `affected_persons`, and `volunteer_shifts`.

---

# 2. Complete Project Understanding

## 2.1 What the system does

DRRAS is a disaster-relief coordination platform for government/NGO use. It manages: disaster events and zones, relief shelters, resource inventory, volunteers, displaced persons, resource requests, resource allocation, donation intake, distribution history, and comprehensive audit.

The key engineering principle: **~85% of business logic lives inside PostgreSQL** (stored functions, triggers, constraints, generated columns). Java is a thin presentation layer.

## 2.2 Overall architecture

```text
FXML / JavaFX Controller
          ↓ (JavaFX Task — background thread)
Service layer  (transaction boundaries, domain-error mapping, ServiceResult)
          ↓ (prepared JDBC statements only)
DAO layer     (one DAO per domain entity)
          ↓ (HikariCP connection pool)
PostgreSQL 15+  (tables, constraints, triggers, functions, RLS, views)
```

Rules:
- Controllers NEVER contain SQL or call `DriverManager`.
- DAOs execute parameterized SQL ONLY. Never concatenate SQL strings.
- Services define transaction boundaries. They translate `SQLException`/SQLState into user-safe `ServiceResult` messages.
- PostgreSQL owns: occupancy computation (`trg_auto_update_shelter_occupancy`), exclusion constraint enforcement, GiST indexes, JSONB validation, array storage.

## 2.3 Technology stack

| Technology | Version / Detail |
|---|---|
| Java | 21 (sourceCompatibility set in build.gradle) |
| JavaFX | 21 (javafx-base, javafx-controls, javafx-fxml, javafx-graphics) |
| PostgreSQL | 15+ |
| JDBC | PostgreSQL JDBC 42.x (to be added by Dev A in build.gradle) |
| HikariCP | Latest stable (added by Dev A) |
| Flyway | 10.x (added by Dev A, `filesystem:database/migrations`) |
| JUnit | 5 (added by Dev A) |
| Testcontainers | Latest (postgresql module, added by Dev A) |
| BCrypt | jbcrypt or spring-security-crypto (Dev A adds, used by Dev C) |
| Gradle | Existing, wrapper present |

## 2.4 Functional domains — what Dev B builds

Dev B builds **one functional domain**: the People hierarchy.

This domain covers:
- `people` base table (PostgreSQL base for INHERITS)
- `volunteers` table (INHERITS people) with `TEXT[]` skills, `tsrange` availability, zone FK
- `affected_persons` table (INHERITS people) with `JSONB` medical_needs, shelter FK
- `volunteer_shifts` table (separate join table, not inheriting) with `tsrange` duty_period and GiST exclusion constraint
- The shelter occupancy trigger (`trg_auto_update_shelter_occupancy`) that fires on `affected_persons` INSERT/UPDATE/DELETE
- All Java models, DAOs, services, and controllers for this domain

---

# 3. Source-of-Truth and Documentation Reconciliation

## 3.1 Identified contradictions

### Contradiction A: Java version
- `project_description_v2.md` (line ~71) says: "Java 17 + JavaFX"
- `build.gradle` (lines 14–15) says: `JavaVersion.VERSION_21`
- `BACKEND_IMPLEMENTATION_PLAN.md` (line 94) says: "Store timestamps as `OffsetDateTime`/`Instant`"

**Resolution:** The build.gradle is the authoritative source. **Java 21 is in use.** The project_description_v2.md is slightly stale. Dev B uses Java 21 target. `OffsetDateTime` is correct for TIMESTAMPTZ columns.

### Contradiction B: Table count
- `project_description_v2.md` says 14 tables.
- `docs/3.1_detailed_description.md` says 13 tables.
- `BACKEND_IMPLEMENTATION_PLAN.md` (current version) says 15 tables (after Wave 0 decision to add `app_users`).

**Resolution:** Wave 0 ratified 15 physical tables. The 3.1 doc is the oldest and most stale. 15 tables is correct (14 original + `app_users`). Dev B's own V004 creates 4 of the 15 tables.

### Contradiction C: PostgreSQL inheritance implementation
- `project_description_v2.md` (lines ~170–190) describes `volunteers INHERITS people` and `affected_persons INHERITS people` using PostgreSQL's `INHERITS` keyword — i.e., a physical `INHERITS` relationship at the DB level.
- `docs/3.3_relational_schema.md` (line ~192–209) says ISA is implemented using "Table-per-Type mapping: both the superclass and each subclass become separate relations, and the subclass uses the superclass PK as its own PK (and FK simultaneously)."

**This is a critical contradiction.** PostgreSQL `INHERITS` does NOT create a FK relationship in the traditional sense — the child table inherits columns. The relational schema doc implies a standard FK (`person_id` FK → `people(person_id)`), which is Table-per-Type without `INHERITS`. PostgreSQL's `INHERITS` is **not** Table-per-Type.

**Resolution:** The `project_description_v2.md` and `docs/3.2_er_diagram.md` are the authoritative project requirements — they explicitly state "PostgreSQL `INHERITS`" and describe it as a uniquely PostgreSQL feature the project is deliberately showcasing. The `3.3_relational_schema.md` description of "Table-per-Type" is an approximation/error; the project_description explicitly calls out that `SELECT * FROM people` returns rows from all three tables, which is the PostgreSQL `INHERITS` behavior.

**Dev B must use the PostgreSQL `INHERITS` keyword.** The `volunteers` and `affected_persons` tables must be created with `INHERITS (people)`. The FK notation in 3.3 is a conceptual shorthand, not a literal DDL instruction.

**Consequence:** Because `INHERITS` does NOT enforce a FK at the PostgreSQL level, there is no referential constraint between `volunteers.person_id` and `people.person_id`. The inheritance relationship IS the mechanism. Dev B must document this clearly in V004.

### Contradiction D: `volunteer_shifts` FK reference
- `docs/3.3_relational_schema.md` (line ~330): `volunteer_id` → `volunteers(person_id)`
- `project_description_v2.md` Table 12: `volunteer_id` FK → `volunteers(person_id)`

**Resolution:** No contradiction — both agree. `volunteer_shifts.volunteer_id` references `volunteers(person_id)`.

### Contradiction E: Existing model class design vs. project architecture
- `Volunteer.java` and `AffectedPerson.java` already exist in the repo with full getters/setters.
- They do NOT have a `Person` base class or interface in Java.
- `BACKEND_IMPLEMENTATION_PLAN.md` (line 57) says: "Create `people` as the superclass and `volunteers`/`affected_persons` as the documented PostgreSQL inheritance hierarchy."

**Resolution:** Dev B must create a `Person.java` base class in Java that `Volunteer` and `AffectedPerson` extend. The existing model classes must be refactored to extend `Person`. Dev B owns all three model files. No other developer is affected because the model package has no existing users (no DAOs or services exist yet).

### Contradiction F: `src/main/test` vs `src/test`
- The repo has `src/main/test/java/` (`.gitkeep` only).
- The `BACKEND_IMPLEMENTATION_PLAN.md` (lines 124–125, 158, 203–204) references test paths as `src/test/java/com/groupa13/disasterrelief/people/**`.

**Resolution:** The plan's intended path is `src/test/java/`. The existing `src/main/test/` is either a mistake or legacy. Dev B must create the standard Gradle `src/test/java/` source set for tests. Dev A will configure the test sourceset in `build.gradle`; if that sourceset is not yet present when Dev B works, Dev B must add the test sourceSet configuration to `build.gradle`. Since `build.gradle` is owned by Dev A, Dev B must **request this change** via pull request comment or directly with Dev A, and add a temporary test sourceSet only if blocked.

### Contradiction G: `ServiceResult` record
- `BACKEND_IMPLEMENTATION_PLAN.md` (lines 88–90) shows:
  ```java
  record ServiceResult<T>(boolean success, T value, String message) {}
  ```
- This is documented as owned by Dev A (it lives in `src/main/java/com/groupa13/disasterrelief/db/**`).
- Dev B's services must return `ServiceResult<T>`.

**Resolution:** Dev B must wait for Dev A to provide `ServiceResult`. If Dev A has not yet merged it, Dev B must create a temporary copy in the people service package and request Dev A to provide the shared version. Once Dev A provides it, Dev B updates import statements.

---

# 4. Current Repository State

## 4.1 What already exists

| Item | State |
|---|---|
| `Volunteer.java` | Exists — complete fields, getters, setters. No `Person` parent class. Skills as `String`, availability as `String`. |
| `AffectedPerson.java` | Exists — complete fields, getters, setters. No `Person` parent class. medicalNeeds as `String`. |
| `VolunteerShift.java` | Exists — complete fields, getters, setters. dutyPeriod as `String`. |
| `VolunteerController.java` | Exists — all method stubs with TODO. No service calls. |
| `AffectedPersonController.java` | Exists — all method stubs with TODO. No service calls. |
| `VolunteerShiftController.java` | Exists — all method stubs with TODO. No service calls. |
| `UserSession.java` | Exists — complete singleton with `userId`, `userName`, `role`, `assignedZoneId`. Owned by Dev C but already in place. |
| `UserRole.java` | Exists — complete enum with 5 roles. Owned by Dev C. |
| `Utility.java` | Exists — complete `changeScene` with RBAC guard, `showErrorAlert`, etc. Owned by Dev C. |
| `database/migrations/` | Empty (only `.gitkeep`). No migrations exist yet. |
| `database/procedures/` | Empty (`.gitkeep`). |
| `database/seed/` | Empty (`.gitkeep`). |
| `src/main/java/.../dao/` | Empty (`.gitkeep`). |
| `src/main/java/.../service/` | Empty (`.gitkeep`). |
| `build.gradle` | JavaFX dependencies only. No JDBC, HikariCP, Flyway, JUnit, or Testcontainers yet. |
| `docker-compose.yml` | Empty (0 bytes). |
| `config/` | Only `.gitkeep`. |

## 4.2 What Dev B needs to create

| Item | Type |
|---|---|
| `V004__people_schema.sql` | New database migration |
| `Person.java` | New model base class |
| Refactor `Volunteer.java` | Extend `Person`, change `skills` to `List<String>`, add `List<String>` `getSkillsList()` |
| Refactor `AffectedPerson.java` | Extend `Person` |
| Review `VolunteerShift.java` | Mostly complete — minor additions |
| `VolunteerDao.java` | New DAO |
| `AffectedPersonDao.java` | New DAO |
| `VolunteerShiftDao.java` | New DAO |
| `VolunteerService.java` | New service |
| `AffectedPersonService.java` | New service |
| `VolunteerShiftService.java` | New service |
| `VolunteerController.java` | Replace all TODOs with service calls |
| `AffectedPersonController.java` | Replace all TODOs with service calls |
| `VolunteerShiftController.java` | Replace all TODOs with service calls |
| `src/test/java/.../people/` | New test package with integration tests |

## 4.3 Preconditions — what must exist before Dev B begins

| Precondition | Provider | Status |
|---|---|---|
| `build.gradle` updated with JDBC, HikariCP, Flyway, JUnit 5, Testcontainers | Dev A | NOT YET |
| `docker-compose.yml` functional (PostgreSQL 15 with healthcheck) | Dev A | NOT YET |
| `config/application.properties.example` and `.env.example` | Dev A | NOT YET |
| V001 (`btree_gist`, `pgcrypto`, enums) | Dev A | NOT YET |
| V002 (core tables: disasters, zones, shelters, resource_categories, resources, shelter_inventory) | Dev A | NOT YET |
| V003 (core constraints and indexes) | Dev A | NOT YET |
| `Database.java` (`DataSource dataSource()`, `<T> T inTransaction(...)`) | Dev A | NOT YET |
| `ServiceResult<T>` record | Dev A | NOT YET |
| Testcontainers migration utility / `MigrationTestSupport` class | Dev A | NOT YET |

**Dev B must not start database work until V001–V003 exist and `./gradlew test` passes from a clean checkout.**

Dev B CAN begin model refactoring and DAO/service skeleton writing in parallel because those are pure Java and require no database connection.

---

# 5. Dev B Scope and Responsibilities

## 5.1 Owned files

### Database migrations
```
database/migrations/V004__people_schema.sql
```

### Models
```
src/main/java/com/groupa13/disasterrelief/model/Person.java          (CREATE NEW)
src/main/java/com/groupa13/disasterrelief/model/Volunteer.java        (MODIFY — extend Person, refine types)
src/main/java/com/groupa13/disasterrelief/model/AffectedPerson.java   (MODIFY — extend Person)
src/main/java/com/groupa13/disasterrelief/model/VolunteerShift.java   (MODIFY — minor)
```

### DAOs
```
src/main/java/com/groupa13/disasterrelief/dao/people/VolunteerDao.java
src/main/java/com/groupa13/disasterrelief/dao/people/AffectedPersonDao.java
src/main/java/com/groupa13/disasterrelief/dao/people/VolunteerShiftDao.java
```

### Services
```
src/main/java/com/groupa13/disasterrelief/service/people/VolunteerService.java
src/main/java/com/groupa13/disasterrelief/service/people/AffectedPersonService.java
src/main/java/com/groupa13/disasterrelief/service/people/VolunteerShiftService.java
```

### Controllers (wiring only — FXML not touched)
```
src/main/java/com/groupa13/disasterrelief/controller/VolunteerController.java
src/main/java/com/groupa13/disasterrelief/controller/AffectedPersonController.java
src/main/java/com/groupa13/disasterrelief/controller/VolunteerShiftController.java
```

### Tests
```
src/test/java/com/groupa13/disasterrelief/people/VolunteerDaoTest.java
src/test/java/com/groupa13/disasterrelief/people/AffectedPersonDaoTest.java
src/test/java/com/groupa13/disasterrelief/people/VolunteerShiftDaoTest.java
src/test/java/com/groupa13/disasterrelief/people/VolunteerServiceTest.java
src/test/java/com/groupa13/disasterrelief/people/AffectedPersonServiceTest.java
src/test/java/com/groupa13/disasterrelief/people/VolunteerShiftServiceTest.java
```

## 5.2 Files Dev B must NOT modify

```
src/main/java/com/groupa13/disasterrelief/util/UserRole.java       (Dev C owns)
src/main/java/com/groupa13/disasterrelief/util/UserSession.java    (Dev C owns)
src/main/java/com/groupa13/disasterrelief/util/Utility.java        (Dev C owns)
src/main/java/com/groupa13/disasterrelief/db/**                    (Dev C owns)
build.gradle                                                        (Dev A owns)
docker-compose.yml                                                  (Dev A owns)
config/                                                             (Dev A owns)
database/migrations/V001*.sql through V003*.sql                     (Dev A owns)
database/migrations/V005*.sql and beyond                            (Dev C owns)
All FXML files                                                      (architecture decision: FXML is not touched by backend work)
```

## 5.3 Read-only reference files

```
src/main/java/com/groupa13/disasterrelief/util/UserRole.java    (read to understand role enum values)
src/main/java/com/groupa13/disasterrelief/util/UserSession.java  (read to understand session structure)
src/main/java/com/groupa13/disasterrelief/db/Database.java       (read to understand transaction API — supplied by Dev A)
project_description_v2.md
docs/3.3_relational_schema.md
BACKEND_IMPLEMENTATION_PLAN.md
```

---

# 6. Dev B Dependencies

## 6.1 Inputs from Dev A (hard dependencies)

| Input | What Dev B needs it for |
|---|---|
| `build.gradle` with `testImplementation 'org.testcontainers:postgresql:...'` and JUnit 5 | Running integration tests |
| `V001__extensions_and_types.sql` applied | `btree_gist` extension required for GiST exclusion constraint in V004 |
| `V002__core_schema.sql` applied | `shelters` and `zones` tables must exist before V004 can add FKs to them |
| `V003__core_constraints_indexes.sql` applied | All FK integrity on shelters/zones must be clean |
| `Database.java` with `dataSource()` and `inTransaction()` | DAO connection acquisition; service transaction management |
| `ServiceResult<T>` record | Service method return type |
| Testcontainers migration utility (`MigrationTestSupport`) | Test setup |

## 6.2 Inputs from existing code

| Input | What Dev B uses |
|---|---|
| `UserSession.java` | `UserSession.getInstance().getAssignedZoneId()` in `AffectedPersonController` for zone filtering |
| `UserRole.java` | `UserRole.ZONE_COORDINATOR` check in `AffectedPersonController.initialize()` |
| `Utility.java` | `changeScene(event, "dashboard", "Dashboard")` — all three controllers already use this |

## 6.3 Outputs to Dev C (what Dev C depends on)

| Dev B output | Why Dev C needs it |
|---|---|
| `people` table (created in V004) | `app_users` (V007) references `people(person_id)` via FK |
| `volunteers` table (created in V004) | Audit triggers in V006 fire on `volunteers` |
| `affected_persons` table (created in V004) | Audit triggers in V006 fire on `affected_persons`; RLS policies reference zone context |
| `volunteer_shifts` table (created in V004) | Audit triggers in V006 fire on `volunteer_shifts` |
| Person IDs exposed as `int` | Dev C uses integer person IDs in `app_users` FK |
| Medical needs exposed as `String` (JSON text) | Dev C may read medical needs in integrated views |

## 6.4 Dependency and blocking map

```
Dev A (Wave 1)
  ↓ merges build.gradle + V001–V003 + Database.java + ServiceResult
Dev B begins V004 + DAOs + services (Wave 2)
  ↓ merges people tables
Dev C begins V007 (app_users links to people) + V006 audit triggers on people tables
```

---

# 7. Resolved Technical Decisions

Every decision below has been made here so the lower model need not reason about it.

## 7.1 PostgreSQL inheritance: `INHERITS` vs Table-per-Type
**Decision:** Use PostgreSQL `INHERITS (people)`.
- Both `volunteers` and `affected_persons` are created with `INHERITS (people)`.
- `person_id` in each child table is `SERIAL PRIMARY KEY` — it generates its own sequence independent of `people`'s sequence.
- Child tables inherit `name`, `contact`, `email` columns automatically from `people`.
- `SELECT * FROM people` will return rows from all three tables combined.
- There is no FK from child to parent; inheritance IS the mechanism.
- **Java models**: `Volunteer extends Person`, `AffectedPerson extends Person`.

## 7.2 `TEXT[]` handling in Java
- `Volunteer.skills` in Java is `List<String>` (not `String`).
- To display in JavaFX TableColumn: format as comma-separated string using `String.join(", ", skills)`.
- JDBC binding: `conn.createArrayOf("text", skills.toArray(new String[0]))` → `PreparedStatement.setArray(n, array)`.
- JDBC reading: `(String[]) rs.getArray("skills").getArray()` → `Arrays.asList(...)`.
- Search query for skills: `WHERE skills @> ARRAY[?]::text[]` or multi-skill: `WHERE skills @> ?::text[]` where the parameter is bound as a `PGobject` with type `text[]`.

## 7.3 `tsrange` handling in Java
- `Volunteer.availability` is `String` (human-readable tsrange literal, e.g., `"[2026-07-09 08:00, 2026-07-20 18:00)"`).
- `VolunteerShift.dutyPeriod` is `String`.
- JDBC binding: use `PGobject` with type `"tsrange"` and value equal to the tsrange literal string.
- JDBC reading: `rs.getString("availability")` (PostgreSQL returns tsrange as its literal string form).
- Validation before binding: the string must match pattern `[^[^,]+, [^,]+[^)]` roughly. Use regex: `^\[.+, .+\)$` or `^\(.+, .+\]$` etc. Reject on mismatch with message `"Duty period must be in tsrange format, e.g. [2026-07-09 08:00, 2026-07-09 20:00)"`.
- The controller already builds the tsrange string from two fields: `"[" + start + ", " + end + ")"`. This is correct.

## 7.4 `JSONB` handling in Java
- `AffectedPerson.medicalNeeds` is `String` (raw JSON text).
- JDBC binding: use `PGobject` with type `"jsonb"` and value equal to the JSON text string.
- JDBC reading: `rs.getString("medical_needs")` (PostgreSQL returns JSONB as its text representation).
- Validation: if non-null and non-empty, check that it starts with `{` and ends with `}`. The existing controller already does this check. Retain it.
- Null is allowed: a person can be registered without medical needs.

## 7.5 Shelter occupancy
- Java code NEVER writes `shelters.current_occupancy` directly.
- The PostgreSQL trigger `trg_auto_update_shelter_occupancy` handles this automatically.
- `AffectedPersonService` calls DAO methods that INSERT/UPDATE/DELETE on `affected_persons`. The trigger fires. Java does not check or set occupancy.

## 7.6 Transaction boundaries
- Each service method is one transaction (one database call boundary).
- Services use `Database.inTransaction(session, work)` provided by Dev A.
- DAOs execute queries within the connection provided to them. DAOs do NOT open their own connections.
- For operations that need RLS (affected persons, zone-restricted), the service sets `set_config('app.current_zone_id', zoneId, true)` inside the transaction before the DAO query.

## 7.7 Zone filtering for AffectedPersonController
- When the logged-in user is `ZONE_COORDINATOR`, the controller reads `UserSession.getInstance().getAssignedZoneId()`.
- The service call passes the zone ID; the DAO adds `WHERE ap.shelter_id IN (SELECT shelter_id FROM shelters WHERE zone_id = ?)` or uses the view that Dev C will provide.
- For now (Wave 2), the service has an `getAllAffectedPersons(Integer zoneId)` method: if `zoneId` is null (admin), return all; if non-null, filter by shelters in that zone.

## 7.8 ComboBox population strategy
- `VolunteerController.zoneCombo` needs a list of zones: `List<Zone>` from Dev A's `ZoneService`.
- `AffectedPersonController.shelterCombo` needs a list of shelters: `List<Shelter>` from Dev A's `ShelterService`.
- `VolunteerShiftController.volunteerCombo` needs a list of volunteers: from `VolunteerService`.
- `VolunteerShiftController.zoneCombo` needs a list of zones: from Dev A's `ZoneService`.

**Problem:** Dev A's `ZoneService` and `ShelterService` are not yet available when Dev B begins coding.

**Resolution:** Define interfaces `ZoneServicePort` and `ShelterServicePort` with only the method signatures Dev B needs. Implement stub versions that return empty lists. Replace with real implementations once Dev A's code is available. This lets Dev B proceed without modification of Dev A files.

Alternatively, use a simpler approach that avoids defining interfaces: use `Database.dataSource()` directly in the combo-loading task with a minimal inline query `SELECT zone_id, name FROM zones ORDER BY name` and `SELECT shelter_id, name FROM shelters ORDER BY name`. This is safe because Dev B only reads zones/shelters — it doesn't modify them, and it avoids changing Dev A's files.

**Decision:** Use direct SQL queries in combo-loading (inline in the controller Task, reading from zones/shelters tables). This is acceptable for combo-loading because: (a) it is read-only, (b) it doesn't bypass business logic since no logic is needed for listing, (c) the controller already has the import for `Database`. If Dev A's architecture docs prohibit direct DB access in controllers, use the stub interface approach instead.

**Final decision:** Use a single-method lightweight helper inside each service to provide list data. `VolunteerService.getAllZones()` is NOT appropriate (that's Dev A's domain). Instead, the controller must call Dev A's zone service. Since Dev A's service does not yet exist, the controller will call `Database.dataSource().getConnection()` for this specific combo load ONLY as a temporary measure, and replace with Dev A's service once available. This is explicitly noted as a temporary measure in a `// TODO: Replace with ZoneService.getAllZones() once Dev A merges` comment.

## 7.9 Error message mapping
Wave 0 decision: short, friendly error messages. Map these SQLState codes:

| SQLState | Cause | User message |
|---|---|---|
| `23000` | Integrity constraint violation (generic) | "Database constraint violated" |
| `23502` | NOT NULL violation | "Required field is missing" |
| `23503` | FK violation | "Referenced record does not exist" |
| `23505` | Unique violation | "A duplicate record already exists" |
| `23P01` | Exclusion constraint violation | "Volunteer already has an overlapping shift for this time period" |
| `22007` | Invalid timestamp format | "Invalid date/time format. Use: yyyy-MM-dd HH:mm" |
| `22P02` | Invalid text representation | "Invalid format for the entered value" |

## 7.10 `VolunteerShift` — no UPDATE?
The plan says Dev B implements "registration, update, deletion" for volunteers and affected persons, and "create/cancel" for shifts. Reading the controller, `handleUpdate` is present for shifts.

**Decision:** Implement shift UPDATE (zone change or duty period change, subject to exclusion constraint re-evaluation). The database exclusion constraint will enforce correctness. In PostgreSQL, UPDATE of a row covered by an exclusion constraint re-evaluates the constraint.

## 7.11 Test sourceset location
**Decision:** `src/test/java/com/groupa13/disasterrelief/people/` is the correct location. Dev B must ensure `build.gradle` has a `test` sourceSet. If Dev A hasn't added it, add it with a PR note. The Gradle standard convention already places `src/test/java` as the default test sourceSet when `id 'java'` is applied, so it may not need explicit configuration.

---

# 8. Decision Register

| # | Decision | Chosen Approach | Why | Evidence | Impact |
|---|---|---|---|---|---|
| D-01 | PostgreSQL inheritance | PostgreSQL `INHERITS (people)` | Project explicitly requires showcase of PostgreSQL inheritance | `project_description_v2.md` §5.6; `docs/3.3_relational_schema.md` §Group3 | No FK enforced at DB level; Java uses class inheritance |
| D-02 | Java model hierarchy | `Volunteer extends Person`, `AffectedPerson extends Person` | Mirrors DB inheritance in Java | `BACKEND_IMPLEMENTATION_PLAN.md` §5 Dev B deliverable 1 | Existing Volunteer/AffectedPerson must be refactored |
| D-03 | `TEXT[]` Java type | `List<String>` | Natural Java equivalent; avoids parsing comma string manually | `docs/3.3_relational_schema.md` R8 | Controller must join list to comma string for display |
| D-04 | `TEXT[]` JDBC binding | `conn.createArrayOf("text", array)` → `setArray()` | Standard PostgreSQL JDBC approach for arrays | PostgreSQL JDBC docs | DAO method must obtain connection reference |
| D-05 | `tsrange` / `TEXT[]` read | `rs.getString()` for tsrange; `(String[]) rs.getArray().getArray()` for TEXT[] | tsrange → literal string; arrays → Java String[] | PostgreSQL JDBC behavior | Simple string display; no parsing needed in Java |
| D-06 | `JSONB` JDBC binding | `PGobject` with type `"jsonb"` | Required for PostgreSQL JSONB parameter binding | PostgreSQL JDBC docs | DAO must import `org.postgresql.util.PGobject` |
| D-07 | Occupancy management | Never write occupancy in Java; trigger handles it | Architecture requirement | `BACKEND_IMPLEMENTATION_PLAN.md` §3 behavior; `project_description_v2.md` §5.2 | `AffectedPersonService` does not call any occupancy update method |
| D-08 | Transaction scope | One service method = one transaction via `Database.inTransaction()` | Architecture requirement | `BACKEND_IMPLEMENTATION_PLAN.md` §4 conventions | DAOs receive `Connection` parameter, not pool |
| D-09 | ComboBox data loading | Temporary inline SQL in Task for zones/shelters combo; replace with Dev A services when available | Dev A's service not yet available in Wave 2 | Cross-team integration contract | Mark clearly with TODO comment; do not block |
| D-10 | Shift exclusion errors | Map `SQLState 23P01` → "Volunteer already has an overlapping shift for this time period" | Wave 0 decision (short friendly messages); plan §4 | `BACKEND_IMPLEMENTATION_PLAN.md` §4 | Controller shows this message in errorLabel |
| D-11 | `availability` in `volunteers` | `tsrange` column, nullable | Volunteer may be registered without availability window | Schema spec | Bind as `PGobject`; null if empty string |
| D-12 | Zone-scoped affected persons | `getAllAffectedPersons(Integer zoneId)` — null means all, non-null filters | ZONE_COORDINATOR must see only zone's shelters | `BACKEND_IMPLEMENTATION_PLAN.md` §5 Dev B deliverable 4; `AffectedPersonController` comment block |  Must apply `set_config` before RLS-filtered query |
| D-13 | Test framework | JUnit 5 + Testcontainers (PostgreSQL module) | Project-specified | `BACKEND_IMPLEMENTATION_PLAN.md` §2 "Verification" row | Requires Dev A's testcontainers dependency |
| D-14 | Skill search JDBC | Single skill: `skills @> ARRAY[?]::text[]`; multi-skill input split by comma | Array containment operator is the specified approach | `project_description_v2.md` §5.4; `VolunteerController.handleSearchBySkill` comment | DAO `findBySkill(String skill)` uses PGobject for array param |
| D-15 | `v_volunteer_availability` | Dev B queries this view for available volunteer list | Plan specifies view access for availability | `BACKEND_IMPLEMENTATION_PLAN.md` §5 Dev B deliverable 6 | View is created by Dev C in V007; Dev B must handle graceful absence of view in Wave 2 |

---

# 9. Detailed Dev B Implementation Plan

## Overview of phases

```
PHASE 0 — Preconditions and setup
PHASE 1 — Database migration (V004)
PHASE 2 — Java models
PHASE 3 — DAO layer
PHASE 4 — Service layer
PHASE 5 — Controller wiring
PHASE 6 — Integration tests
PHASE 7 — Verification
PHASE 8 — PR preparation and handoff
```

---

# 10. Database Implementation

## Step 1 — Create V004__people_schema.sql

### Objective
Create the four tables of the people domain: `people`, `volunteers`, `affected_persons`, and `volunteer_shifts`, including the shelter-occupancy trigger.

### Why
These tables are the schema foundation for Dev B's entire domain. No Java code can run against the database without this migration existing and being applied cleanly.

### Prerequisites
- V001 applied: `btree_gist` extension is installed (required for GiST exclusion constraint on `volunteer_shifts`).
- V002 applied: `shelters` and `zones` tables exist.
- V003 applied: all FK constraints on V002 tables are valid.
- Flyway configured by Dev A to read from `filesystem:database/migrations`.

### Files Involved
**Create:**  
`database/migrations/V004__people_schema.sql`

**Read-only / Reference:**  
`database/migrations/V001__extensions_and_types.sql` (confirm `btree_gist` is installed)  
`database/migrations/V002__core_schema.sql` (confirm `shelters` and `zones` column names)  
`project_description_v2.md`  
`docs/3.3_relational_schema.md`

**Do Not Touch:**  
All other migration files.

### Technical Decision
- Use `CREATE TABLE ... INHERITS (people)` for `volunteers` and `affected_persons`.
- `volunteer_shifts` is a separate table (not inheriting) with an `EXCLUDE USING gist` constraint.
- `trg_auto_update_shelter_occupancy` fires `AFTER INSERT OR DELETE OR UPDATE OF shelter_id ON affected_persons`.

### Exact SQL Content

```sql
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
```

### Why each choice exists
- **`INHERITS (people)`**: The project explicitly requires showcasing this PostgreSQL feature.
- **`SERIAL PRIMARY KEY` re-declared in children**: Each child table needs its own sequence to generate independent `person_id` values. Without re-declaring, INSERT into `volunteers` would use `people`'s sequence, which is correct behavior, but the PK must be explicit for clarity.
- **`btree_gist` in V001**: The `EXCLUDE USING GIST` on `volunteer_shifts` requires `btree_gist` to handle the combined `volunteer_id WITH =` (btree-type equality) and `duty_period WITH &&` (GiST-type overlap) in a single GiST index.
- **`ON DELETE SET NULL` on `assigned_zone`**: Allows zones to be deleted without cascading volunteer deletion.
- **`ON DELETE CASCADE` on shift FK**: Deleting a volunteer removes their shifts.
- **`fn_update_shelter_occupancy` function**: Uses `COUNT(*)` on `affected_persons` at each shelter rather than incrementing/decrementing, because a count-based approach is always accurate even if concurrent changes occur.

### Procedure

1. Create file `database/migrations/V004__people_schema.sql`.
2. Copy the exact SQL content from the "Exact SQL Content" section above into the file.
3. Start Docker Compose: `docker compose up -d` (after Dev A has configured it).
4. Confirm PostgreSQL is healthy: `docker compose ps`.
5. Run Flyway migration (as configured by Dev A) to apply V001–V004 in order.
6. Connect to the database and run:
   ```sql
   \d people
   \d volunteers
   \d affected_persons
   \d volunteer_shifts
   \df fn_update_shelter_occupancy
   \d+ volunteers  -- confirm GIN index on skills
   ```
7. Verify all tables, indexes, and the trigger function exist.

### Verification

```sql
-- Confirm tables exist
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('people', 'volunteers', 'affected_persons', 'volunteer_shifts');
-- Expected: 4 rows

-- Confirm inheritance
SELECT parent.relname AS parent, child.relname AS child
FROM pg_inherits
JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
JOIN pg_class child ON pg_inherits.inhrelid = child.oid;
-- Expected: people → volunteers, people → affected_persons

-- Confirm exclusion constraint
SELECT conname, contype FROM pg_constraint
WHERE conrelid = 'volunteer_shifts'::regclass;
-- Expected: includes a constraint of type 'x' (exclusion)

-- Confirm occupancy trigger
SELECT trigger_name FROM information_schema.triggers
WHERE event_object_table = 'affected_persons';
-- Expected: trg_auto_update_shelter_occupancy
```

### Failure Diagnosis

| Failure | Cause | Fix |
|---|---|---|
| `ERROR: extension "btree_gist" is not installed` | V001 not applied or btree_gist not in V001 | Ensure V001 contains `CREATE EXTENSION IF NOT EXISTS btree_gist` |
| `ERROR: relation "shelters" does not exist` | V002 not applied | Apply V001–V003 first |
| `ERROR: relation "zones" does not exist` | Same as above | Same fix |
| `ERROR: syntax error at or near "EXCLUDE"` | PostgreSQL version < 9.0 or `btree_gist` missing | Confirm PostgreSQL 15+, confirm `btree_gist` installed |
| Migration version conflict | Flyway checksum error if file was edited after first apply | Never edit applied migrations; create a new V005 correction |

### Completion Criteria
V004 applies cleanly from a clean (no prior schema) state via `./gradlew flywayMigrate` (or equivalent). All SQL verification queries above return expected results. Running `./gradlew test` (with Dev A's test suite) still passes V001–V003 tests.

---

# 11. Model Implementation

## Step 2 — Create Person.java (base model)

### Objective
Create a `Person` Java base class that `Volunteer` and `AffectedPerson` will extend.

### Why
The BACKEND_IMPLEMENTATION_PLAN.md (Dev B deliverable 1) requires: "Create `people` as the superclass and `volunteers`/`affected_persons` as the documented PostgreSQL inheritance hierarchy." The Java class hierarchy must mirror the database inheritance.

### Files Involved
**Create:**  
`src/main/java/com/groupa13/disasterrelief/model/Person.java`

**Modify:**  
`src/main/java/com/groupa13/disasterrelief/model/Volunteer.java`  
`src/main/java/com/groupa13/disasterrelief/model/AffectedPerson.java`

### Exact Changes

**`Person.java` — CREATE:**

```java
package com.groupa13.disasterrelief.model;

/**
 * Base model for the 'people' table.
 * Corresponds to the PostgreSQL superclass table in the ISA hierarchy.
 * Both Volunteer and AffectedPerson extend this class.
 *
 * Schema: docs/3.3_relational_schema.md Relation 7
 * DB table: people(person_id, name, contact, email)
 *
 * Note: PostgreSQL's INHERITS means SELECT * FROM people returns
 * rows from volunteers and affected_persons too.
 */
public abstract class Person {

    protected int personId;
    protected String name;
    protected String contact;
    protected String email;

    protected Person() {}

    protected Person(int personId, String name, String contact, String email) {
        this.personId = personId;
        this.name = name;
        this.contact = contact;
        this.email = email;
    }

    public int getPersonId() { return personId; }
    public void setPersonId(int personId) { this.personId = personId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
```

## Step 3 — Refactor Volunteer.java

### Objective
Make `Volunteer` extend `Person`. Change `skills` from `String` to `List<String>`. Add a `getSkillsDisplay()` convenience method for the JavaFX table column.

### Exact Changes

**`Volunteer.java` — REPLACE entirely with:**

```java
package com.groupa13.disasterrelief.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model for the 'volunteers' table (INHERITS people).
 * Schema: docs/3.3_relational_schema.md Relation 8
 *
 * Volunteer-specific columns:
 *   skills       TEXT[]   -- PostgreSQL array; Java List<String>
 *   availability TSRANGE  -- stored/displayed as a string literal
 *   assigned_zone INTEGER -- FK → zones(zone_id), nullable
 *
 * JDBC binding:
 *   skills: conn.createArrayOf("text", skills.toArray(new String[0]))
 *   availability: PGobject with type "tsrange"
 */
public class Volunteer extends Person {

    /** Skill tags. PostgreSQL TEXT[]. JDBC: createArrayOf("text", ...) */
    private List<String> skills;

    /**
     * Availability window as a tsrange literal string,
     * e.g. "[2026-07-09 08:00, 2026-07-20 18:00)".
     * Null or empty means not specified.
     * JDBC: PGobject with type "tsrange"
     */
    private String availability;

    /** FK → zones(zone_id). Nullable. */
    private Integer assignedZone;

    /** Display-friendly zone name (joined from zones, not a DB column). */
    private String zoneName;

    public Volunteer() {
        this.skills = new ArrayList<>();
    }

    public Volunteer(int personId, String name, String contact, String email,
                     List<String> skills, String availability, Integer assignedZone) {
        super(personId, name, contact, email);
        this.skills = skills != null ? skills : new ArrayList<>();
        this.availability = availability;
        this.assignedZone = assignedZone;
    }

    // --- Getters ---

    public List<String> getSkills() { return skills; }

    /**
     * Returns skills as a comma-separated string for JavaFX TableColumn display.
     * PropertyValueFactory in the controller binds to "skillsDisplay".
     */
    public String getSkillsDisplay() {
        return skills == null || skills.isEmpty() ? "" : String.join(", ", skills);
    }

    public String getAvailability() { return availability; }
    public Integer getAssignedZone() { return assignedZone; }
    public String getZoneName() { return zoneName; }

    // --- Setters ---

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }
    public void setAvailability(String availability) { this.availability = availability; }
    public void setAssignedZone(Integer assignedZone) { this.assignedZone = assignedZone; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
```

> **Important:** Because the `colSkills` column in `VolunteerController` uses `new PropertyValueFactory<>("skills")`, and `skills` now returns `List<String>`, this would display `[first_aid, driving]` in the table. Change the `PropertyValueFactory` in the controller to `"skillsDisplay"` (which returns the comma-joined string). This is done in Step 9 (Controller wiring).

## Step 4 — Refactor AffectedPerson.java

### Exact Changes

**`AffectedPerson.java` — REPLACE entirely with:**

```java
package com.groupa13.disasterrelief.model;

/**
 * Model for the 'affected_persons' table (INHERITS people).
 * Schema: docs/3.3_relational_schema.md Relation 9
 *
 * Affected-person-specific columns:
 *   medical_needs JSONB         -- stored as JSON text string in Java
 *   shelter_id    INTEGER       -- FK → shelters(shelter_id), nullable
 *   registered_at TIMESTAMPTZ  -- stored as display string (OffsetDateTime.toString())
 *
 * JDBC binding:
 *   medical_needs: PGobject with type "jsonb"
 *   shelter_id: setInt() or setNull(Types.INTEGER)
 */
public class AffectedPerson extends Person {

    /**
     * JSON document for medical needs.
     * e.g. {"conditions": ["diabetes"], "wheelchair": true}
     * NULL or empty string means no recorded medical needs.
     * JDBC: PGobject with type "jsonb"
     */
    private String medicalNeeds;

    /** FK → shelters(shelter_id). Nullable. */
    private Integer shelterId;

    /** TIMESTAMPTZ stored as formatted string for display. */
    private String registeredAt;

    /** Display-friendly shelter name (joined from shelters, not a DB column). */
    private String shelterName;

    public AffectedPerson() {}

    public AffectedPerson(int personId, String name, String contact, String email,
                          String medicalNeeds, Integer shelterId, String registeredAt) {
        super(personId, name, contact, email);
        this.medicalNeeds = medicalNeeds;
        this.shelterId = shelterId;
        this.registeredAt = registeredAt;
    }

    // --- Getters ---
    public String getMedicalNeeds() { return medicalNeeds; }
    public Integer getShelterId() { return shelterId; }
    public String getRegisteredAt() { return registeredAt; }
    public String getShelterName() { return shelterName; }

    // --- Setters ---
    public void setMedicalNeeds(String medicalNeeds) { this.medicalNeeds = medicalNeeds; }
    public void setShelterId(Integer shelterId) { this.shelterId = shelterId; }
    public void setRegisteredAt(String registeredAt) { this.registeredAt = registeredAt; }
    public void setShelterName(String shelterName) { this.shelterName = shelterName; }

    @Override
    public String toString() {
        return personId + " — " + name;
    }
}
```

## Step 5 — Review/Finalize VolunteerShift.java

The existing `VolunteerShift.java` is largely correct. Minor update: add a comment clarifying the JDBC binding for `dutyPeriod`.

No structural changes needed. The existing class remains valid. Add only the JDBC comment.

---

# 12. DAO Implementation

## Step 6 — Create VolunteerDao.java

### Objective
Implement all database operations for the `volunteers` table using prepared JDBC statements.

### Package
`com.groupa13.disasterrelief.dao.people`

### Files Involved
**Create:** `src/main/java/com/groupa13/disasterrelief/dao/people/VolunteerDao.java`

### Methods — Exact Signatures and SQL

```java
package com.groupa13.disasterrelief.dao.people;

import com.groupa13.disasterrelief.model.Volunteer;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the 'volunteers' table.
 * All methods receive a live Connection from the service transaction scope.
 * No method opens or closes connections.
 * No SQL string concatenation is used anywhere.
 * All parameters are bound with PreparedStatement.
 */
public class VolunteerDao {

    // ── Insert ────────────────────────────────────────────────────────────────

    /**
     * INSERT a new volunteer. Returns the generated person_id.
     *
     * SQL: INSERT INTO volunteers (name, contact, email, skills, availability, assigned_zone)
     *      VALUES (?, ?, ?, ?, ?::tsrange, ?) RETURNING person_id
     *
     * @param conn       live connection (in transaction)
     * @param name       required, NOT NULL
     * @param contact    nullable
     * @param email      nullable
     * @param skills     nullable list; empty list stored as empty TEXT[]
     * @param availability nullable tsrange literal; null if not specified
     * @param zoneId     nullable FK to zones
     * @return generated person_id
     * @throws SQLException on any DB error
     */
    public int insert(Connection conn, String name, String contact, String email,
                      List<String> skills, String availability, Integer zoneId)
            throws SQLException {
        String sql = "INSERT INTO volunteers (name, contact, email, skills, availability, assigned_zone) "
                   + "VALUES (?, ?, ?, ?, ?::tsrange, ?) RETURNING person_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            // Bind TEXT[]
            if (skills == null || skills.isEmpty()) {
                ps.setArray(4, conn.createArrayOf("text", new String[0]));
            } else {
                ps.setArray(4, conn.createArrayOf("text", skills.toArray(new String[0])));
            }
            // Bind tsrange
            if (availability == null || availability.isBlank()) {
                ps.setNull(5, Types.OTHER);
            } else {
                PGobject tsrange = new PGobject();
                tsrange.setType("tsrange");
                tsrange.setValue(availability);
                ps.setObject(5, tsrange);
            }
            // Bind zone FK
            if (zoneId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, zoneId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("person_id");
                }
                throw new SQLException("INSERT into volunteers returned no RETURNING id");
            }
        }
    }

    // ── Select All ────────────────────────────────────────────────────────────

    /**
     * SELECT all volunteers with joined zone name.
     *
     * SQL: SELECT v.*, z.name AS zone_name
     *      FROM volunteers v
     *      LEFT JOIN zones z ON v.assigned_zone = z.zone_id
     *      ORDER BY v.name
     */
    public List<Volunteer> findAll(Connection conn) throws SQLException {
        String sql = "SELECT v.person_id, v.name, v.contact, v.email, "
                   + "       v.skills, v.availability::text, v.assigned_zone, z.name AS zone_name "
                   + "FROM volunteers v "
                   + "LEFT JOIN zones z ON v.assigned_zone = z.zone_id "
                   + "ORDER BY v.name";
        List<Volunteer> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Select by ID ──────────────────────────────────────────────────────────

    /**
     * SELECT volunteer by person_id.
     *
     * SQL: SELECT v.*, z.name AS zone_name
     *      FROM volunteers v
     *      LEFT JOIN zones z ON v.assigned_zone = z.zone_id
     *      WHERE v.person_id = ?
     */
    public Optional<Volunteer> findById(Connection conn, int personId) throws SQLException {
        String sql = "SELECT v.person_id, v.name, v.contact, v.email, "
                   + "       v.skills, v.availability::text, v.assigned_zone, z.name AS zone_name "
                   + "FROM volunteers v "
                   + "LEFT JOIN zones z ON v.assigned_zone = z.zone_id "
                   + "WHERE v.person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    // ── Select by Skill ───────────────────────────────────────────────────────

    /**
     * SELECT volunteers who have ALL of the given skills.
     * Uses PostgreSQL array containment: skills @> ARRAY[...]::text[]
     *
     * @param conn   live connection
     * @param skills one or more skills to match (all must be present)
     */
    public List<Volunteer> findBySkills(Connection conn, List<String> skills) throws SQLException {
        // Build: WHERE skills @> ARRAY[?]::text[]
        // We bind the skills as a PostgreSQL text[] parameter
        String sql = "SELECT v.person_id, v.name, v.contact, v.email, "
                   + "       v.skills, v.availability::text, v.assigned_zone, z.name AS zone_name "
                   + "FROM volunteers v "
                   + "LEFT JOIN zones z ON v.assigned_zone = z.zone_id "
                   + "WHERE v.skills @> ? "
                   + "ORDER BY v.name";
        List<Volunteer> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Bind as text[] using createArrayOf
            Array skillArray = conn.createArrayOf("text", skills.toArray(new String[0]));
            ps.setArray(1, skillArray);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * UPDATE volunteer fields. All parameters are nullable — only non-null
     * values from the caller update the corresponding columns.
     * Uses COALESCE to preserve existing values when parameter is null.
     *
     * SQL: UPDATE volunteers
     *      SET name=COALESCE(?,name), contact=COALESCE(?,contact),
     *          email=COALESCE(?,email), skills=COALESCE(?,skills),
     *          availability=COALESCE(?::tsrange,availability),
     *          assigned_zone=COALESCE(?,assigned_zone)
     *      WHERE person_id=?
     */
    public boolean update(Connection conn, int personId, String name, String contact, String email,
                          List<String> skills, String availability, Integer zoneId)
            throws SQLException {
        String sql = "UPDATE volunteers "
                   + "SET name = COALESCE(?, name), "
                   + "    contact = COALESCE(?, contact), "
                   + "    email = COALESCE(?, email), "
                   + "    skills = COALESCE(?, skills), "
                   + "    availability = COALESCE(?::tsrange, availability), "
                   + "    assigned_zone = COALESCE(?, assigned_zone) "
                   + "WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            if (skills == null) {
                ps.setNull(4, Types.ARRAY);
            } else {
                ps.setArray(4, conn.createArrayOf("text", skills.toArray(new String[0])));
            }
            if (availability == null || availability.isBlank()) {
                ps.setNull(5, Types.OTHER);
            } else {
                PGobject tsrange = new PGobject();
                tsrange.setType("tsrange");
                tsrange.setValue(availability);
                ps.setObject(5, tsrange);
            }
            if (zoneId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, zoneId);
            }
            ps.setInt(7, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * DELETE volunteer by person_id.
     * CASCADE will also delete their volunteer_shifts.
     *
     * SQL: DELETE FROM volunteers WHERE person_id = ?
     */
    public boolean delete(Connection conn, int personId) throws SQLException {
        String sql = "DELETE FROM volunteers WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── ResultSet Mapping ─────────────────────────────────────────────────────

    private Volunteer mapRow(ResultSet rs) throws SQLException {
        Volunteer v = new Volunteer();
        v.setPersonId(rs.getInt("person_id"));
        v.setName(rs.getString("name"));
        v.setContact(rs.getString("contact"));
        v.setEmail(rs.getString("email"));
        // Map TEXT[] skills
        Array skillsArray = rs.getArray("skills");
        if (skillsArray != null) {
            v.setSkills(new ArrayList<>(Arrays.asList((String[]) skillsArray.getArray())));
        }
        v.setAvailability(rs.getString("availability")); // tsrange as string
        int zone = rs.getInt("assigned_zone");
        v.setAssignedZone(rs.wasNull() ? null : zone);
        v.setZoneName(rs.getString("zone_name")); // nullable, from LEFT JOIN
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
```

## Step 7 — Create AffectedPersonDao.java

```java
package com.groupa13.disasterrelief.dao.people;

import com.groupa13.disasterrelief.model.AffectedPerson;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the 'affected_persons' table.
 * All methods receive a live Connection from the service transaction scope.
 */
public class AffectedPersonDao {

    // ── Insert ────────────────────────────────────────────────────────────────

    /**
     * INSERT a new affected person. Returns the generated person_id.
     * The trg_auto_update_shelter_occupancy trigger fires automatically.
     *
     * SQL: INSERT INTO affected_persons (name, contact, email, medical_needs, shelter_id)
     *      VALUES (?, ?, ?, ?::jsonb, ?) RETURNING person_id, registered_at
     */
    public AffectedPerson insert(Connection conn, String name, String contact, String email,
                                 String medicalNeeds, Integer shelterId) throws SQLException {
        String sql = "INSERT INTO affected_persons (name, contact, email, medical_needs, shelter_id) "
                   + "VALUES (?, ?, ?, ?::jsonb, ?) "
                   + "RETURNING person_id, registered_at";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            bindJsonb(ps, 4, medicalNeeds);
            if (shelterId == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, shelterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AffectedPerson ap = new AffectedPerson();
                    ap.setPersonId(rs.getInt("person_id"));
                    ap.setName(name);
                    ap.setContact(contact);
                    ap.setEmail(email);
                    ap.setMedicalNeeds(medicalNeeds);
                    ap.setShelterId(shelterId);
                    ap.setRegisteredAt(rs.getString("registered_at"));
                    return ap;
                }
                throw new SQLException("INSERT into affected_persons returned no row");
            }
        }
    }

    // ── Select All ────────────────────────────────────────────────────────────

    /**
     * SELECT all affected persons, joined with shelter name.
     */
    public List<AffectedPerson> findAll(Connection conn) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "ORDER BY ap.registered_at DESC";
        List<AffectedPerson> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    // ── Select by Zone ────────────────────────────────────────────────────────

    /**
     * SELECT affected persons at shelters belonging to a given zone.
     * Used for Zone Coordinator filtered view.
     *
     * SQL: SELECT ap.*, s.name AS shelter_name
     *      FROM affected_persons ap
     *      JOIN shelters s ON ap.shelter_id = s.shelter_id
     *      JOIN zones z ON ... -- shelters don't directly FK to zones
     *      -- Filter: only shelters that are the primary_shelter of the given zone
     *      -- OR: persons at any shelter in a zone (requires zone-shelter relationship)
     *
     * NOTE: The schema does not have a shelters.zone_id FK directly.
     * Zones reference shelters via primary_shelter_id (the primary shelter).
     * For zone filtering, query affected_persons WHERE shelter_id = 
     *   (SELECT primary_shelter_id FROM zones WHERE zone_id = ?)
     * This returns persons at the zone's primary shelter only.
     * A broader approach would require Dev A's shelter-zone relationship.
     * For now, use primary_shelter_id filter:
     */
    public List<AffectedPerson> findByZone(Connection conn, int zoneId) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "WHERE ap.shelter_id = (SELECT primary_shelter_id FROM zones WHERE zone_id = ?) "
                   + "ORDER BY ap.registered_at DESC";
        List<AffectedPerson> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, zoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Select by ID ──────────────────────────────────────────────────────────

    public Optional<AffectedPerson> findById(Connection conn, int personId) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "WHERE ap.person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    // ── Select by Shelter ─────────────────────────────────────────────────────

    public List<AffectedPerson> findByShelterId(Connection conn, int shelterId) throws SQLException {
        String sql = "SELECT ap.person_id, ap.name, ap.contact, ap.email, "
                   + "       ap.medical_needs::text, ap.shelter_id, ap.registered_at::text, "
                   + "       s.name AS shelter_name "
                   + "FROM affected_persons ap "
                   + "LEFT JOIN shelters s ON ap.shelter_id = s.shelter_id "
                   + "WHERE ap.shelter_id = ? "
                   + "ORDER BY ap.registered_at DESC";
        List<AffectedPerson> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shelterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * UPDATE affected person. Uses COALESCE for partial updates.
     * The trg_auto_update_shelter_occupancy fires on UPDATE OF shelter_id.
     */
    public boolean update(Connection conn, int personId, String name, String contact,
                          String email, String medicalNeeds, Integer shelterId) throws SQLException {
        String sql = "UPDATE affected_persons "
                   + "SET name = COALESCE(?, name), "
                   + "    contact = COALESCE(?, contact), "
                   + "    email = COALESCE(?, email), "
                   + "    medical_needs = COALESCE(?::jsonb, medical_needs), "
                   + "    shelter_id = COALESCE(?, shelter_id) "
                   + "WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableString(ps, 1, name);
            setNullableString(ps, 2, contact);
            setNullableString(ps, 3, email);
            bindJsonb(ps, 4, medicalNeeds);
            if (shelterId == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, shelterId);
            ps.setInt(6, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * DELETE affected person by person_id.
     * The trg_auto_update_shelter_occupancy fires automatically.
     * Java does NOT manually update occupancy.
     */
    public boolean delete(Connection conn, int personId) throws SQLException {
        String sql = "DELETE FROM affected_persons WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── ResultSet Mapping ─────────────────────────────────────────────────────

    private AffectedPerson mapRow(ResultSet rs) throws SQLException {
        AffectedPerson ap = new AffectedPerson();
        ap.setPersonId(rs.getInt("person_id"));
        ap.setName(rs.getString("name"));
        ap.setContact(rs.getString("contact"));
        ap.setEmail(rs.getString("email"));
        ap.setMedicalNeeds(rs.getString("medical_needs")); // JSONB as text
        int sid = rs.getInt("shelter_id");
        ap.setShelterId(rs.wasNull() ? null : sid);
        ap.setRegisteredAt(rs.getString("registered_at"));
        ap.setShelterName(rs.getString("shelter_name"));
        return ap;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, value);
    }

    /**
     * Bind a JSONB value to a PreparedStatement parameter.
     * If the value is null or blank, bind SQL NULL.
     */
    private void bindJsonb(PreparedStatement ps, int index, String json) throws SQLException {
        if (json == null || json.isBlank()) {
            ps.setNull(index, Types.OTHER);
        } else {
            PGobject jsonb = new PGobject();
            jsonb.setType("jsonb");
            jsonb.setValue(json);
            ps.setObject(index, jsonb);
        }
    }
}
```

## Step 8 — Create VolunteerShiftDao.java

```java
package com.groupa13.disasterrelief.dao.people;

import com.groupa13.disasterrelief.model.VolunteerShift;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for the 'volunteer_shifts' table.
 * The GiST exclusion constraint (volunteer_id WITH =, duty_period WITH &&)
 * is enforced by PostgreSQL. When it fires, SQLState is '23P01'.
 * The service layer maps this to: "Volunteer already has an overlapping shift."
 */
public class VolunteerShiftDao {

    // ── Insert ────────────────────────────────────────────────────────────────

    /**
     * INSERT a new shift. Returns the created VolunteerShift.
     * If exclusion constraint fires, throws SQLException with SQLState 23P01.
     *
     * SQL: INSERT INTO volunteer_shifts (volunteer_id, zone_id, duty_period)
     *      VALUES (?, ?, ?::tsrange)
     *      RETURNING shift_id
     */
    public VolunteerShift insert(Connection conn, int volunteerId, int zoneId,
                                 String dutyPeriod) throws SQLException {
        String sql = "INSERT INTO volunteer_shifts (volunteer_id, zone_id, duty_period) "
                   + "VALUES (?, ?, ?::tsrange) RETURNING shift_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, volunteerId);
            ps.setInt(2, zoneId);
            PGobject range = new PGobject();
            range.setType("tsrange");
            range.setValue(dutyPeriod);
            ps.setObject(3, range);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    VolunteerShift shift = new VolunteerShift();
                    shift.setShiftId(rs.getInt("shift_id"));
                    shift.setVolunteerId(volunteerId);
                    shift.setZoneId(zoneId);
                    shift.setDutyPeriod(dutyPeriod);
                    return shift;
                }
                throw new SQLException("INSERT into volunteer_shifts returned no row");
            }
        }
    }

    // ── Select All ────────────────────────────────────────────────────────────

    public List<VolunteerShift> findAll(Connection conn) throws SQLException {
        String sql = "SELECT vs.shift_id, vs.volunteer_id, vs.zone_id, "
                   + "       vs.duty_period::text, "
                   + "       v.name AS volunteer_name, z.name AS zone_name "
                   + "FROM volunteer_shifts vs "
                   + "JOIN volunteers v ON vs.volunteer_id = v.person_id "
                   + "JOIN zones z ON vs.zone_id = z.zone_id "
                   + "ORDER BY vs.duty_period DESC";
        List<VolunteerShift> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    // ── Select by ID ──────────────────────────────────────────────────────────

    public Optional<VolunteerShift> findById(Connection conn, int shiftId) throws SQLException {
        String sql = "SELECT vs.shift_id, vs.volunteer_id, vs.zone_id, "
                   + "       vs.duty_period::text, "
                   + "       v.name AS volunteer_name, z.name AS zone_name "
                   + "FROM volunteer_shifts vs "
                   + "JOIN volunteers v ON vs.volunteer_id = v.person_id "
                   + "JOIN zones z ON vs.zone_id = z.zone_id "
                   + "WHERE vs.shift_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    // ── Select by Volunteer ───────────────────────────────────────────────────

    public List<VolunteerShift> findByVolunteerId(Connection conn, int volunteerId) throws SQLException {
        String sql = "SELECT vs.shift_id, vs.volunteer_id, vs.zone_id, "
                   + "       vs.duty_period::text, "
                   + "       v.name AS volunteer_name, z.name AS zone_name "
                   + "FROM volunteer_shifts vs "
                   + "JOIN volunteers v ON vs.volunteer_id = v.person_id "
                   + "JOIN zones z ON vs.zone_id = z.zone_id "
                   + "WHERE vs.volunteer_id = ? "
                   + "ORDER BY vs.duty_period";
        List<VolunteerShift> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, volunteerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapRow(rs));
            }
        }
        return results;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * UPDATE zone or duty_period of a shift.
     * The exclusion constraint will re-evaluate for the new duty_period.
     */
    public boolean update(Connection conn, int shiftId, Integer zoneId,
                          String dutyPeriod) throws SQLException {
        String sql = "UPDATE volunteer_shifts "
                   + "SET zone_id = COALESCE(?, zone_id), "
                   + "    duty_period = COALESCE(?::tsrange, duty_period) "
                   + "WHERE shift_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (zoneId == null) ps.setNull(1, Types.INTEGER);
            else ps.setInt(1, zoneId);
            if (dutyPeriod == null || dutyPeriod.isBlank()) {
                ps.setNull(2, Types.OTHER);
            } else {
                PGobject range = new PGobject();
                range.setType("tsrange");
                range.setValue(dutyPeriod);
                ps.setObject(2, range);
            }
            ps.setInt(3, shiftId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public boolean delete(Connection conn, int shiftId) throws SQLException {
        String sql = "DELETE FROM volunteer_shifts WHERE shift_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── ResultSet Mapping ─────────────────────────────────────────────────────

    private VolunteerShift mapRow(ResultSet rs) throws SQLException {
        VolunteerShift s = new VolunteerShift();
        s.setShiftId(rs.getInt("shift_id"));
        s.setVolunteerId(rs.getInt("volunteer_id"));
        s.setZoneId(rs.getInt("zone_id"));
        s.setDutyPeriod(rs.getString("duty_period"));
        s.setVolunteerName(rs.getString("volunteer_name"));
        s.setZoneName(rs.getString("zone_name"));
        return s;
    }
}
```

---

# 13. Service Implementation

## Step 9 — Create VolunteerService.java

### Objective
Implement the service layer for volunteers: business validation, transaction management, error mapping, and `ServiceResult<T>` wrapping.

```java
package com.groupa13.disasterrelief.service.people;

import com.groupa13.disasterrelief.dao.people.VolunteerDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.ServiceResult;
import com.groupa13.disasterrelief.model.Volunteer;
import com.groupa13.disasterrelief.util.UserSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Service for volunteer domain operations.
 * Transaction boundary: each public method is one transaction.
 * Error messages follow Wave 0 convention: short, user-friendly.
 */
public class VolunteerService {

    private final VolunteerDao dao = new VolunteerDao();

    /**
     * Register a new volunteer.
     * Validates: name required, availability tsrange format if provided.
     *
     * @param name         required
     * @param contact      nullable
     * @param email        nullable
     * @param skills       nullable; parsed from comma-separated input
     * @param availability nullable tsrange string e.g. "[2026-07-09 08:00, 2026-07-09 20:00)"
     * @param zoneId       nullable FK
     */
    public ServiceResult<Volunteer> register(String name, String contact, String email,
                                              List<String> skills, String availability,
                                              Integer zoneId) {
        // Business validation
        if (name == null || name.isBlank())
            return ServiceResult.failure("Name is required");
        if (availability != null && !availability.isBlank() && !isValidTsrange(availability))
            return ServiceResult.failure("Availability must be a valid tsrange, e.g. [2026-07-09 08:00, 2026-07-20 18:00)");

        try {
            Volunteer created = Database.inTransaction(conn -> {
                int id = dao.insert(conn, name.trim(), blankToNull(contact), blankToNull(email),
                                    skills, blankToNull(availability), zoneId);
                return dao.findById(conn, id).orElseThrow(() ->
                    new RuntimeException("Volunteer not found after insert"));
            });
            return ServiceResult.success(created, "Volunteer registered successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Load all volunteers for the table view. */
    public ServiceResult<List<Volunteer>> getAllVolunteers() {
        try {
            List<Volunteer> list = Database.inTransaction(conn -> dao.findAll(conn));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Find a single volunteer by ID. */
    public ServiceResult<Volunteer> findById(int personId) {
        try {
            Optional<Volunteer> v = Database.inTransaction(conn -> dao.findById(conn, personId));
            if (v.isPresent()) return ServiceResult.success(v.get(), "");
            return ServiceResult.failure("Volunteer with ID " + personId + " not found");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Search volunteers by one or more skills.
     * Input is a comma-separated string from the UI field, e.g. "first_aid, driving".
     */
    public ServiceResult<List<Volunteer>> findBySkills(String skillsInput) {
        if (skillsInput == null || skillsInput.isBlank())
            return ServiceResult.failure("Enter at least one skill to search");
        List<String> skills = Arrays.stream(skillsInput.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .toList();
        if (skills.isEmpty())
            return ServiceResult.failure("Enter at least one skill to search");
        try {
            List<Volunteer> results = Database.inTransaction(conn -> dao.findBySkills(conn, skills));
            if (results.isEmpty())
                return ServiceResult.failure("No volunteers found with the specified skills");
            return ServiceResult.success(results, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Update an existing volunteer. Null fields = keep existing value. */
    public ServiceResult<Volunteer> update(int personId, String name, String contact,
                                            String email, List<String> skills,
                                            String availability, Integer zoneId) {
        if (availability != null && !availability.isBlank() && !isValidTsrange(availability))
            return ServiceResult.failure("Availability must be a valid tsrange, e.g. [2026-07-09 08:00, 2026-07-20 18:00)");
        try {
            Volunteer updated = Database.inTransaction(conn -> {
                boolean changed = dao.update(conn, personId, blankToNull(name), blankToNull(contact),
                                             blankToNull(email), skills, blankToNull(availability), zoneId);
                if (!changed) throw new RuntimeException("Volunteer with ID " + personId + " not found");
                return dao.findById(conn, personId).orElseThrow();
            });
            return ServiceResult.success(updated, "Volunteer updated successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Delete a volunteer. Their shifts are cascade-deleted. */
    public ServiceResult<Void> delete(int personId) {
        try {
            Database.inTransaction(conn -> {
                boolean deleted = dao.delete(conn, personId);
                if (!deleted) throw new RuntimeException("Volunteer with ID " + personId + " not found");
                return null;
            });
            return ServiceResult.success(null, "Volunteer removed successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Load all zones for the ComboBox in VolunteerController.
     * Returns a List of String formatted as "zone_id — zone_name".
     * TODO: Replace with Dev A's ZoneService.getAllZones() once available.
     */
    public List<String> getAllZonesForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> zones = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT zone_id, name FROM zones ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        zones.add(rs.getInt("zone_id") + " — " + rs.getString("name"));
                    }
                }
                return zones;
            });
        } catch (Exception e) {
            return new ArrayList<>(); // Return empty list on failure; controller shows no options
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    private <T> ServiceResult<T> mapException(Exception e) {
        if (e instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state == null) return ServiceResult.failure("Database error: " + sql.getMessage());
            return switch (state) {
                case "23502" -> ServiceResult.failure("Required field is missing");
                case "23503" -> ServiceResult.failure("Referenced zone does not exist");
                case "23505" -> ServiceResult.failure("A duplicate volunteer record already exists");
                default -> ServiceResult.failure("Database error: " + sql.getMessage());
            };
        }
        if (e.getCause() instanceof SQLException sql) return mapException(sql);
        return ServiceResult.failure(e.getMessage() != null ? e.getMessage() : "Unexpected error");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Basic tsrange format validation.
     * Acceptable: [YYYY-MM-DD HH:mm, YYYY-MM-DD HH:mm) or similar bracketed forms.
     */
    private boolean isValidTsrange(String s) {
        return s.matches("[\\[(].+,.+[\\])]");
    }
}
```

## Step 10 — Create AffectedPersonService.java

```java
package com.groupa13.disasterrelief.service.people;

import com.groupa13.disasterrelief.dao.people.AffectedPersonDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.ServiceResult;
import com.groupa13.disasterrelief.model.AffectedPerson;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for affected_persons domain operations.
 * Occupancy is NEVER updated by this service — the DB trigger handles it.
 */
public class AffectedPersonService {

    private final AffectedPersonDao dao = new AffectedPersonDao();

    /**
     * Register a new displaced person.
     * zoneId is used only for RLS set_config; pass null for admin.
     */
    public ServiceResult<AffectedPerson> register(String name, String contact, String email,
                                                   String medicalNeeds, Integer shelterId,
                                                   Integer zoneId) {
        if (name == null || name.isBlank())
            return ServiceResult.failure("Name is required");
        if (medicalNeeds != null && !medicalNeeds.isBlank()) {
            String trimmed = medicalNeeds.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
                return ServiceResult.failure("Medical needs must be valid JSON, e.g. {\"conditions\": [\"diabetes\"]}");
        }
        try {
            AffectedPerson created = Database.inTransaction(conn -> {
                if (zoneId != null) setZoneContext(conn, zoneId);
                return dao.insert(conn, name.trim(), blankToNull(contact), blankToNull(email),
                                  blankToNull(medicalNeeds), shelterId);
            });
            return ServiceResult.success(created, "Person registered successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Load all affected persons. zoneId = null means admin (all zones). */
    public ServiceResult<List<AffectedPerson>> getAllAffectedPersons(Integer zoneId) {
        try {
            List<AffectedPerson> list = Database.inTransaction(conn -> {
                if (zoneId != null) {
                    setZoneContext(conn, zoneId);
                    return dao.findByZone(conn, zoneId);
                }
                return dao.findAll(conn);
            });
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Find by person_id. */
    public ServiceResult<AffectedPerson> findById(int personId) {
        try {
            Optional<AffectedPerson> ap = Database.inTransaction(conn -> dao.findById(conn, personId));
            if (ap.isPresent()) return ServiceResult.success(ap.get(), "");
            return ServiceResult.failure("Person with ID " + personId + " not found");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Find all persons at a specific shelter. */
    public ServiceResult<List<AffectedPerson>> findByShelterId(int shelterId) {
        try {
            List<AffectedPerson> list = Database.inTransaction(conn -> dao.findByShelterId(conn, shelterId));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Update person details. Trigger handles occupancy on shelter_id change. */
    public ServiceResult<AffectedPerson> update(int personId, String name, String contact,
                                                 String email, String medicalNeeds,
                                                 Integer shelterId, Integer zoneId) {
        if (medicalNeeds != null && !medicalNeeds.isBlank()) {
            String trimmed = medicalNeeds.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
                return ServiceResult.failure("Medical needs must be valid JSON");
        }
        try {
            AffectedPerson updated = Database.inTransaction(conn -> {
                if (zoneId != null) setZoneContext(conn, zoneId);
                boolean changed = dao.update(conn, personId, blankToNull(name), blankToNull(contact),
                                             blankToNull(email), blankToNull(medicalNeeds), shelterId);
                if (!changed) throw new RuntimeException("Person with ID " + personId + " not found");
                return dao.findById(conn, personId).orElseThrow();
            });
            return ServiceResult.success(updated, "Person updated successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /** Delete a person. Trigger updates shelter occupancy automatically. */
    public ServiceResult<Void> delete(int personId, Integer zoneId) {
        try {
            Database.inTransaction(conn -> {
                if (zoneId != null) setZoneContext(conn, zoneId);
                boolean deleted = dao.delete(conn, personId);
                if (!deleted) throw new RuntimeException("Person with ID " + personId + " not found");
                return null;
            });
            return ServiceResult.success(null, "Person removed successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Load all shelters for the ComboBox.
     * Returns "shelter_id — shelter_name" strings.
     * TODO: Replace with Dev A's ShelterService.getAllShelters() once available.
     */
    public List<String> getAllSheltersForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT shelter_id, name FROM shelters ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getInt("shelter_id") + " — " + rs.getString("name"));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── RLS context helper ────────────────────────────────────────────────────

    /**
     * Sets the transaction-local zone context for RLS enforcement.
     * The 'true' third argument makes the setting transaction-local —
     * it resets automatically when the transaction ends.
     * This prevents Hikari connection pool from leaking zone identity.
     */
    private void setZoneContext(java.sql.Connection conn, int zoneId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT set_config('app.current_zone_id', ?, true)")) {
            ps.setString(1, String.valueOf(zoneId));
            ps.execute();
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    private <T> ServiceResult<T> mapException(Exception e) {
        if (e instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state == null) return ServiceResult.failure("Database error: " + sql.getMessage());
            return switch (state) {
                case "23502" -> ServiceResult.failure("Required field is missing");
                case "23503" -> ServiceResult.failure("Shelter does not exist");
                case "22007" -> ServiceResult.failure("Invalid date/time format");
                default -> ServiceResult.failure("Database error: " + sql.getMessage());
            };
        }
        if (e.getCause() instanceof SQLException) return mapException(e.getCause());
        return ServiceResult.failure(e.getMessage() != null ? e.getMessage() : "Unexpected error");
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
```

## Step 11 — Create VolunteerShiftService.java

```java
package com.groupa13.disasterrelief.service.people;

import com.groupa13.disasterrelief.dao.people.VolunteerShiftDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.ServiceResult;
import com.groupa13.disasterrelief.model.VolunteerShift;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for volunteer_shifts operations.
 * The GiST exclusion constraint (SQLState 23P01) is mapped to a user-friendly message.
 */
public class VolunteerShiftService {

    private final VolunteerShiftDao dao = new VolunteerShiftDao();

    /** Schedule a new shift. Returns failure if exclusion constraint fires. */
    public ServiceResult<VolunteerShift> createShift(int volunteerId, int zoneId, String dutyPeriod) {
        if (dutyPeriod == null || dutyPeriod.isBlank())
            return ServiceResult.failure("Duty period is required");
        if (!isValidTsrange(dutyPeriod))
            return ServiceResult.failure("Duty period must be a valid tsrange, e.g. [2026-07-09 08:00, 2026-07-09 20:00)");
        try {
            VolunteerShift shift = Database.inTransaction(conn -> dao.insert(conn, volunteerId, zoneId, dutyPeriod));
            return ServiceResult.success(shift, "Shift scheduled successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<List<VolunteerShift>> getAllShifts() {
        try {
            List<VolunteerShift> list = Database.inTransaction(conn -> dao.findAll(conn));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<VolunteerShift> findById(int shiftId) {
        try {
            Optional<VolunteerShift> s = Database.inTransaction(conn -> dao.findById(conn, shiftId));
            if (s.isPresent()) return ServiceResult.success(s.get(), "");
            return ServiceResult.failure("Shift with ID " + shiftId + " not found");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<List<VolunteerShift>> getShiftsByVolunteerId(int volunteerId) {
        try {
            List<VolunteerShift> list = Database.inTransaction(conn -> dao.findByVolunteerId(conn, volunteerId));
            return ServiceResult.success(list, "");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<VolunteerShift> updateShift(int shiftId, Integer zoneId, String dutyPeriod) {
        if (dutyPeriod != null && !dutyPeriod.isBlank() && !isValidTsrange(dutyPeriod))
            return ServiceResult.failure("Duty period must be a valid tsrange");
        try {
            VolunteerShift updated = Database.inTransaction(conn -> {
                boolean changed = dao.update(conn, shiftId, zoneId, dutyPeriod);
                if (!changed) throw new RuntimeException("Shift with ID " + shiftId + " not found");
                return dao.findById(conn, shiftId).orElseThrow();
            });
            return ServiceResult.success(updated, "Shift updated successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    public ServiceResult<Void> deleteShift(int shiftId) {
        try {
            Database.inTransaction(conn -> {
                boolean deleted = dao.delete(conn, shiftId);
                if (!deleted) throw new RuntimeException("Shift with ID " + shiftId + " not found");
                return null;
            });
            return ServiceResult.success(null, "Shift cancelled successfully");
        } catch (Exception e) {
            return mapException(e);
        }
    }

    /**
     * Load all volunteers for the VolunteerShiftController ComboBox.
     * TODO: Replace with VolunteerService.getAllVolunteers() combo helper.
     */
    public List<String> getAllVolunteersForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT person_id, name FROM volunteers ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getInt("person_id") + " — " + rs.getString("name"));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Load zones for the zone combo. */
    public List<String> getAllZonesForCombo() {
        try {
            return Database.inTransaction(conn -> {
                List<String> list = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT zone_id, name FROM zones ORDER BY name");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getInt("zone_id") + " — " + rs.getString("name"));
                    }
                }
                return list;
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── Error mapping ─────────────────────────────────────────────────────────

    private <T> ServiceResult<T> mapException(Exception e) {
        if (e instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state == null) return ServiceResult.failure("Database error: " + sql.getMessage());
            return switch (state) {
                case "23P01" -> ServiceResult.failure("Volunteer already has an overlapping shift for this time period");
                case "23502" -> ServiceResult.failure("Required field is missing");
                case "23503" -> ServiceResult.failure("Volunteer or zone does not exist");
                case "22007" -> ServiceResult.failure("Invalid date/time format. Use: yyyy-MM-dd HH:mm");
                default -> ServiceResult.failure("Database error: " + sql.getMessage());
            };
        }
        if (e.getCause() instanceof SQLException) return mapException(e.getCause());
        return ServiceResult.failure(e.getMessage() != null ? e.getMessage() : "Unexpected error");
    }

    private boolean isValidTsrange(String s) {
        return s.matches("[\\[(].+,.+[\\])]");
    }
}
```

---

# 14. Controller Implementation

## Step 12 — Wire VolunteerController.java

### What to remove
All comment-blocks wrapped in `/* ... */` that show the commented-out TODO service calls.  
The `errorLabel.setText("TODO: ...")` placeholder messages.

### What to retain
All `@FXML` field declarations. All `initialize()` column bindings. The `goBack()` method. `handleClear()`. All form-field reads.

### What to change
- `colSkills` `PropertyValueFactory`: change `"skills"` → `"skillsDisplay"`.
- Add `private final VolunteerService volunteerService = new VolunteerService();` field.
- Add `private final Map<String, Integer> zoneNameToId = new HashMap<>()` to track ComboBox selections.
- `initialize()`: load zone combo asynchronously; load volunteer list asynchronously.
- `handleAdd()`: call `volunteerService.register(...)`.
- `handleUpdate()`: call `volunteerService.update(...)`.
- `handleDelete()`: call `volunteerService.delete(...)`.
- `handleFind()`: call `volunteerService.findById(...)`.
- `handleSearchBySkill()`: call `volunteerService.findBySkills(...)`.

### Exact pattern for every service call (JavaFX Task)

```java
// Example: handleAdd
@FXML
public void handleAdd(ActionEvent event) {
    errorLabel.setText("");
    String name = nameField.getText();
    // ... read all fields ...
    List<String> skills = skillsField.getText().isBlank() ? List.of()
        : Arrays.stream(skillsField.getText().split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
    String availability = availabilityField.getText().isBlank() ? null : availabilityField.getText().trim();
    Integer zoneId = parseZoneId(zoneCombo.getValue()); // extract integer from "id — name"

    Task<ServiceResult<Volunteer>> task = new Task<>() {
        @Override
        protected ServiceResult<Volunteer> call() {
            return volunteerService.register(name, contact, email, skills, availability, zoneId);
        }
    };
    task.setOnSucceeded(e -> {
        ServiceResult<Volunteer> result = task.getValue();
        if (result.success()) {
            dataList.add(0, result.value());
            volunteerTable.setItems(dataList);
            handleClear(null);
        } else {
            errorLabel.setText(result.message());
        }
    });
    task.setOnFailed(e -> errorLabel.setText("Unexpected error: " + task.getException().getMessage()));
    new Thread(task) {{ setDaemon(true); }}.start();
}
```

### ComboBox loading in initialize()

```java
// In initialize():
Task<List<String>> loadZonesTask = new Task<>() {
    @Override protected List<String> call() { return volunteerService.getAllZonesForCombo(); }
};
loadZonesTask.setOnSucceeded(e -> zoneCombo.getItems().setAll(loadZonesTask.getValue()));

Task<ServiceResult<List<Volunteer>>> loadVolunteersTask = new Task<>() {
    @Override protected ServiceResult<List<Volunteer>> call() { return volunteerService.getAllVolunteers(); }
};
loadVolunteersTask.setOnSucceeded(e -> {
    ServiceResult<List<Volunteer>> result = loadVolunteersTask.getValue();
    if (result.success()) { dataList.setAll(result.value()); volunteerTable.setItems(dataList); }
    else errorLabel.setText(result.message());
});

new Thread(loadZonesTask) {{ setDaemon(true); }}.start();
new Thread(loadVolunteersTask) {{ setDaemon(true); }}.start();
```

### Helper method parseZoneId

```java
/** Extracts integer zone_id from a ComboBox item formatted as "3 — Wayanad North". */
private Integer parseZoneId(String selection) {
    if (selection == null || selection.isBlank()) return null;
    try { return Integer.parseInt(selection.split(" — ")[0].trim()); }
    catch (NumberFormatException e) { return null; }
}
```

### NEVER do in controllers
- Do NOT call `DriverManager`.
- Do NOT call DAO methods directly.
- Do NOT call `volunteerService.register(...)` on the JavaFX Application Thread directly (use `Task`).
- Do NOT write SQL.
- Do NOT catch `SQLException` (services catch and map them).
- Do NOT update `shelters.current_occupancy` anywhere.

## Step 13 — Wire AffectedPersonController.java

Same pattern. Key differences:
- Read `UserSession.getInstance().getRole()` and `getAssignedZoneId()` in `initialize()`.
- If role is `ZONE_COORDINATOR`, pass `zoneId` to service methods.
- `shelterCombo` is populated from `affectedPersonService.getAllSheltersForCombo()`.
- `handleRegister()` → `affectedPersonService.register(name, contact, email, medicalNeeds, shelterId, zoneId)`.
- `handleFindByShelter()` → parse `shelterId` from `shelterCombo.getValue()` → `affectedPersonService.findByShelterId(shelterId)`.
- Retain the existing JSON format validation in `handleRegister()`.

Zone context code in initialize:

```java
UserSession session = UserSession.getInstance();
Integer zoneId = null;
if (session.getRole() == UserRole.ZONE_COORDINATOR) {
    zoneId = session.getAssignedZoneId();
    // Store as field for use in all handler methods
    this.currentZoneId = zoneId;
}
```

Add field: `private Integer currentZoneId = null;`

## Step 14 — Wire VolunteerShiftController.java

Same pattern. Key points:
- `volunteerCombo` loaded from `shiftService.getAllVolunteersForCombo()`.
- `zoneCombo` loaded from `shiftService.getAllZonesForCombo()`.
- `handleCreate()` builds `dutyPeriod = "[" + dutyStart + ", " + dutyEnd + ")"` (existing logic, correct).
- Calls `shiftService.createShift(volunteerId, zoneId, dutyPeriod)`.
- On `23P01` SQLState error, `ServiceResult.message()` returns: "Volunteer already has an overlapping shift for this time period" — display this in `errorLabel`.
- `handleFindByVolunteer()` → parse volunteerId from `volunteerCombo.getValue()` → `shiftService.getShiftsByVolunteerId(volunteerId)`.

---

# 15. PostgreSQL/JDBC Type Mapping

## Complete type mapping reference

### TEXT[] (skills in volunteers)

```
UI TextField → controller splits by comma → List<String>
List<String> → VolunteerService.register() → VolunteerDao.insert()
VolunteerDao: conn.createArrayOf("text", skills.toArray(new String[0]))
              ps.setArray(paramIndex, array)
PostgreSQL: TEXT[] column

Reading:
ResultSet.getArray("skills") → (String[]) .getArray() → Arrays.asList()
Display: String.join(", ", list) via getSkillsDisplay()
```

**Null handling:** If skills is null or empty, bind as `conn.createArrayOf("text", new String[0])` (not SQL NULL — preserve an empty array rather than NULL for consistency with `@>` operator behavior).

### tsrange (availability in volunteers, duty_period in volunteer_shifts)

```
UI TextField (e.g. "[2026-07-09 08:00, 2026-07-09 20:00)") → String
String → service validates format → DAO
DAO: PGobject pgo = new PGobject(); pgo.setType("tsrange"); pgo.setValue(value);
     ps.setObject(paramIndex, pgo)

Reading: rs.getString("availability") or rs.getString("duty_period")
  → returns PostgreSQL's literal string representation
Display: directly as string in TableView column

Null: ps.setNull(paramIndex, Types.OTHER) if null
```

**Validation pattern (service layer):**
```java
private boolean isValidTsrange(String s) {
    // Matches: [start, end) or (start, end] or [start, end] etc.
    return s.matches("[\\[(].+,.+[\\])]");
}
```

### JSONB (medical_needs in affected_persons)

```
UI TextArea (raw JSON string) → String
String → service validates JSON wrapper ({...}) → DAO
DAO: PGobject pgo = new PGobject(); pgo.setType("jsonb"); pgo.setValue(json);
     ps.setObject(paramIndex, pgo)

Reading: rs.getString("medical_needs")
  → returns PostgreSQL's text representation of JSONB
Display: directly as string in TableView column

Null: ps.setNull(paramIndex, Types.OTHER) if null or blank
```

### TIMESTAMPTZ (registered_at in affected_persons)

```
PostgreSQL: TIMESTAMPTZ DEFAULT NOW()
Reading: rs.getString("registered_at::text") — display string
Java: stored as String for display only (OffsetDateTime not needed since we don't
      parse it in Java for people domain)
```

### INTEGER FK (shelterId, zoneId)

```
Nullable: ps.setNull(index, Types.INTEGER) or ps.setInt(index, value)
Reading: int raw = rs.getInt("col_name"); rs.wasNull() ? null : raw
```

---

# 16. Testing Strategy

## Test architecture

- Use JUnit 5 (`@Test`, `@BeforeAll`, `@AfterEach`).
- Use Testcontainers `PostgreSQLContainer` to spin up a real PostgreSQL 15 instance.
- Apply all migrations V001–V004 via the `MigrationTestSupport` class provided by Dev A before each test class.
- Tests must be independent: `@AfterEach` truncates affected tables.
- No test relies on pre-existing data or test execution order.

## Test package structure

```
src/test/java/com/groupa13/disasterrelief/people/
  VolunteerDaoTest.java
  AffectedPersonDaoTest.java
  VolunteerShiftDaoTest.java
  VolunteerServiceTest.java
  AffectedPersonServiceTest.java
  VolunteerShiftServiceTest.java
```

## Detailed test specifications

### VolunteerDaoTest.java

**Test 1: `testInsertAndFindById_basic`**
- Setup: Insert one volunteer with name="Alice", skills=["first_aid"], availability="[2026-07-09 08:00, 2026-07-09 20:00)"
- Assert: `findById` returns non-empty; `name` is "Alice"; `skills` contains "first_aid"; `availability` is not null
- Why: Proves basic INSERT + SELECT round-trip works with PostgreSQL INHERITS

**Test 2: `testInsertWithNullAvailability`**
- Setup: Insert volunteer with null availability
- Assert: `findById` returns non-empty; `availability` is null
- Why: Nullable tsrange column must work

**Test 3: `testInsertWithEmptySkills`**
- Setup: Insert volunteer with empty skills list
- Assert: `findById` returns non-empty; `skills` is empty list (not null)
- Why: `TEXT[]` with empty array must not cause NPE

**Test 4: `testFindBySkills_singleSkill`**
- Setup: Insert volunteer with skills=["first_aid","driving"]
- Input: `findBySkills(["first_aid"])`
- Assert: result contains the volunteer
- Why: `@>` operator with single-element array

**Test 5: `testFindBySkills_multiSkill`**
- Input: `findBySkills(["first_aid","driving"])`
- Assert: returns volunteer (has both)

**Test 6: `testFindBySkills_noMatch`**
- Input: `findBySkills(["translation"])` on above data
- Assert: empty list
- Why: Proves non-matching array is rejected

**Test 7: `testUpdate_partialFields`**
- Setup: Insert volunteer
- Action: `update(id, null, null, null, null, "[new tsrange]", null)`
- Assert: `findById` shows only availability changed; other fields unchanged
- Why: COALESCE-based partial update

**Test 8: `testDelete`**
- Setup: Insert volunteer
- Action: `delete(id)`
- Assert: `findById` returns empty
- Why: Basic deletion

### AffectedPersonDaoTest.java

**Test 9: `testInsert_updatesOccupancy`**
- Setup: Create a shelter with capacity=10, current_occupancy=0
- Action: Insert affected person with shelterId=that shelter
- Assert: `SELECT current_occupancy FROM shelters WHERE shelter_id=?` returns 1
- Why: Proves `trg_auto_update_shelter_occupancy` fires on INSERT

**Test 10: `testDelete_updatesOccupancy`**
- Setup: Insert two persons at shelter (occupancy becomes 2)
- Action: Delete one
- Assert: occupancy is 1
- Why: Trigger fires on DELETE

**Test 11: `testUpdate_shelterReassignment_updatesOccupancy`**
- Setup: shelter A (1 person), shelter B (0 persons)
- Action: UPDATE shelter_id from A to B
- Assert: A occupancy = 0, B occupancy = 1
- Why: Trigger fires on UPDATE OF shelter_id; both old and new shelter updated

**Test 12: `testInsert_invalidJsonb`**
- Action: Pass invalid JSON string like `"not-json"` — but since the controller validates before calling the DAO, test directly passing the invalid value to the DAO
- Expect: PostgreSQL raises exception (JSONB validation at DB level)
- Why: DB JSONB type rejects non-JSON values

**Test 13: `testFindByShelterId`**
- Setup: Insert 2 persons at shelter A, 1 at shelter B
- Assert: `findByShelterId(A)` returns 2, `findByShelterId(B)` returns 1

**Test 14: `testFindByZone`**
- Setup: Create zone with primary_shelter_id; insert persons at that shelter
- Assert: `findByZone(zoneId)` returns only those persons

### VolunteerShiftDaoTest.java

**Test 15: `testInsert_successfulShift`**
- Action: Insert a shift for volunteer 1 in zone 1, duty period "[2026-07-09 08:00, 2026-07-09 20:00)"
- Assert: Returns VolunteerShift with non-zero shiftId; `findById` confirms it

**Test 16: `testInsert_overlappingShift_failsWithExclusionConstraint`**
- Setup: Insert shift for volunteer 1: "[2026-07-09 08:00, 2026-07-09 20:00)"
- Action: Insert second shift for same volunteer: "[2026-07-09 12:00, 2026-07-10 08:00)" (overlaps)
- Assert: `SQLException` thrown with `getSQLState() == "23P01"`
- Why: Proves exclusion constraint fires correctly

**Test 17: `testInsert_adjacentShift_succeeds`**
- Setup: Insert shift for volunteer 1: "[2026-07-09 08:00, 2026-07-09 20:00)"
- Action: Insert second shift: "[2026-07-09 20:00, 2026-07-10 08:00)" (adjacent, not overlapping)
- Assert: No exception; second shift inserted
- Why: Adjacent ranges do NOT overlap in PostgreSQL tsrange semantics (half-open intervals)

**Test 18: `testInsert_differentVolunteer_overlappingTime_succeeds`**
- Setup: Insert shift for volunteer 1; insert shift for volunteer 2 at same time
- Assert: No exception (exclusion is per-volunteer)
- Why: Constraint only fires for same volunteer_id

**Test 19: `testFindByVolunteerId`**
- Setup: Insert 2 shifts for volunteer 1, 1 shift for volunteer 2
- Assert: `findByVolunteerId(1)` returns 2, `findByVolunteerId(2)` returns 1

**Test 20: `testDelete`**
- Setup: Insert shift
- Action: `delete(shiftId)`
- Assert: `findById` returns empty; no cascaded effects on volunteer

### Service-level tests

**Test 21: `testRegisterVolunteer_missingName_returnsFailure`**
- Input: `volunteerService.register("", ...)` 
- Assert: `result.success() == false`, `result.message()` contains "Name is required"

**Test 22: `testRegisterVolunteer_invalidTsrange_returnsFailure`**
- Input: availability = "not-a-range"
- Assert: failure with tsrange message

**Test 23: `testCreateShift_overlapping_returnsFriendlyMessage`**
- Setup: Register volunteer; create first shift
- Action: Create second overlapping shift
- Assert: `result.success() == false`; `result.message()` == "Volunteer already has an overlapping shift for this time period"
- Why: Tests that SQLState 23P01 is correctly mapped to friendly message

**Test 24: `testRegisterAffectedPerson_invalidJson_returnsFailure`**
- Input: medicalNeeds = "not-json"
- Assert: failure; service validation catches it before DB

**Test 25: `testDeleteAffectedPerson_updatesOccupancy`**
- Full integration: register person at shelter → verify occupancy → delete → verify occupancy = 0
- Why: Integration test spanning service → DAO → trigger

---

# 17. Acceptance Tests

The `BACKEND_IMPLEMENTATION_PLAN.md` Dev B acceptance criteria are:

### AC-1: Affected-person insert, reassignment, and deletion update occupancy correctly

**What it means:** The trigger `trg_auto_update_shelter_occupancy` fires correctly on all three operations.

**How to test:**
1. Create shelter A (capacity=10), shelter B (capacity=10) via V002 seed data or direct INSERT in test.
2. INSERT affected person at shelter A. Query: `SELECT current_occupancy FROM shelters WHERE shelter_id=A_ID`. **Expected: 1**.
3. UPDATE that person's shelter_id to shelter B. Query both shelters. **Expected: A=0, B=1**.
4. DELETE the person. Query shelter B. **Expected: B=0**.

**Test file:** `AffectedPersonDaoTest.java` — tests 9, 10, 11.

**Passing:** All three assertions pass without any Java code setting `current_occupancy`.

---

### AC-2: Invalid JSONB and malformed timestamp ranges fail safely

**What it means:** The service layer catches invalid JSON and tsrange before they reach the DB; when they do reach the DB, the DB rejects them without exposing raw errors.

**How to test:**
1. `affectedPersonService.register("Alice", null, null, "INVALID", null, null)` → **Expected: ServiceResult.success=false, friendly message**.
2. `volunteerService.register("Bob", null, null, null, "BADRANGE", null)` → **Expected: failure with tsrange message**.
3. Directly call DAO with `PGobject(type="jsonb", value="not-json")` → **Expected: SQLException thrown at DB level**.
4. Directly call DAO with `PGobject(type="tsrange", value="bad")` → **Expected: SQLException at DB level**.

**Test files:** `VolunteerServiceTest.java` — tests 22; `AffectedPersonServiceTest.java` — test 24; `VolunteerDaoTest.java`; `AffectedPersonDaoTest.java` — test 12.

---

### AC-3: Two overlapping shifts for one volunteer fail with the exclusion constraint; adjacent shifts succeed

**What it means:** The GiST exclusion constraint prevents double-booking and does not falsely reject adjacent (non-overlapping) shifts.

**How to test:**
1. `shiftService.createShift(volunteerId, zoneId, "[2026-07-09 08:00, 2026-07-09 20:00)")` → success.
2. `shiftService.createShift(volunteerId, zoneId, "[2026-07-09 12:00, 2026-07-10 08:00)")` → **Expected: failure, message = "Volunteer already has an overlapping shift for this time period"**.
3. `shiftService.createShift(volunteerId, zoneId, "[2026-07-09 20:00, 2026-07-10 08:00)")` → **Expected: success** (adjacent, not overlapping).

**Test files:** `VolunteerShiftDaoTest.java` — tests 16, 17; `VolunteerShiftServiceTest.java` — test 23.

---

### AC-4: Skill-array search and zone assignments return only correct rows

**What it means:** `TEXT[] @>` operator correctly filters volunteers by skill; zone assignment returns volunteers matching that zone.

**How to test:**
1. Insert volunteer A with skills=["first_aid","driving"]; volunteer B with skills=["cooking"].
2. `volunteerService.findBySkills("first_aid")` → **Expected: only volunteer A**.
3. `volunteerService.findBySkills("cooking")` → **Expected: only volunteer B**.
4. `volunteerService.findBySkills("first_aid,driving")` → **Expected: only volunteer A** (has both).
5. Query volunteers with `assigned_zone=1` via `findAll()` → filter in assertion.

**Test files:** `VolunteerDaoTest.java` — tests 4, 5, 6; `VolunteerServiceTest.java`.

---

# 18. Git / Commit / PR Plan

## Branch
`feature/dev-b`

## Commit sequence

| # | Commit message | Contents |
|---|---|---|
| 1 | `feat(people): add Person base model; refactor Volunteer and AffectedPerson to extend Person` | `Person.java`, updated `Volunteer.java`, updated `AffectedPerson.java`, updated `VolunteerShift.java` |
| 2 | `feat(people): add V004 people schema migration` | `V004__people_schema.sql` only |
| 3 | `feat(people): add VolunteerDao with TEXT[] and tsrange JDBC handling` | `VolunteerDao.java` |
| 4 | `feat(people): add AffectedPersonDao with JSONB binding and zone filtering` | `AffectedPersonDao.java` |
| 5 | `feat(people): add VolunteerShiftDao with GiST exclusion constraint handling` | `VolunteerShiftDao.java` |
| 6 | `feat(people): add VolunteerService, AffectedPersonService, VolunteerShiftService` | All three service classes |
| 7 | `feat(people): wire VolunteerController, AffectedPersonController, VolunteerShiftController to services` | All three controllers |
| 8 | `test(people): add integration tests for people domain DAOs and services` | All test files |

## PR checklist before opening PR
- [ ] All SQL is parameterized (no concatenation).
- [ ] Transaction boundaries are in service layer only.
- [ ] `./gradlew clean test` passes from a clean clone (no prior DB state).
- [ ] Only owned files are changed.
- [ ] No Dev A migration files (V001–V003) were edited.
- [ ] No Dev C files (UserRole, UserSession, Utility, auth/**) were edited.
- [ ] All TODO comments for Dev A service integration are present and clear.
- [ ] PR description states: which migration was added, which tables, which tests pass.

## PR review checklist items to provide reviewers
- SQL parameterized? ✓
- Transaction in service layer? ✓
- DB function/trigger logic NOT replicated in Java? ✓ (occupancy trigger)
- Exclusion constraint handled via exception mapping, not client-side check? ✓
- Negative tests present? ✓

## Merge order
Dev A merges first (Wave 1 baseline). Dev B's PR opens against `main` after Dev A merges. Dev C's V005–V008 open after Dev B merges.

---

# 19. Dependency and Blocking Map

```
BLOCKED UNTIL:
  Dev A merges: build.gradle (JDBC, Testcontainers, JUnit 5), 
                docker-compose.yml, 
                config/application.properties.example,
                V001–V003,
                Database.java (with DataSource + inTransaction()),
                ServiceResult<T> record,
                MigrationTestSupport test utility

THEN Dev B performs:
  Phase 0: local checkout, docker up, verify Dev A tests pass
  Phase 1: write V004, apply migrations V001–V004, verify schema
  Phase 2: refactor models (Person, Volunteer, AffectedPerson)
  Phase 3: write DAOs (VolunteerDao, AffectedPersonDao, VolunteerShiftDao)
  Phase 4: write services
  Phase 5: wire controllers
  Phase 6: write tests

PROVIDES to rest of team:
  'people' table      → Dev C's app_users FK
  'volunteers' table  → Dev C's audit trigger attachment
  'affected_persons'  → Dev C's audit trigger + RLS policies
  'volunteer_shifts'  → Dev C's audit trigger attachment
  Person IDs as int   → Dev C's app_users FK type
  VolunteerService.getAllVolunteers() → VolunteerShiftController combo

WHICH UNBLOCKS:
  Dev C: V007 app_users table creation (needs people FK target)
  Dev C: V006 audit trigger attachment on volunteers, affected_persons, volunteer_shifts
  Dev C: RLS policy testing on affected_persons (zone context)
```

---

# 20. Master Execution Checklist

```
PHASE 0 — Preconditions
[ ] Confirm Dev A has merged Wave 1 baseline (V001–V003, Database.java, ServiceResult, etc.)
[ ] Pull main → create branch feature/dev-b
[ ] docker compose up -d → confirm healthy PostgreSQL 15 container
[ ] Copy config/application.properties.example → config/application.properties (fill credentials)
[ ] ./gradlew test → all Dev A tests pass from clean checkout

PHASE 1 — Database Migration
[ ] Create database/migrations/V004__people_schema.sql with exact SQL from Step 1
[ ] Run Flyway: confirm V001–V004 applied cleanly (no errors)
[ ] Run SQL verification queries (see Step 1 Verification section)
[ ] Confirm people, volunteers, affected_persons, volunteer_shifts tables exist
[ ] Confirm INHERITS relationship: pg_inherits shows people→volunteers, people→affected_persons
[ ] Confirm exclusion constraint exists on volunteer_shifts
[ ] Confirm trg_auto_update_shelter_occupancy trigger exists on affected_persons
[ ] Confirm GIN index on volunteers.skills, GiST index on volunteers.availability
[ ] Commit V004: git add database/migrations/V004__people_schema.sql; git commit -m "feat(people): add V004 people schema migration"

PHASE 2 — Models
[ ] Create src/main/java/com/groupa13/disasterrelief/model/Person.java (Step 2)
[ ] Refactor Volunteer.java to extend Person, change skills to List<String> (Step 3)
[ ] Refactor AffectedPerson.java to extend Person (Step 4)
[ ] Review VolunteerShift.java — add JDBC comment (Step 5)
[ ] Compile check: ./gradlew compileJava (no errors)
[ ] Commit models: git commit -m "feat(people): add Person base model; refactor Volunteer and AffectedPerson"

PHASE 3 — DAOs
[ ] Create src/main/java/com/groupa13/disasterrelief/dao/people/VolunteerDao.java (Step 6)
[ ] Create src/main/java/com/groupa13/disasterrelief/dao/people/AffectedPersonDao.java (Step 7)
[ ] Create src/main/java/com/groupa13/disasterrelief/dao/people/VolunteerShiftDao.java (Step 8)
[ ] Compile check: ./gradlew compileJava
[ ] Commit DAOs: git commit -m "feat(people): add VolunteerDao, AffectedPersonDao, VolunteerShiftDao"

PHASE 4 — Services
[ ] Create src/main/java/com/groupa13/disasterrelief/service/people/VolunteerService.java (Step 9)
[ ] Create src/main/java/com/groupa13/disasterrelief/service/people/AffectedPersonService.java (Step 10)
[ ] Create src/main/java/com/groupa13/disasterrelief/service/people/VolunteerShiftService.java (Step 11)
[ ] Compile check: ./gradlew compileJava
[ ] Commit services: git commit -m "feat(people): add VolunteerService, AffectedPersonService, VolunteerShiftService"

PHASE 5 — Controller Wiring
[ ] Modify VolunteerController.java (Step 12): remove TODOs, add Task patterns, fix PropertyValueFactory
[ ] Modify AffectedPersonController.java (Step 13): add zone filtering, Task patterns, shelter combo load
[ ] Modify VolunteerShiftController.java (Step 14): add Task patterns, volunteer/zone combo load
[ ] Compile check: ./gradlew compileJava
[ ] Run application: ./gradlew run → navigate to Volunteer, AffectedPerson, VolunteerShift screens → confirm they load without error
[ ] Commit controllers: git commit -m "feat(people): wire people controllers to service layer"

PHASE 6 — Tests
[ ] Create src/test/java/com/groupa13/disasterrelief/people/VolunteerDaoTest.java (all 8 DAO tests)
[ ] Create src/test/java/com/groupa13/disasterrelief/people/AffectedPersonDaoTest.java (tests 9–14)
[ ] Create src/test/java/com/groupa13/disasterrelief/people/VolunteerShiftDaoTest.java (tests 15–20)
[ ] Create src/test/java/com/groupa13/disasterrelief/people/VolunteerServiceTest.java (tests 21–25)
[ ] Create src/test/java/com/groupa13/disasterrelief/people/AffectedPersonServiceTest.java
[ ] Create src/test/java/com/groupa13/disasterrelief/people/VolunteerShiftServiceTest.java
[ ] Run: ./gradlew test → ALL tests pass (Dev A + Dev B)
[ ] Commit tests: git commit -m "test(people): add integration tests for people domain"

PHASE 7 — Verification
[ ] ./gradlew clean test → passes from clean state
[ ] Manually log in (with seed account after Dev C merges, or with dev credentials) → navigate all 3 screens
[ ] Register volunteer with skills → confirm appears in table
[ ] Search by skill → confirm only matching volunteers shown
[ ] Register affected person → confirm shelter occupancy increments in DB
[ ] Delete person → confirm occupancy decrements
[ ] Schedule shift → succeeds
[ ] Schedule overlapping shift → UI shows "Volunteer already has an overlapping shift..."
[ ] Schedule adjacent shift → succeeds

PHASE 8 — PR
[ ] git rebase origin/main (or merge main into feature/dev-b)
[ ] ./gradlew clean test → still passes
[ ] Confirm only owned files changed: git diff --name-only main
[ ] Open PR: feature/dev-b → main
[ ] PR description includes: V004 migration summary, tables created, trigger created, tests passing
[ ] Request review from Dev A (inventory/shelter interactions) and Dev C (will depend on tables)
```

---

# 21. Definition of Done

**Dev B is complete when ALL of the following are true:**

1. `./gradlew clean test` passes from a fresh clone with Docker PostgreSQL running.
2. Migration V004 applies cleanly after V001–V003 with no errors.
3. `people`, `volunteers`, `affected_persons`, `volunteer_shifts` tables exist in PostgreSQL with correct schemas, constraints, indexes, and trigger.
4. `INHERITS` relationship is verified in `pg_inherits`.
5. GiST exclusion constraint exists on `volunteer_shifts`.
6. `trg_auto_update_shelter_occupancy` exists and fires correctly (test 9–11 pass).
7. All 25+ unit and integration tests in `src/test/java/.../people/` pass on a real PostgreSQL 15 Testcontainer.
8. `VolunteerController`, `AffectedPersonController`, and `VolunteerShiftController` contain NO placeholder `errorLabel.setText("TODO: ...")` messages.
9. All three controllers use JavaFX `Task` for every service call.
10. No controller calls any DAO directly, uses `DriverManager`, or contains SQL.
11. `Person.java` base class exists; `Volunteer extends Person`, `AffectedPerson extends Person`.
12. `Volunteer.skills` is `List<String>`; `getSkillsDisplay()` exists for table column display.
13. PR is open against `main`; only Dev B-owned files are in the diff.
14. PR description documents: migration V004, trigger, 4 tables, all acceptance tests.
15. No other developer's files were modified.

---

# 22. Dev B Handoff Package

## What Dev B delivers to the team

### To Dev C specifically:

| Deliverable | Detail | Dev C needs it for |
|---|---|---|
| `people` table | Created in V004; people(person_id, name, contact, email) | `app_users.person_id FK → people(person_id)` in V007 |
| `volunteers` table | `volunteers INHERITS people`; person_id is PK | Audit trigger in V006 on `volunteers` |
| `affected_persons` table | `affected_persons INHERITS people`; shelter_id FK | Audit trigger in V006; RLS on affected persons by zone |
| `volunteer_shifts` table | shift_id PK, volunteer_id FK, zone_id FK, duty_period tsrange | Audit trigger in V006 |
| Person IDs as `int` | All `person_id` fields are integers | `app_users.person_id INTEGER REFERENCES people(person_id)` |
| `VolunteerService.getAllVolunteers()` | Returns `ServiceResult<List<Volunteer>>` | Can be used in cross-domain integrations |
| `AffectedPersonService.getAllAffectedPersons(Integer zoneId)` | Zone-scoped list with RLS `set_config` pattern | Dev C's RLS testing |

### Known assumptions / notes for Dev C:
1. `people`, `volunteers`, `affected_persons` all have `person_id SERIAL PRIMARY KEY`. They do NOT share a single sequence. `app_users` can safely reference `people(person_id)` since `people` rows are only inserted via the base table. However: be aware that `INSERT INTO volunteers` inserts into BOTH `volunteers` AND `people` (that's how INHERITS works). An `app_users` row linked to `volunteers.person_id` is correct because `volunteers.person_id` IS a `people.person_id`.
2. The zone context helper (`set_config('app.current_zone_id', ?, true)`) is already implemented in `AffectedPersonService`. Dev C's RLS policies should use `current_setting('app.current_zone_id', true)`.
3. `trg_auto_update_shelter_occupancy` fires ONLY on `affected_persons`. Dev C's audit trigger (`trg_audit_log`) should be attached SEPARATELY to volunteers, affected_persons, and volunteer_shifts — it should NOT interfere with the occupancy trigger.

### To Dev A:
- Dev B's `VolunteerService.getAllZonesForCombo()` and `VolunteerShiftService.getAllZonesForCombo()` contain TODO comments requesting Dev A to expose `ZoneService.getAllZones()`. When Dev A provides that, Dev B (or whoever owns the next iteration) should replace the inline SQL with the proper service call.
- Dev B's `AffectedPersonService.getAllSheltersForCombo()` similarly needs Dev A's `ShelterService.getAllShelters()`.

---

# Stop Conditions for the Lower Model

**Stop immediately and report to the developer if:**

1. V001 does not contain `CREATE EXTENSION IF NOT EXISTS btree_gist` — the exclusion constraint will fail without it.
2. `Database.java` does not provide `inTransaction()` with a `Connection`-receiving functional interface — the DAO pattern cannot work.
3. `ServiceResult<T>` does not exist as a record with `boolean success()`, `T value()`, `String message()`.
4. Any Dev A migration file (V001, V002, V003) is missing or fails to apply — do NOT attempt to fix it; it is owned by Dev A.
5. The `Utility.java` class signature changes in a way that breaks `changeScene()` calls — report to Dev C.
6. The `shelters` table does not have a `current_occupancy` column — V004's trigger will fail.
7. The `zones` table does not have a `primary_shelter_id` column — the `findByZone` query will fail.
8. `build.gradle` does not include test sourceset with `src/test/java` — add only the test sourceset declaration without modifying other sections; if in doubt, stop and request Dev A to add it.
9. A `UserSession` method that the controllers rely on (`getAssignedZoneId()`, `getRole()`) changes signature — stop and consult Dev C.
10. Implementing the documented behavior (PostgreSQL INHERITS) would require modifying V001 or V002 owned by Dev A — request the change through Dev A.
