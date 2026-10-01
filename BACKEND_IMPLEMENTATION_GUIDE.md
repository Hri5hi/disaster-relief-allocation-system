# Backend Implementation Guide for Developers

This guide explains **how to execute** [BACKEND_IMPLEMENTATION_PLAN.md](BACKEND_IMPLEMENTATION_PLAN.md). Read the plan for ownership and technical requirements; use this file for the day-to-day approach.

## 1. Start here

Do not start implementing random controllers or writing SQL directly in a controller. The backend depends on a working PostgreSQL schema and shared connection layer first.

Follow this order:

1. Complete Wave 0 decisions together.
2. Dev A creates the database/platform baseline in Wave 1.
3. Once the baseline is merged, Dev A, Dev B, and Dev C work on their own owned files in parallel.
4. Dev C integrates cross-domain flows and runs the final integration tests.

The source of truth for file ownership is Section 5 of `BACKEND_IMPLEMENTATION_PLAN.md`. Do not edit a file owned by someone else. Request the owner to make a change instead.

## 2. Wave 0 — team decisions before coding

The team has ratified the following Wave 0 decisions (recorded in Section 9 of [BACKEND_IMPLEMENTATION_PLAN.md](BACKEND_IMPLEMENTATION_PLAN.md)):

| Decision | Agreed choice | Implementation owner & timing | Why it matters |
|---|---|---|---|
| Database name | `dras` (DRAS) | Dev A (Wave 1) | Establishes uniform database naming across Docker, Flyway, and environment configs. |
| Developer credentials | Local git-ignored config files | All Devs (post-Wave 1 baseline) | Populated locally in `.env` / `config/application.properties`; never committed to Git. |
| Branch naming | `feature/dev-a`, `feature/dev-b`, `feature/dev-c` | Dev A, Dev B, Dev C (Waves 1–3) | Developer-letter branches corresponding to each developer's owned subsystem. |
| Authentication table | Add `app_users` linked to `people` | Dev C (Wave 3 schema, Wave 4 docs) | Real login requires usernames, BCrypt hashes, roles, and zone assignments; brings physical table count to 15. |
| Database IDs | Use integer primary keys; format IDs only for display | All Devs (Waves 1–3) | Keeps foreign keys, queries, and JDBC mapping simple while maintaining UI conventions (e.g., `D1001`). |
| Request destination | Selectable destination shelter via `destination_shelter_id` | Dev C (Wave 2 V005 schema, Wave 3 V006 logic) | Allows requests to specify exact destination shelter, not just default primary shelter. |
| Demo accounts | One active account for each of the five roles | Dev C (Wave 3 V008) | Provides seed users (`admin`, `zone_coord`, etc.) with BCrypt hashes for role/RLS verification without committing real passwords. |
| Partition maintenance | Create current and next month distribution partitions | Dev C (Wave 2 V005, Wave 3 V006) | Prevents allocation failures at the start of a new month. |
| Audit log scope | Track 10 mutable operational tables | Dev C (Wave 2 V005, Wave 3 V006) | Captures changes on disasters, zones, shelters, resources, inventory, volunteers, affected persons, requests, shifts, donations. |
| Error-message style | Short, user-friendly messages (e.g., “Insufficient stock at source shelter”) | All Devs in service layer (Waves 2–3) | Maps SQLState class 23 and domain violations into clean UI feedback instead of raw SQL crashes. |

## 3. Local setup for every developer

After Dev A merges the platform baseline:

1. Pull `main` and create your own developer branch (`feature/dev-a`, `feature/dev-b`, or `feature/dev-c`).
2. Copy the example configuration (`config/application.properties.example`, `.env.example`) to your ignored local configuration file (`config/application.properties`, `.env`) and supply your local database credentials once Dev A has completed initial setup.
3. Start PostgreSQL with Docker Compose.
4. Run the migration command configured by Dev A. Verify that Flyway applies migrations in `database/migrations/`.
5. Run `./gradlew test` before starting a task. Fix a broken baseline before adding work.

Never share a database schema by manually running copied SQL in a GUI. Create a new numbered Flyway migration, commit it with the Java code that needs it, and let the test suite apply it to a clean database.

## 4. How each developer should work

### Dev A — build the foundation first

Dev A owns the backend platform and core operational data.

Work in this sequence:

1. Add PostgreSQL JDBC, HikariCP, Flyway, BCrypt, JUnit, and Testcontainers to Gradle.
2. Create Docker Compose and the example configuration. Confirm a clean local checkout can start PostgreSQL.
3. Create `Database` infrastructure: configuration loading, pooled datasource, transaction helper, and safe cleanup.
4. Add migrations V001–V003 for extensions, core tables, constraints, and indexes.
5. Add core DAOs/services in small vertical slices:
   - disasters and zones;
   - shelters;
   - categories/resources;
   - inventory.
6. Replace sample data in the matching five controllers only after their services are tested.
7. Add Testcontainers support so everyone can test a fresh PostgreSQL database.

Dev A’s first pull request is the team’s dependency baseline. Keep it focused: do not include people, authentication, or allocation logic in that PR.

### Dev B — implement people as database-backed workflows

Dev B begins after Dev A’s migrations V001–V003 are available.

Work in this sequence:

