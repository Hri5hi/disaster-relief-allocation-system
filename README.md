# Disaster Relief Resource Allocation System (DRRAS)

A production-grade Disaster Relief Resource Allocation System built for **23CSE202 - Database Management Systems** at **Amrita School of Computing**.

The application manages disaster relief operations with high operational fidelity: $\sim$85% of critical business rules (atomic inventory transfer with row locking, low-stock real-time alerts, cascade disaster closure, audit trails, and Row-Level Security) are executed directly within PostgreSQL 15+.

---

## System Architecture

```text
JavaFX Presentation Layer (7 Operational + 6 Core/People Screens)
       │ (Asynchronous JavaFX Task)
Service Layer (Transaction Boundaries, UserSession Context, ServiceResult)
       │ (Prepared Parameterized Statements Only)
DAO Layer (HikariCP Connection Pool)
       │
PostgreSQL 15+ (15 Physical Tables, Partitions, Advisory Locks, Triggers, Views, RLS)
```

---

## Physical Schema (15 Tables)

1. `disasters` — Root disaster incidents and active lifecycle status.
2. `zones` — Impacted geographic zones linked to disasters.
3. `shelters` — Relief camps and emergency shelters with geographic coordinates.
4. `resource_categories` — Hierarchical category tree (self-referencing).
5. `resources` — Master catalog of relief items and supplies.
6. `shelter_inventory` — M:N inventory junction with reorder level thresholds.
7. `people` — Superclass entity for all individuals in the system.
8. `volunteers` — Specialization of `people` with skills array and availability range.
9. `affected_persons` — Specialization of `people` with JSONB medical needs.
10. `resource_requests` — Priority requests with selectable destination shelter and generated `priority_score`.
11. `distribution_log` — Append-only transfer log range-partitioned monthly by `distributed_at`.
12. `volunteer_shifts` — Shift schedule with GiST exclusion constraint preventing overlapping bookings.
13. `donation_records` — Immutable intake log of donated bundles unpacked via `fn_process_donation`.
14. `audit_log` — Append-only mutation trail capturing JSONB old/new diffs across 10 mutable tables.
15. `app_users` — Application credentials with salted BCrypt password hashes, RBAC roles, and assigned zones.

---

## Tech Stack & Dependencies

* **Language / Runtime:** Java 21 LTS
* **UI Framework:** JavaFX 21 (Modular FXML, CSS, Ikonli FontAwesome icons)
* **Database:** PostgreSQL 15+
* **Connection Pooling:** HikariCP 7.1.0
* **Schema Migrations:** Flyway 13.8.0 (`filesystem:database/migrations`)
* **Security:** `org.mindrot:jbcrypt` (BCrypt password hashing)
* **Testing:** JUnit 5, PostgreSQL Testcontainers 2.0.5

---

## Standard Demonstration Accounts

All demonstration accounts are seeded via `database/migrations/V008__development_seed.sql` with the standard password:

> **Demonstration Password for All Roles:** `Password123!`

| Role | Username | Assigned Zone | Typical Actions |
|---|---|---|---|
| **System Administrator** | `admin` | Global / None | Full access, Disaster/Zone/Shelter CRUD, Materialized view refresh, Audit log review |
| **Zone Coordinator** | `zone_coord` | Zone 1 (Wayanad North) | Submit prioritized resource requests, register affected persons, view zone deficit |
| **Warehouse Manager** | `warehouse_mgr` | Global / None | Fulfill requests (`fn_allocate_resources`), process incoming donations, manage inventory |
| **Volunteer Coordinator**| `volunteer_coord`| Global / None | Schedule non-overlapping volunteer shifts, manage directory & availability |
| **Analytics User** | `analytics_user` | Global / None | Inspect deficit reports, materialized views, distribution statistics |

---

## Running the Project

### 1. Database Configuration
Copy the configuration template:
```bash
cp config/application.properties.example config/application.properties
```
Ensure a PostgreSQL 15+ instance is running (via `docker compose up -d` or a local PostgreSQL service).

### 2. Build and Test
Compile and execute the automated test suite:
```bash
./gradlew clean test
```

### 3. Launch Application
Launch the JavaFX desktop client:
```bash
./gradlew run
```

---

## Project Status

✅ **Backend Implementation Complete**
* Versioned migrations `V001` through `V008` in place.
* 15 physical tables, range partitioning, and trigger-driven audit logging.
* BCrypt authentication and role-based screen navigation enforcement.
* Real asynchronous services and DAOs wired to all controllers.