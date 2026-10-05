# Dev B Wave 2 — Comprehensive Study Guide
## Disaster Relief Resource Allocation System (DRAS) — People / Volunteer Domain

> **Status as of 2026-10-03:** Branch `feature/dev-b` (5 local commits, `451b0a4` to `af0a994`). Not yet pushed or merged.
> Origin/main is 2 commits ahead (Dev A Wave 2 + Dev C Wave 2 and 3 at `e5c01c0`). **No merge conflicts detected.**

---

## 1. Why Dev B Exists — Strategic Context

The system backend is split into waves so no one developer blocks another:

```
Wave 0: Team agrees on contracts (naming, types, error style) — NO code
Wave 1: Dev A builds the platform baseline (Docker, Flyway V001-V003, HikariCP, Testcontainers)
Wave 2: Dev B builds the People domain (V004, models, DAOs, services, controllers, tests)
Wave 3: Dev C adds auth, allocation, RLS, audit, analytics (V005-V008)
Wave 4: Full integration and hardening by all three
```

**Dev B's mandate:** Own everything that represents a _person_ in the system — volunteers, displaced persons, and volunteer scheduling — at the database, Java model, business logic, and UI layers.

---

## 2. Architecture Overview

```
JavaFX Controller
      |  (JavaFX Task — background thread — NEVER blocks UI)
      v
Service Layer        (validates input, opens transaction, maps SQLState to message)
      |  (Database.inTransaction(conn -> ...))
      v
DAO Layer            (parameterized SQL only — receives Connection from service)
      |  (HikariCP connection pool)
      v
PostgreSQL 15        (tables, INHERITS, triggers, GiST exclusion, TEXT[], JSONB, tsrange)
```

### Iron Rules (non-negotiable)

| Layer | Rule |
|---|---|
| Controller | NO SQL. NO DAO calls. NO `DriverManager`. ALL service calls in `Task` on daemon thread. |
| Service | One public method = one transaction. Map `SQLException` to `ServiceResult`. Never set `current_occupancy`. |
| DAO | Receives `Connection` from service. 100% prepared statements. Never opens/closes its own connection. |
| Database | Occupancy owned by `trg_auto_update_shelter_occupancy`. Exclusion constraint owns shift conflict detection. |

---

## 3. Database Migration: V004__people_schema.sql

**File:** `database/migrations/V004__people_schema.sql`
**Dependencies:** V001 (`btree_gist` extension), V002 (`shelters`, `zones` tables)

### 3.1 The Four Tables

#### `people` — Superclass

```sql
CREATE TABLE people (
    person_id   SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    contact     VARCHAR(20),
    email       VARCHAR(100)
);
```

- Base table for the ISA hierarchy.
- `SELECT * FROM people` returns rows from all three tables combined (PostgreSQL INHERITS behavior).

#### `volunteers` — Inherits people

```sql
CREATE TABLE volunteers (
    person_id       SERIAL PRIMARY KEY,   -- own independent sequence
    skills          TEXT[],               -- PostgreSQL array; GIN-indexed
    availability    TSRANGE,              -- nullable; GiST-indexed
    assigned_zone   INTEGER REFERENCES zones(zone_id) ON DELETE SET NULL
) INHERITS (people);
```

- `person_id` re-declared as `SERIAL PRIMARY KEY` — gives volunteers their own sequence.
- `INHERITS (people)` causes columns `name`, `contact`, `email` to appear automatically.
- **No FK between `volunteers.person_id` and `people.person_id`** — the inheritance IS the mechanism.
- Indexes: GIN on `skills` (for `@>` containment), GiST on `availability`, B-tree on `assigned_zone`.

#### `affected_persons` — Inherits people

```sql
CREATE TABLE affected_persons (
    person_id       SERIAL PRIMARY KEY,
    medical_needs   JSONB,                -- nullable; GIN-indexed
    shelter_id      INTEGER REFERENCES shelters(shelter_id) ON DELETE SET NULL,
    registered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
) INHERITS (people);
```

- Changing `shelter_id` fires `trg_auto_update_shelter_occupancy` automatically.
- Java **never** writes `shelters.current_occupancy`.