1. Write V004 for `people`, `volunteers`, `affected_persons`, and `volunteer_shifts`.
2. Test PostgreSQL-specific types before UI integration:
   - `TEXT[]` skills search;
   - `JSONB` medical-needs validation;
   - `tsrange` duty periods;
   - GiST exclusion constraint for overlapping shifts.
3. Implement DAO/service pairs one domain at a time: affected persons, volunteers, then shifts.
4. Implement the shelter occupancy trigger and test insert, reassignment, and deletion. Java must never update occupancy directly.
5. Replace TODO code in the three owned controllers using JavaFX `Task` calls to services.
6. Test the user-visible errors: malformed JSON, invalid range, missing required field, and overlapping shift.

Dev B should ask Dev A for a core-service capability through an interface/contract request, rather than changing Dev A’s DAO files.

### Dev C — implement real security and the cross-domain operations

Dev C starts the operations schema after Dev A’s core schema is merged. Features that depend on people should wait for Dev B’s V004 merge.

Work in this sequence:

1. Write V005 for requests, partitioned distribution history, donations, and audit log.
2. Write V006 for database business logic. Test `fn_allocate_resources` before connecting it to the UI.
3. Write V007 for `app_users`, roles/grants, RLS, views, materialized views, and analytics functions.
4. Add V008 seed accounts and coherent demo data.
5. Implement authentication. The role stored in `app_users` wins; the UI must not be able to grant itself a role.
6. Replace the operational controller TODOs: request, donation, distribution, audit, analytics, login, and dashboard.
7. Make `Utility.changeScene` enforce a single protected-screen role map.
8. Add the end-to-end Testcontainers suite after Dev A and Dev B merge their work.

For every operation that changes multiple tables, call a stored function from one service method. Do not reimplement allocation, occupancy, audit, or stock logic in Java.

## 5. Correct database-change workflow

Use this workflow for every schema feature:

1. Add the next migration file, for example `V009__add_feature.sql`.
2. Write the migration so it works on an empty database after all earlier migrations.
3. Add a Testcontainers test that proves both success and a meaningful failure case.
4. Run all migrations on a fresh test database.
5. Add or update the DAO/service using prepared statements.
6. Connect the controller only after the service test passes.
7. Commit the migration, Java code, and tests together.

Never:

- edit a migration that has been merged or applied;
- build SQL with string concatenation;
- put passwords in a migration, Java source, or commit;
- update a derived column such as `priority_score` or `current_occupancy` in Java;
- use a pooled connection with a persistent `SET app.current_zone_id` value.

For zone-bound work, use `set_config('app.current_zone_id', ?, true)` inside the current transaction. The `true` setting makes it transaction-local and prevents one Hikari connection from leaking a zone identity to its next borrower.

## 6. Pull-request and merge workflow

Each pull request should have one owner and one narrow purpose.

Before opening a PR:

- Rebase or merge the current `main` branch.
- Confirm that only files you own are changed.
- Run `./gradlew test`.
- Start from a fresh PostgreSQL Testcontainer/database, not an old local schema.
- State the migrations added and the test cases covered in the PR description.

Review checklist:

- Are SQL statements parameterized?
- Are transaction boundaries in the service layer?
- Are function/trigger rules implemented in PostgreSQL instead of duplicated in Java?
- Does the code use the developer’s owned package/files only?
- Does a negative test prove that an invalid operation fails?

Merge order:

1. Dev A platform + V001–V003.
2. Dev B V004 and people modules; Dev C V005 operations schema may proceed once the core schema is present.
3. Dev C V006–V008 after dependencies are available.
4. Dev C integration tests after all domain PRs are merged.

## 7. How to test the most important flows

### Login and security

- Log in with each seeded account and confirm the stored role is used.
- Attempt an invalid password and inactive account.
- Attempt to navigate directly to an unauthorized screen.
- As a zone coordinator, attempt to read or modify another zone’s request or affected person.

### Allocation

- Create a pending request with adequate source stock; fulfill it once.
- Verify inventory decreases, request status becomes fulfilled, and one distribution row is written.
- Repeat fulfillment and verify it fails without changing data.
- Run two concurrent fulfillment attempts and verify only one succeeds.
- Attempt allocation with insufficient stock and verify every related change rolls back.

### People and shelters

- Register, reassign, and delete an affected person; verify shelter occupancy is correct each time.
- Create overlapping volunteer shifts; verify PostgreSQL rejects the second one.
- Search volunteers by a skill stored in the `TEXT[]` field.

### Donation, audit, alerts, and reporting

- Submit a multi-item donation; verify the JSONB record and inventory UPSERTs.
- Cross a reorder threshold; verify one low-stock notification is received.
- Change a mutable row; verify the audit log records correct old/new JSON and cannot be edited by the application.
- Refresh materialized views and verify analytics/chart queries return database data instead of sample lists.

## 8. When the backend is actually finished

Do not call the backend complete because screens look correct. It is complete only when:

- all migrations apply to a fresh PostgreSQL 15 database;
- no controller still has placeholder authentication, sample data, or TODO persistence behavior;
- all five roles authenticate and role/zone restrictions hold in the database;
- transactions, triggers, constraints, audit logging, alerts, partitions, views, and analytics pass automated tests;
- `./gradlew clean test build` passes from a clean checkout;
- the README documents local setup, migrations, test execution, and demo accounts.

If a developer is blocked, record the exact dependency in the pull request or team chat. Do not silently modify another developer’s owned file to unblock yourself.
