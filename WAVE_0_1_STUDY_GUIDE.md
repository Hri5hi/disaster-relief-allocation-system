# Waves 0 and 1 Study Guide

This document explains the completed backend foundation work for the Disaster
Relief Resource Allocation System (DRAS). It covers **only Wave 0 and Wave 1**.
Later waves will receive their own study guides after they are completed.

## 1. Big picture

The backend work is split into waves so that the team agrees on shared rules
before multiple developers start changing related code.

```text
Wave 0: decide the contracts
    ↓
Wave 1: build the shared database/platform baseline
    ↓
Later waves: build domain features on that stable baseline
```

Wave 0 answers *“What rules will everyone follow?”*.

Wave 1 answers *“Can a clean project start PostgreSQL, create the agreed core
schema, and prove it works automatically?”*.

The result is a reliable base for later work on people, authentication,
allocation, donations, audit logging, analytics, core DAOs/services, and UI
controllers. Those features were intentionally **not** implemented in Wave 1.

## 2. Wave 0 — contract and planning work

### Purpose

Wave 0 was a planning wave. Its purpose was to remove ambiguity before database
code, migrations, or controllers were changed. A database schema is difficult
to change after other developers depend on it, so the team first agreed on
names, data rules, ownership boundaries, and test expectations.

There were no runtime Java, SQL-migration, or controller features delivered in
Wave 0. The outcome was a written contract that Wave 1 had to follow.

### Ratified decisions

| Area | Decision | Why it matters |
|---|---|---|
| Local database | PostgreSQL 15, database `dras`, host `localhost:5432` | Docker, JDBC, migrations, and documentation all use the same target. |
| Example account | `dras_app` is the non-production example database user | Makes local setup predictable without committing a real credential. |
| Secrets | `.env` and `config/application.properties` are local and ignored by Git | Passwords must never be committed. Templates are committed instead. |
| Branches | `feature/dev-a`, `feature/dev-b`, `feature/dev-c` | Gives each developer a clear ownership branch. |
| Java/runtime | Java 21 | Keeps language and build expectations consistent. |
| Library versions | JDBC 42.7.13, HikariCP 7.1.0, Flyway 13.8.0, jBCrypt 0.4, JUnit 5.12.2, Testcontainers 2.0.5 | Prevents incompatible dependency choices between developers. |
| Migration naming | Immutable, zero-padded files such as `V001__extensions_and_types.sql` | Flyway applies migrations in a stable order. A merged migration is never edited; a correction becomes a new migration. |
| IDs | Database primary and foreign keys are integers | JDBC mapping and joins remain simple. UI-only display IDs such as `D1001` are formatting, not database keys. |
| Coordinates | `coordinates(latitude DOUBLE PRECISION, longitude DOUBLE PRECISION)` | Provides one shared location type for shelters and later map/proximity features. |
| Shelter address | `address VARCHAR(255)` is required | Users need a readable shelter location even when no map coordinates are available. |
| Shelter coordinates | `location coordinates` may be `NULL` | Offline registration can succeed before a shelter is geocoded. |
| Occupancy | `0 <= current_occupancy <= capacity` | Prevents invalid capacity data at the database level. |
| Deletes | Core references use `ON DELETE RESTRICT` | A record that is still being used cannot accidentally be removed. |
| Circular relationship | Create `zones` first with a nullable `primary_shelter_id`, create `shelters`, then add the shelter FK | Avoids a table-creation cycle while retaining the relationship. |
| Error style | Controllers should receive short, safe messages, never raw SQL/credentials/stack traces | Protects users and keeps the UI understandable. |

### Status vocabularies agreed in Wave 0

The following database enum values are shared vocabulary. Using enums prevents
spelling variations such as `High`, `HIGH`, or `hgh` from entering the database.

| Type | Values |
|---|---|
| `severity_level` | `low`, `medium`, `high`, `critical` |
| `disaster_status` | `active`, `monitoring`, `resolved`, `closed` |
| `shelter_status` | `operational`, `full`, `closed` |

### Cross-team boundaries decided in Wave 0

Wave 0 also identified who owns later work. These are plans and contracts, not
features already present in Wave 1.