#### `volunteer_shifts` — Separate (not inheriting)

```sql
CREATE TABLE volunteer_shifts (
    shift_id        SERIAL PRIMARY KEY,
    volunteer_id    INTEGER NOT NULL REFERENCES volunteers(person_id) ON DELETE CASCADE,
    zone_id         INTEGER NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    duty_period     TSRANGE NOT NULL,
    EXCLUDE USING GIST (volunteer_id WITH =, duty_period WITH &&)
);
```

- **GiST Exclusion Constraint:** Prevents any two rows with the same `volunteer_id` AND overlapping `duty_period`.
- Requires `btree_gist` (from V001) to combine `=` (btree equality) with `&&` (GiST range overlap) in a single GiST index.
- Fires `SQLException` with SQLState `23P01` on violation.

### 3.2 The Occupancy Trigger

```sql
CREATE OR REPLACE FUNCTION fn_update_shelter_occupancy()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- INSERT: update new shelter occupancy
    -- DELETE: update old shelter occupancy
    -- UPDATE OF shelter_id: update BOTH old AND new shelter
    -- Uses COUNT(*) — always accurate regardless of concurrent changes
    RETURN NULL; -- AFTER trigger; return value ignored
END; $$;

CREATE TRIGGER trg_auto_update_shelter_occupancy
    AFTER INSERT OR DELETE OR UPDATE OF shelter_id
    ON affected_persons
    FOR EACH ROW
    EXECUTE FUNCTION fn_update_shelter_occupancy();
```

**Why COUNT(\*) instead of +1/-1?** Concurrent inserts/deletes can race; COUNT is always accurate regardless of ordering.

---

## 4. Java Model Layer

### 4.1 Class Hierarchy

```
abstract Person
    +-- Volunteer
    +-- AffectedPerson

VolunteerShift  (standalone — not part of inheritance)
```

### 4.2 Person.java (abstract base)

**File:** `src/main/java/com/groupa13/disasterrelief/model/Person.java`

```java
public abstract class Person {
    protected int personId;
    protected String name;
    protected String contact;
    protected String email;

    protected Person() {}
    protected Person(int personId, String name, String contact, String email) { ... }
    // getters, setters, toString() returns "personId — name"
}
```

### 4.3 Volunteer.java (extends Person)

**File:** `src/main/java/com/groupa13/disasterrelief/model/Volunteer.java`

| Java Field | DB Column | Type | Notes |
|---|---|---|---|
| `List<String> skills` | `skills` | `TEXT[]` | Bound with `conn.createArrayOf("text", ...)` |
| `String availability` | `availability` | `TSRANGE` | Stored as literal string e.g. `"[2026-07-09 08:00, 2026-07-09 20:00)"` |
| `Integer assignedZone` | `assigned_zone` | `INTEGER FK` | Nullable; use `getInt()` + `wasNull()` pattern |
| `String zoneName` | (joined) | — | From LEFT JOIN `zones`, not a DB column |

Special method: `getSkillsDisplay()` returns `String.join(", ", skills)` for JavaFX table binding.

> **Critical:** The `colSkills` table column must bind to `"skillsDisplay"`, NOT `"skills"` (which returns `List<String>` and renders as `[first_aid, driving]`).

### 4.4 AffectedPerson.java (extends Person)

**File:** `src/main/java/com/groupa13/disasterrelief/model/AffectedPerson.java`

| Java Field | DB Column | Type | Notes |
|---|---|---|---|
| `String medicalNeeds` | `medical_needs` | `JSONB` | Raw JSON text. Bound with `PGobject("jsonb")` |
| `Integer shelterId` | `shelter_id` | `INTEGER FK` | Nullable |
| `String registeredAt` | `registered_at` | `TIMESTAMPTZ` | Stored as display string only |
| `String shelterName` | (joined) | — | From LEFT JOIN `shelters` |

### 4.5 VolunteerShift.java

**File:** `src/main/java/com/groupa13/disasterrelief/model/VolunteerShift.java`

| Java Field | DB Column | Notes |
|---|---|---|
| `int shiftId` | `shift_id` | PK |
| `int volunteerId` | `volunteer_id` | FK to volunteers |
| `int zoneId` | `zone_id` | FK to zones |
| `String dutyPeriod` | `duty_period` | TSRANGE as literal string |
| `String volunteerName` | (joined) | Display only |
| `String zoneName` | (joined) | Display only |

---

## 5. JDBC Type Mapping Cheat Sheet

### TEXT[] (skills)

```java
// Writing
Array arr = conn.createArrayOf("text", skills.toArray(new String[0]));
ps.setArray(index, arr);
// For null/empty — always bind empty array, NOT SQL NULL
ps.setArray(index, conn.createArrayOf("text", new String[0]));

// Reading
Array arr = rs.getArray("skills");
List<String> skills = (arr != null)
    ? new ArrayList<>(Arrays.asList((String[]) arr.getArray()))
    : new ArrayList<>();

// Querying (GIN containment search)
// WHERE skills @> ?
ps.setArray(1, conn.createArrayOf("text", skillsList.toArray(new String[0])));
```

### TSRANGE (availability, duty_period)

```java
// Writing
PGobject range = new PGobject();
range.setType("tsrange");
range.setValue("[2026-07-09 08:00, 2026-07-09 20:00)");
ps.setObject(index, range);
// For null: ps.setNull(index, Types.OTHER)

// Reading — PostgreSQL returns tsrange as its literal string
String availability = rs.getString("availability");

// Service-layer validation
private boolean isValidTsrange(String s) {
    return s.matches("[\\[(].+,.+[\\])]");
}
```

### JSONB (medical_needs)

```java
// Writing
PGobject jsonb = new PGobject();
jsonb.setType("jsonb");
jsonb.setValue("{\"conditions\": [\"diabetes\"]}");
ps.setObject(index, jsonb);
// For null/blank: ps.setNull(index, Types.OTHER)

// Reading
String json = rs.getString("medical_needs"); // returns JSONB as text

// Service-layer validation (before DB)
if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
    return ServiceResult.failure("Medical needs must be valid JSON...");
```

### Nullable INTEGER FK

```java
// Writing
if (value == null) ps.setNull(index, Types.INTEGER);
else ps.setInt(index, value);

// Reading
int raw = rs.getInt("shelter_id");
Integer result = rs.wasNull() ? null : raw;
```

---

## 6. DAO Layer

**Package:** `com.groupa13.disasterrelief.dao.people`
**Pattern:** All methods receive `Connection conn` from the calling service. DAOs never open/close connections.

### 6.1 VolunteerDao.java

| Method | Key SQL Pattern | Notes |
|---|---|---|
| `insert(...)` | `INSERT INTO volunteers (...) VALUES (?, ?, ?, ?, ?::tsrange, ?) RETURNING person_id` | Returns generated ID |
| `findAll(conn)` | `SELECT v.*, z.name AS zone_name FROM volunteers v LEFT JOIN zones z ... ORDER BY v.name` | Joined zone name |
| `findById(conn, personId)` | Same + `WHERE v.person_id = ?` | Returns `Optional<Volunteer>` |
| `findBySkills(conn, skills)` | `WHERE v.skills @> ?` — bind as `Array` | GIN index used |
| `update(conn, personId, ...)` | `SET col = COALESCE(?, col)` for each field | Partial update — null keeps existing |
| `delete(conn, personId)` | `DELETE FROM volunteers WHERE person_id = ?` | Cascades to `volunteer_shifts` |

### 6.2 AffectedPersonDao.java

| Method | Key SQL Pattern | Notes |
|---|---|---|
| `insert(...)` | `INSERT INTO affected_persons (...) RETURNING person_id, registered_at` | Trigger fires automatically |
| `findAll(conn)` | `LEFT JOIN shelters s ... ORDER BY ap.registered_at DESC` | — |
| `findByZone(conn, zoneId)` | `WHERE ap.shelter_id = (SELECT primary_shelter_id FROM zones WHERE zone_id = ?)` | Zone coordinator view |
| `findByShelterId(conn, shelterId)` | `WHERE ap.shelter_id = ?` | — |
| `update(...)` | COALESCE partial; trigger fires on `shelter_id` change | — |
| `delete(conn, personId)` | `DELETE FROM affected_persons WHERE person_id = ?` | Trigger decrements occupancy |