| Future work | Owner/wave | Reason for recording it early |
|---|---|---|
| People, volunteers, shifts, and occupancy trigger | Dev B, beginning with V004 | The later tables depend on the Wave 1 core schema. |
| Requests, allocation, donations, audit, security, RLS, analytics | Dev C, beginning with V005 | Prevents Dev A's baseline from creating competing schema or logic. |
| `resource_requests.destination_shelter_id` | Dev C V005 | A request must identify the specific shelter receiving the allocation. |
| Demo application accounts | Dev C V008 | The planned users are `admin`, `zone_coord`, `warehouse_mgr`, `shelter_mgr`, and `logistics_officer`; their BCrypt hashes are added later, not in Wave 1. |
| Low-stock trigger and `LISTEN/NOTIFY` implementation | Dev C V006 | Wave 1 does not invent an operational trigger before its payload contract exists. |

### Wave 0 documentation files updated

| File | What changed | Purpose |
|---|---|---|
| [BACKEND_IMPLEMENTATION_PLAN.md](BACKEND_IMPLEMENTATION_PLAN.md) | Added/updated the ratified Wave 0 decision record | This is the full-team architecture and delivery plan. |
| [BACKEND_IMPLEMENTATION_GUIDE.md](BACKEND_IMPLEMENTATION_GUIDE.md) | Clarified the five future demo-account usernames | Makes the team guide agree with the ratified security/seed-data contract. |

The detailed Dev A guide created in Wave 1 also records the Wave 0 contract so
that an implementer can read the plan and its required decisions in one place.

## 3. Wave 1 — shared PostgreSQL platform baseline

### Purpose

Wave 1 converts the Wave 0 decisions into working infrastructure. It provides
a clean, reproducible PostgreSQL database and a Java database layer that later
features can safely use.

The central success condition was:

> From a clean checkout, PostgreSQL 15 can start, Flyway can migrate an empty
> database through V003, and automated tests can prove the schema and
> transaction behavior.

### Architecture created in Wave 1

```text
Local .env / application.properties
                │
                ▼
        DatabaseConfig validates values
                │
                ▼
 Flyway migrates an empty PostgreSQL database (V001 → V003)
                │
                ▼
   HikariCP creates the reusable application connection pool
                │
                ▼
 Database.inTransaction(...) commits or rolls back work safely
                │
                ▼
 Later DAO/service/controller code (not implemented in Wave 1)
```

For automated testing, Testcontainers starts an isolated PostgreSQL 15 Docker
container. Tests never rely on a developer's manually created database tables.

## 4. Wave 1 files and their purpose

### Build, Docker, and configuration

| File | Created or updated | What it does and why |
|---|---|---|
| [build.gradle](build.gradle) | Updated | Pins Java 21 and adds PostgreSQL JDBC, HikariCP, Flyway core/PostgreSQL support, BCrypt, JUnit 5, and Testcontainers. It also enables JUnit Platform test discovery. |
| [docker-compose.yml](docker-compose.yml) | Created | Defines a PostgreSQL 15 service with the `dras` database, `dras_app` user, persistent `dras_postgres_data` volume, `pg_isready` health check, configurable port, and no committed password. |
| [.env.example](.env.example) | Created | Safe Docker environment template. Developers copy it to ignored `.env` and replace `POSTGRES_PASSWORD`. |
| [config/application.properties.example](config/application.properties.example) | Created | Safe Java database configuration template: JDBC URL, user, password placeholder, Hikari pool values, and Flyway migration location. |
| [.gitignore](.gitignore) | Updated | Keeps `build/`, `bin/`, `.env`, local application properties, IDE files, and generated files out of Git. |

The two local files below are deliberately **not** committed. A developer creates
them from the templates:

```text
.env
config/application.properties
```

### Implementation and study/acceptance documentation

| File | Created or updated | What it does and why |
|---|---|---|
| [DEV_A_BACKEND_IMPLEMENTATION_GUIDE.md](DEV_A_BACKEND_IMPLEMENTATION_GUIDE.md) | Created | Detailed implementation contract for Dev A. It maps phases to waves, records the Wave 0 decisions, defines ownership boundaries, and explains the required database/test behavior. |
| [GATES.md](GATES.md) | Created | Records the executable Wave 1 acceptance checks: Compose validation, PostgreSQL migration/tests, clean build, static contract checks, and final scope review. All six gates passed. |
| [BACKEND_IMPLEMENTATION_GUIDE.md](BACKEND_IMPLEMENTATION_GUIDE.md) | Updated in Wave 0 and included in the Wave 1 baseline commit | Team-level guide; it is not a runtime dependency. |

### Database infrastructure classes