### 6.3 VolunteerShiftDao.java

| Method | Key SQL Pattern | Notes |
|---|---|---|
| `insert(...)` | `INSERT INTO volunteer_shifts (..., ?::tsrange) RETURNING shift_id` | SQLState `23P01` if exclusion fires |
| `findAll(conn)` | `JOIN volunteers v ... JOIN zones z ...` | — |
| `findById(conn, shiftId)` | `WHERE vs.shift_id = ?` | Returns `Optional<VolunteerShift>` |
| `findByVolunteerId(...)` | `WHERE vs.volunteer_id = ?` | — |
| `update(...)` | COALESCE-based; exclusion constraint re-evaluated on update | — |
| `delete(conn, shiftId)` | `DELETE FROM volunteer_shifts WHERE shift_id = ?` | Does NOT cascade to volunteer |

---

## 7. Service Layer

**Package:** `com.groupa13.disasterrelief.service.people`
**Pattern:** Each public method = one `Database.inTransaction(conn -> ...)` call.

### 7.1 SQLState Error Mapping Table

| SQLState | PostgreSQL Cause | User Message |
|---|---|---|
| `23P01` | GiST exclusion (overlapping shift) | `"Volunteer already has an overlapping shift for this time period"` |
| `23502` | NOT NULL violation | `"Required field is missing"` |
| `23503` | FK violation | `"Referenced record does not exist"` / `"Shelter does not exist"` |
| `23505` | Unique violation | `"A duplicate volunteer record already exists"` |
| `22007` | Invalid timestamp format | `"Invalid date/time format. Use: yyyy-MM-dd HH:mm"` |
| `22P02` | Invalid text representation | Mapped from DB-level rejection |

### 7.2 VolunteerService.java — Public API

```java
ServiceResult<Volunteer>       register(name, contact, email, skills, availability, zoneId)
ServiceResult<List<Volunteer>> getAllVolunteers()
ServiceResult<Volunteer>       findById(int personId)
ServiceResult<List<Volunteer>> findBySkills(String skillsInput)  // comma-separated string
ServiceResult<Volunteer>       update(personId, name, contact, email, skills, availability, zoneId)
ServiceResult<Void>            delete(int personId)
List<String>                   getAllZonesForCombo()  // TODO: replace with ZoneService once Dev A merges
```

`findBySkills` splits comma-separated input before passing to DAO:
```java
List<String> skills = Arrays.stream(skillsInput.split(","))
    .map(String::trim).filter(s -> !s.isEmpty()).toList();
```

### 7.3 AffectedPersonService.java — Public API

```java
ServiceResult<AffectedPerson>       register(name, contact, email, medicalNeeds, shelterId, zoneId)
ServiceResult<List<AffectedPerson>> getAllAffectedPersons(Integer zoneId)  // null = admin (all)
ServiceResult<AffectedPerson>       findById(int personId)
ServiceResult<List<AffectedPerson>> findByShelterId(int shelterId)
ServiceResult<AffectedPerson>       update(personId, name, contact, email, medicalNeeds, shelterId, zoneId)
ServiceResult<Void>                 delete(int personId, Integer zoneId)
List<String>                        getAllSheltersForCombo()  // TODO: replace with ShelterService
```

**RLS Zone Context — critical for Wave 3:**

```java
private void setZoneContext(Connection conn, int zoneId) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
            "SELECT set_config('app.current_zone_id', ?, true)")) {
        ps.setString(1, String.valueOf(zoneId));
        ps.execute();
    }
}
```

- The `true` third argument makes the setting **transaction-local** — it resets automatically when the transaction ends.
- This prevents HikariCP connection reuse from leaking one user's zone identity to the next request.
- Dev C's RLS policies read `current_setting('app.current_zone_id', true)` to filter rows.

### 7.4 VolunteerShiftService.java — Public API