| File | Purpose |
|---|---|
| [Database.java](src/main/java/com/groupa13/disasterrelief/db/Database.java) | Owns the Hikari connection pool, runs migrations during startup, exposes `dataSource()`, executes transactions, restores auto-commit, closes resources, and sets the current zone in a transaction when `UserSession` has an assigned zone. |
| [DatabaseConfig.java](src/main/java/com/groupa13/disasterrelief/db/DatabaseConfig.java) | Loads and validates configuration. It rejects missing settings, invalid PostgreSQL URLs, invalid pool sizes, and unsafe timeouts with useful messages. It redacts the password in `toString()`. |
| [DatabaseException.java](src/main/java/com/groupa13/disasterrelief/db/DatabaseException.java) | A safe unchecked database boundary exception. It retains SQLState and PostgreSQL constraint names for logging/service translation without exposing raw database details to the UI. |
| [FlywayMigrator.java](src/main/java/com/groupa13/disasterrelief/db/FlywayMigrator.java) | Configures Flyway, validates the configured migration directory, migrates a supplied datasource, and validates the resulting schema history. |
| [SqlWork.java](src/main/java/com/groupa13/disasterrelief/db/SqlWork.java) | Functional interface that accepts the transaction-scoped `Connection`. It prevents services from opening unrelated connections during one transaction. |
| [ServiceResult.java](src/main/java/com/groupa13/disasterrelief/service/core/ServiceResult.java) | Shared service-to-controller result shape: `success`, `value`, and safe `message`. Later services use it instead of making controllers inspect SQL exceptions. |

### Important database-startup design choice

`Database` runs Flyway using a short-lived, unpooled `PGSimpleDataSource` before
creating the Hikari application pool.

Why? Flyway may need more than one database connection while it creates or
checks migration metadata. The application pool can validly be configured with
only one connection in a test. Running migrations through that one-connection
pool caused a connection timeout, so the startup design separates migration
work from the later reusable pool. This keeps startup reliable while preserving
strict transaction/pool-leak testing.

### Flyway migrations

Flyway runs migration files in version order. The three Wave 1 migrations are
the authoritative source of the core schema.

| File | Main contents | Why it exists |
|---|---|---|
| [V001__extensions_and_types.sql](database/migrations/V001__extensions_and_types.sql) | Creates `btree_gist`, `pgcrypto`, `coordinates`, and the three enums | Provides shared capabilities/types needed by the core schema and later developer waves. |
| [V002__core_schema.sql](database/migrations/V002__core_schema.sql) | Creates six core tables and foreign keys | Establishes the minimum operational data model. The zone/shelter circular FK is added only after both tables exist. |
| [V003__core_constraints_indexes.sql](database/migrations/V003__core_constraints_indexes.sql) | Adds named checks, unique constraints, and indexes | Makes invalid data impossible at the database layer and supports common foreign-key/filter queries. Named constraints also make error mapping and tests precise. |

### The six core tables created by V002

| Table | Represents | Important rules |
|---|---|---|
| `disasters` | A disaster event | Integer ID, nonblank name, severity enum, optional type and active date range, status, creation time. |
| `zones` | An area within a disaster | Must reference a disaster; unique name within that disaster; may later point to one primary shelter. |
| `shelters` | A place where relief is provided | Required name, required readable address, optional coordinates, positive capacity, occupancy default `0`, status enum. |
| `resource_categories` | An arbitrary-depth resource-category tree | Optional self-parent relationship; cannot name itself as its own parent. |
| `resources` | Resource catalogue entries, such as water or bandages | Must reference a category; optional unit must be meaningful if present. |
| `shelter_inventory` | Quantity of one resource at one shelter | Composite key `(shelter_id, resource_id)`, nonnegative quantity/reorder level, update timestamp. |

### Key integrity rules added by V003

The database, rather than only Java validation, enforces these rules:

- Names and other required text fields cannot be blank after trimming whitespace.
- A shelter address cannot be blank.
- A shelter location is either absent or has both coordinates within valid
  latitude/longitude ranges.
- Shelter capacity must be positive.
- Shelter occupancy must be nonnegative and cannot exceed capacity.
- Inventory quantity and reorder level must be nonnegative.
- Zone names are unique within the same disaster.
- Parent categories, resources, inventory, zones, and shelters cannot be
  deleted while still referenced (`ON DELETE RESTRICT`).
- Indexes support zone-by-disaster, resource-by-category, category-tree,
  inventory-by-resource, active-disaster, and operational-shelter queries.

## 5. Transaction behavior in Wave 1

`Database.inTransaction(UserSession, SqlWork)` is the shared transaction
boundary for later services.

```text
1. Borrow one Hikari connection.
2. Remember its original auto-commit state.
3. Set auto-commit to false.
4. If the session has an assigned zone, set app.current_zone_id locally.
5. Run the supplied SQL work.
6. Commit once on success, or roll back on SQL/runtime failure.
7. Restore auto-commit and close the connection.
```

The zone setting uses:

```sql
SELECT set_config('app.current_zone_id', ?, true)
```

The final `true` makes the setting transaction-local. This is important because
Hikari reuses connections: a zone ID from one user must not leak into a later
borrower's transaction.

## 6. Automated tests created in Wave 1

| File | What it proves |
|---|---|
| [PostgresTestSupport.java](src/test/java/com/groupa13/disasterrelief/support/PostgresTestSupport.java) | Starts a PostgreSQL 15 Testcontainer, builds a datasource/configuration for it, and truncates core tables between tests while retaining the migrated schema. |
| [DatabaseConfigTest.java](src/test/java/com/groupa13/disasterrelief/core/DatabaseConfigTest.java) | Valid configuration parses correctly; passwords are redacted; missing/invalid values are rejected; missing local config and bad Flyway inputs fail before a connection attempt. |
| [CorePlatformIntegrationTest.java](src/test/java/com/groupa13/disasterrelief/core/CorePlatformIntegrationTest.java) | A fresh PostgreSQL 15 database receives all migrations; schema types/tables/indexes exist; shelter offline/geocoded registration works; invalid data/FKs/deletes are rejected; transactions commit/rollback correctly and do not leak zone context. |

The integration suite specifically tests both a shelter with no coordinates and
a shelter with valid coordinates. This demonstrates the Wave 0 decision that a
readable address is required while map coordinates are optional.

## 7. Verification completed

The following checks passed after Docker and PostgreSQL 15 Testcontainers were
available:

```bash
docker compose --env-file .env.example config
./gradlew test
./gradlew clean test build
```

The final test run completed nine tests successfully:

- Four configuration/migration-input unit tests.
- Five PostgreSQL 15 integration tests.

All six Wave 1 acceptance gates in [GATES.md](GATES.md) are marked as met. The
Wave 1 implementation was committed and pushed as:

```text
ac96f73 feat: add wave 1 PostgreSQL platform baseline
```

## 8. What Wave 1 intentionally does not contain

Do not mistake a platform baseline for the complete backend. The following are
not implemented yet and belong to later waves:

- Core Java models, DAOs, and CRUD services.
- Replacing the five JavaFX controllers' sample-data behavior.
- People, volunteers, shifts, and the occupancy trigger.
- Application users, BCrypt login, authorization, and row-level security.
- Resource requests, allocation functions, donations, audit logs, analytics,
  partitions, dashboard logic, and low-stock triggers.
- The low-stock notification listener implementation.

Wave 1 gives later code a safe place to build. It does not pre-implement those
features because doing so would cross developer ownership boundaries and make
the shared migration history harder to maintain.

## 9. Recommended study order

1. Read the Wave 0 decisions in this guide first. They explain *why* the later
   code uses its exact names and constraints.
2. Read `docker-compose.yml`, `.env.example`, and
   `config/application.properties.example` together to understand local setup.
3. Read V001, V002, then V003 in order. They are the database's history.
4. Read `DatabaseConfig`, `FlywayMigrator`, then `Database` to understand
   startup and transactions.
5. Read the Testcontainers support and integration test to see how the contract
   is verified from an empty PostgreSQL database.
6. Use `DEV_A_BACKEND_IMPLEMENTATION_GUIDE.md` and the full implementation plan
   as detailed references when studying a particular design choice.

## 10. Quick self-check questions

1. Why is `address` required but `location` nullable for a shelter?
2. Why are primary keys integers even though the UI may display values such as
   `D1001`?
3. Why is the zone-to-primary-shelter foreign key added after both tables are
   created?
4. Why must a database constraint enforce occupancy and inventory rules even if
   Java validates user input?
5. Why is Flyway run before the Hikari application pool is created?
6. Why does `set_config(..., true)` protect against connection-pool zone leaks?
7. Why are migrations immutable after they have been merged?
8. Which later features are deliberately absent from Wave 1, and why?

If you can answer these questions, you understand the main architectural
purpose of Waves 0 and 1.