```java
ServiceResult<VolunteerShift>       createShift(int volunteerId, int zoneId, String dutyPeriod)
ServiceResult<List<VolunteerShift>> getAllShifts()
ServiceResult<VolunteerShift>       findById(int shiftId)
ServiceResult<List<VolunteerShift>> getShiftsByVolunteerId(int volunteerId)
ServiceResult<VolunteerShift>       updateShift(int shiftId, Integer zoneId, String dutyPeriod)
ServiceResult<Void>                 deleteShift(int shiftId)
List<String>                        getAllVolunteersForCombo()
List<String>                        getAllZonesForCombo()
```

---

## 8. Controller Layer

Controllers wired (FXML files not modified):
- `VolunteerController.java`
- `AffectedPersonController.java`
- `VolunteerShiftController.java`

### 8.1 Mandatory JavaFX Task Pattern

Every service call must follow this pattern — never call a service directly on the JavaFX Application Thread:

```java
@FXML
public void handleAdd(ActionEvent event) {
    errorLabel.setText("");

    // Step 1: Read form fields on the UI thread (safe here)
    String name = nameField.getText();
    List<String> skills = skillsField.getText().isBlank() ? List.of()
        : Arrays.stream(skillsField.getText().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

    // Step 2: Create Task that runs service call on background thread
    Task<ServiceResult<Volunteer>> task = new Task<>() {
        @Override
        protected ServiceResult<Volunteer> call() {
            return volunteerService.register(name, contact, email, skills, availability, zoneId);
        }
    };

    // Step 3: Handle result on JavaFX Application Thread (safe for UI updates)
    task.setOnSucceeded(e -> {
        ServiceResult<Volunteer> result = task.getValue();
        if (result.success()) {
            dataList.add(0, result.value());
            volunteerTable.setItems(dataList);
            handleClear(null);
        } else {
            errorLabel.setText(result.message());  // user-friendly, never raw SQL
        }
    });
    task.setOnFailed(e ->
        errorLabel.setText("Unexpected error: " + task.getException().getMessage()));

    // Step 4: Launch as daemon thread
    new Thread(task) {{ setDaemon(true); }}.start();
}
```

### 8.2 ComboBox ID Extraction Helper

ComboBox items are formatted as `"3 — Wayanad North"`. Extract the integer ID with:

```java
private Integer parseZoneId(String selection) {
    if (selection == null || selection.isBlank()) return null;
    try { return Integer.parseInt(selection.split(" — ")[0].trim()); }
    catch (NumberFormatException e) { return null; }
}
```

### 8.3 AffectedPersonController Zone Scoping

```java
// In initialize() — read session role and store zone ID
private Integer currentZoneId = null;

UserSession session = UserSession.getInstance();
if (session.getRole() == UserRole.ZONE_COORDINATOR) {
    this.currentZoneId = session.getAssignedZoneId();
}

// In every data-loading handler — pass zone ID to service
affectedPersonService.getAllAffectedPersons(this.currentZoneId);
// null = admin sees all rows; non-null = ZONE_COORDINATOR sees only their zone's shelter
```

---

## 9. Integration Tests (40 Tests, 6 Classes)

**Package:** `src/test/java/com/groupa13/disasterrelief/people/`
**Framework:** JUnit 5 + Testcontainers (`PostgreSQLContainer`)
**Setup:** `FlywayMigrator.migrate(dataSource)` applies V001–V004 once per class. `@BeforeEach` truncates tables for isolation.

### Test Coverage Summary

| Class | Count | What Is Proven |
|---|---|---|
| `VolunteerDaoTest` | 9 | INHERITS round-trip, TEXT[] GIN search (`@>`), nullable tsrange, partial COALESCE update, delete, DB-level type rejection |
| `AffectedPersonDaoTest` | 6 | Occupancy trigger on INSERT/DELETE/UPDATE, JSONB rejection, `findByZone`, `findByShelterId` |
| `VolunteerShiftDaoTest` | 7 | Exclusion constraint (`23P01`), adjacent shift succeeds, different-volunteer overlap succeeds, `findByVolunteerId`, delete |
| `VolunteerServiceTest` | 5 | Name validation, tsrange format validation, multi-skill comma-split, friendly messages |
| `AffectedPersonServiceTest` | 7 | JSON format validation, zone scoping, trigger-through-service occupancy, FK messages |
| `VolunteerShiftServiceTest` | 6 | `23P01` to friendly message, update re-evaluates exclusion, adjacent succeeds |

### The Four Acceptance Criteria

**AC-1: Occupancy maintained by trigger only**

```
Insert person at shelter A -> occupancy = 1
Reassign person to shelter B -> A = 0, B = 1
Delete person -> B = 0
Verification: grep confirms zero Java writes to current_occupancy
```

**AC-2: Malformed data fails safely**

```
volunteerService.register("Bob", null, null, null, "BADRANGE", null)
=> ServiceResult.success() == false
=> message contains tsrange format instructions

affectedPersonService.register("Alice", null, null, "not-json", null, null)
=> ServiceResult.success() == false
=> message contains JSON format instructions
```

**AC-3: Shift exclusion constraint — no double-booking**

```
Overlapping shift  => SQLState 23P01 => "Volunteer already has an overlapping shift for this time period"
Adjacent [08:00,20:00) + [20:00,08:00) => SUCCEEDS (half-open intervals; endpoint exclusion)
Different volunteer, same window => SUCCEEDS (constraint is per volunteer_id)
```

**AC-4: Skill array containment search**

```
Volunteer A: skills=["first_aid","driving"]
Volunteer B: skills=["cooking"]

findBySkills("first_aid")          => only A
findBySkills("cooking")            => only B
findBySkills("first_aid,driving")  => only A (must have BOTH)
findBySkills("translation")        => empty list
```

---

## 10. Merge Status and Implications

### Current Git State

```
ebfbd53 (local main / merge base)
   |
   +-- 5f56e73  feat: complete Dev A wave 2 core integration
   |
   +-- e5c01c0  dev 3 wave 2 & 3      <--- origin/main HEAD (2 ahead)
   |
   +-- 451b0a4  feat(people): add Person base model ...
   +-- 5012abc  feat(people): add VolunteerDao ...
   +-- dba993c  feat(people): add VolunteerService ...
   +-- b3a2ff2  feat(people): wire controllers ...
   +-- af0a994  test(people): add 40 integration tests   <--- feature/dev-b HEAD (local only)
```

### Merge Analysis: No Conflicts

`git merge-tree` on all six Dev B modified files (3 models, 3 controllers) returned 0 conflict markers.

**Why no conflicts?**
- Dev A Wave 2 added entirely new files (`dao/core/`, `service/core/`) — did not touch Dev B's controllers or model files.
- Dev C Wave 2&3 committed `AffectedPerson.java`, `Volunteer.java`, and the three controllers **in their original pre-refactor state** — the exact same state that is Dev B's starting point.

### The 3-vs-4 Migration Test — ALREADY FIXED on Origin

The `CorePlatformIntegrationTest` failure reported by Dev B is already resolved:

| Version | Assertion |
|---|---|
| Old `ebfbd53` baseline (broken) | `assertEquals(3, count)` — counted all migrations |
| Fixed `origin/main` | `assertEquals(Set.of("001","002","003"), querySet(...WHERE version IN ('001','002','003')))` |
| Table check: old | `assertEquals(Set.of(6 tables), querySet(...))` — exact equality rejects Wave 2 tables |
| Table check: fixed | `assertTrue(tables.containsAll(Set.of(6 tables)))` — Wave 2 tables are accepted |

Once `feature/dev-b` is rebased or merged onto `origin/main`, the test suite runs fully green.

### What Dev C Already Built That Depends on Dev B

V005 through V008 are all in `origin/main` and all reference V004 as a dependency:

| Migration | Dev C dependency on Dev B |
|---|---|
| `V005__operations_schema.sql` | Declares `V004 (people)` as prerequisite; `resource_requests` FKs to `zones`/`shelters` |
| `V006__operations_logic.sql` | `trg_audit_volunteers` and `trg_audit_affected_persons` on Dev B's tables; `fn_register_volunteer` inserts into `volunteers` |
| `V007__security_views_analytics.sql` | `app_users (person_id FK -> people(person_id))`; `v_volunteer_availability` JOINs `volunteers`, `people`, `volunteer_shifts`; RLS on `affected_persons` |
| `V008__development_seed.sql` | Seeds demo `app_users` rows linked to `people` entries |

### TODOs Now Resolvable

Both TODOs embedded by Dev B now have a real target in `origin/main`:

| TODO Location | Fix |
|---|---|
| `VolunteerService.getAllZonesForCombo()` | Replace inline SQL with `ZoneService.listZones()` from `service/core/ZoneService.java` |
| `AffectedPersonService.getAllSheltersForCombo()` | Replace inline SQL with `ShelterService.listShelters()` from `service/core/ShelterService.java` |
| `VolunteerShiftService.getAllZonesForCombo()` | Same — replace with `ZoneService.listZones()` |

---

## 11. Contracts Dev B Exposes to the Team

### To Dev C

| Contract Item | Detail |
|---|---|
| `people(person_id, name, contact, email)` | FK target for `app_users.person_id` in V007 |
| `volunteers INHERITS people` | Audit trigger (`trg_audit_volunteers`) attachment in V006 |
| `affected_persons INHERITS people` | RLS enabled; audit trigger attachment in V006 |
| `volunteer_shifts` | Audit trigger attachment in V006 |
| `app.current_zone_id` session variable | Set transaction-locally by `AffectedPersonService`. Read via `current_setting('app.current_zone_id', true)` in RLS policies |
| `trg_auto_update_shelter_occupancy` | Authoritative — never write `current_occupancy` from Java |
| SQLState `23P01` | Produced by GiST exclusion on overlapping shifts — no client-side duplicate check needed |

### To Dev A

| Item | Status |
|---|---|
| `ZoneService` / `ShelterService` | Now available in `origin/main` — Dev B can replace TODO combo loaders |
| `CorePlatformIntegrationTest` | Already fixed in `origin/main` — no further action needed |

---

## 12. Definition of Done Checklist

- [x] `./gradlew clean test` all Dev B tests: **40/40 PASS**
- [x] V004 applies cleanly after V001-V003
- [x] `people`, `volunteers`, `affected_persons`, `volunteer_shifts` tables exist with correct schema
- [x] `pg_inherits` confirms `people -> volunteers`, `people -> affected_persons`
- [x] GiST exclusion constraint on `volunteer_shifts`
- [x] `trg_auto_update_shelter_occupancy` fires correctly
- [x] 25+ required integration tests pass on real PostgreSQL 15
- [x] No controller contains `errorLabel.setText("TODO: ...")`
- [x] All three controllers use JavaFX `Task` for every service call
- [x] No controller calls DAO directly, uses `DriverManager`, or contains SQL
- [x] `Person.java` abstract base; `Volunteer extends Person`; `AffectedPerson extends Person`
- [x] `Volunteer.skills` is `List<String>`; `getSkillsDisplay()` exists
- [x] Only Dev B-owned files in diff vs origin/main
- [ ] PENDING: Push `feature/dev-b` and open PR against current `origin/main`
- [ ] PENDING: Rebase onto `origin/main`, re-run `./gradlew clean test` (0 failures expected)
- [ ] PENDING: Replace 3 TODO combo-loader methods with `ZoneService`/`ShelterService` from Dev A

---

## 13. Quick Reference Table

| Question | Where to Look |
|---|---|
| Why does `SELECT * FROM people` return volunteers? | Section 3.1, PostgreSQL INHERITS behavior |
| How do I store a skill array in JDBC? | Section 5, TEXT[] mapping |
| Why can't Java set `current_occupancy`? | Sections 3.2 and 7.3 (trigger ownership) |
| How does zone-based filtering work? | Section 7.3 (setZoneContext), Section 8.3 |
| What SQLState does a shift overlap produce? | `23P01`, Sections 6.3, 7.4, 9 |
| Why do adjacent shifts work but overlapping ones fail? | Section 9 AC-3, tsrange half-open interval `[start, end)` semantics |
| How are service errors displayed to the user? | Section 7.1 error mapping table |
| Is there a merge conflict with Dev A/C? | Section 10 — **No conflicts detected** |
| Is the migration count test still broken? | Section 10 — **Fixed in origin/main** |
| What did Dev C already build that depends on Dev B? | Section 10, V005-V008 dependency table |
