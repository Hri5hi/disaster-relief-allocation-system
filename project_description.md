# Disaster Relief Resource Allocation System (DRRAS)

## DBMS Course Project — Project Description

---

## 1. Real-World Problem

### The Crisis

When a natural disaster strikes — a flood, earthquake, cyclone, or landslide — government agencies and NGOs must simultaneously manage a cascade of urgent, interdependent operations:

- **Thousands of displaced people** need shelter, food, water, and medical supplies — each with unique needs (elderly requiring medication, children needing nutrition supplements, persons with disabilities needing accessible shelters).
- **Limited relief resources** (food packets, water drums, medicines, blankets, tarpaulins) must be distributed across multiple affected zones — but supplies are finite, constantly depleting, and arriving unevenly through donations.
- **Hundreds of volunteers** with varying skills (medical, rescue, logistics, translation) must be deployed to the right zones at the right times, without double-scheduling or leaving any zone uncovered.
- **Shelters fill up unpredictably** — occupancy changes hour by hour as people arrive, are relocated, or return home. A shelter at 30% capacity in the morning can be at 110% by evening.
- **Urgency is not uniform** — a zone running out of drinking water is more critical than a zone requesting extra blankets. Resource allocation must be *priority-driven*, not first-come-first-served.
- **Accountability is non-negotiable** — every resource unit must be traceable from donation receipt → warehouse → transport → distribution point → beneficiary. Audit trails are a legal and ethical requirement.

### Why Current Systems Fail

In practice, disaster relief coordination in India (and globally) still relies heavily on:

| Current Method | Problem |
|---|---|
| **Spreadsheets and WhatsApp groups** | No real-time stock visibility; data conflicts when multiple coordinators update simultaneously |
| **Paper registers at shelters** | Occupancy counts are delayed; no central dashboard for decision-makers |
| **Phone-call-based resource requests** | No priority queuing; critical requests wait behind routine ones |
| **Manual volunteer scheduling** | Double-assignments, skill mismatches, shift gaps |
| **Post-disaster auditing from paper trails** | Weeks of effort to reconstruct what went where and when |

**The core problem is: disaster relief is a complex, multi-stakeholder, time-critical resource allocation problem that demands transactional integrity, real-time visibility, priority-based queuing, and complete audit traceability — yet it is still managed with tools that offer none of these guarantees.**

---

## 2. Objectives

### Primary Objectives

| # | Objective | What It Means |
|---|---|---|
| O1 | **Centralized disaster event management** | Register disasters, define affected zones with severity levels, and track disaster lifecycle from active → resolved |
| O2 | **Shelter capacity management with real-time tracking** | Register shelters with capacity limits, track occupancy in real time, prevent over-allocation at the database constraint level |
| O3 | **Priority-based resource allocation** | Allow zone coordinators to submit resource requests with urgency levels; allocate resources using a priority-scoring algorithm implemented entirely within the database |
| O4 | **Atomic, auditable resource transfers** | Every resource movement (source shelter → destination shelter) must be a single atomic database transaction — either the entire transfer completes or nothing changes |
| O5 | **Volunteer management with conflict-free scheduling** | Register volunteers with skill sets, assign them to zones and shifts, and prevent double-scheduling using database-level exclusion constraints |
| O6 | **Donation tracking and inventory management** | Record incoming donations, update shelter inventory automatically, generate low-stock alerts when supplies drop below thresholds |
| O7 | **Comprehensive audit trail** | Every data mutation (insert, update, delete) on critical tables must be automatically logged with timestamp, user, old values, and new values — enforced by database triggers, not application code |
| O8 | **Analytical dashboards and reporting** | Provide zone deficit reports, shelter occupancy dashboards, resource consumption trends (7-day moving averages), and volunteer coverage analysis using views, materialized views, and window functions |

### Database Engineering Objectives

| # | Objective | DBMS Concept Demonstrated |
|---|---|---|
| D1 | Push **all business logic into PostgreSQL** | Stored functions (PL/pgSQL), triggers, constraints, generated columns |
| D2 | Demonstrate **PostgreSQL-specific features** not available in other RDBMS | Table inheritance, range types, exclusion constraints, JSONB, array types, LISTEN/NOTIFY, advisory locks, row-level security, custom composite types |
| D3 | Ensure **transactional integrity** across multi-table operations | `BEGIN...COMMIT` blocks, `SELECT...FOR UPDATE` row-level locking, advisory locks for concurrent allocation safety |
| D4 | Implement **advanced query patterns** | Recursive CTEs (category hierarchy roll-ups), window functions (moving averages, ranking), LATERAL JOINs (top-N per group), materialized views with concurrent refresh |
| D5 | Design for **performance at scale** | Table partitioning (distribution logs by month), composite and partial indexes, GiST indexes on range types, GIN indexes on JSONB and arrays |

---

## 3. Proposed Database System

### Technology Stack

| Component | Technology | Justification |
|---|---|---|
| **RDBMS** | **PostgreSQL 15+** | Required for: table inheritance, range types (`tsrange`/`daterange`), exclusion constraints, JSONB, array types, `LISTEN/NOTIFY`, advisory locks, row-level security, generated columns, partitioning, `LATERAL JOIN`, `UPSERT` — none of which are available in MySQL |
| **Application Layer** | **Java 17 + JavaFX** | Reuses existing Hostel Management System's UI architecture; JavaFX provides rich desktop GUI with TableViews, Charts, and FXML layouts |
| **JDBC Driver** | **PostgreSQL JDBC (42.x)** | Standard Java-to-PostgreSQL connectivity |
| **Connection Pooling** | **HikariCP** | High-performance JDBC connection pool replacing the current connection-per-query pattern |
| **Build System** | **Gradle** | Existing build system; only the driver dependency changes (MySQL → PostgreSQL) |

### Database Schema Overview (13 Tables + Views)

#### Core Tables

```
┌──────────────────────────────────────────────────────────────────┐
│                        DISASTER DOMAIN                          │
├─────────────────┬────────────────────────────────────────────────┤
│ disasters       │ Disaster events with daterange active periods  │
│ zones           │ Affected areas with severity, linked to        │
│                 │ a disaster and a primary shelter               │
└─────────────────┴────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                       SHELTER & INVENTORY                        │
├─────────────────┬────────────────────────────────────────────────┤
│ shelters        │ Relief shelters with capacity, location        │
│                 │ (custom composite type), occupancy tracking    │
│ resource_       │ Hierarchical categories (Medical → First Aid   │
│   categories    │ → Bandages) for recursive CTE roll-ups        │
│ resources       │ Resource master list with category linkage     │
│ shelter_        │ Current stock per shelter per resource,        │
│   inventory     │ with reorder_level for trigger-based alerts    │
└─────────────────┴────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                      PEOPLE (Inheritance)                        │
├─────────────────┬────────────────────────────────────────────────┤
│ people          │ Base table: person_id, name, contact, email    │
│ ├─ volunteers   │ INHERITS people: skills (TEXT[]), availability │
│ │               │ (tsrange), assigned_zone                       │
│ └─ affected_    │ INHERITS people: medical_needs (JSONB),        │
│    persons      │ shelter assignment, registration timestamp     │
└─────────────────┴────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                    ALLOCATION & DISTRIBUTION                     │
├─────────────────┬────────────────────────────────────────────────┤
│ resource_       │ Requests from coordinators with urgency,       │
│   requests      │ auto-computed priority_score (generated col)   │
│ distribution_   │ Timestamped records of every resource          │
│   log           │ transfer (partitioned by month)                │
│ volunteer_      │ Shift assignments with tsrange duty periods    │
│   shifts        │ and exclusion constraint (no overlaps)         │
└─────────────────┴────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                     DONATIONS & AUDIT                            │
├─────────────────┬────────────────────────────────────────────────┤
│ donation_       │ Donor details, items, quantities, dates        │
│   records       │                                                │
│ audit_log       │ Trigger-populated: tracks all mutations        │
│                 │ across key tables (automatic, zero app code)   │
└─────────────────┴────────────────────────────────────────────────┘
```

#### Entity-Relationship Summary

```
disasters ──1:N──→ zones ──1:N──→ resource_requests
                     │                   │
                     └──→ shelters ──1:N──→ shelter_inventory ←──N:1── resources
                            │                                            │
                            ├──1:N──→ affected_persons (INHERITS people) │
                            │                                            │
                            └───── distribution_log (partitioned) ───────┘
                                         ↑
volunteers (INHERITS people) ──→ volunteer_shifts (exclusion constrained)

resource_categories ──self-referencing (parent_id)──→ recursive hierarchy
                         │
                         └──1:N──→ resources

donation_records ──→ shelter_inventory (UPSERT on receipt)

ALL key tables ──trigger──→ audit_log
```

#### Views and Materialized Views

| View | Type | Purpose |
|---|---|---|
| `v_shelter_dashboard` | Regular | Real-time occupancy %, stock levels, latest arrivals per shelter |
| `v_pending_requests_ranked` | Regular | Pending resource requests ordered by auto-computed `priority_score` |
| `v_volunteer_availability` | Regular | Volunteers with no active shift at the current timestamp |
| `v_active_disasters` | Regular | Disasters where `active_period @> CURRENT_DATE` |
| `mv_zone_resource_summary` | Materialized | Aggregated resource status by zone (refreshed with `CONCURRENTLY`) |
| `mv_daily_distribution_stats` | Materialized | Distribution volume trends for analytics dashboards |

---

## 4. Intended Users

| User Role | Description | Key Operations | Access Level |
|---|---|---|---|
| **System Administrator** | Manages disaster events, creates zones, registers shelters, configures resource categories | Full CRUD on all entities; disaster lifecycle management (activate, resolve, close) | Unrestricted |
| **Zone Coordinator** | Manages a specific affected zone; submits resource requests, registers affected persons | Submit resource requests with urgency levels; register/update affected persons at shelters; view zone-specific dashboards | Restricted to their assigned zone (enforced by PostgreSQL Row-Level Security) |
| **Warehouse / Inventory Manager** | Manages resource stock at shelters; processes incoming donations; fulfills resource transfer requests | View pending requests; execute resource allocations (calls `fn_allocate_resources`); process donations; monitor low-stock alerts via `LISTEN/NOTIFY` | Access to inventory and distribution tables |
| **Volunteer Coordinator** | Registers volunteers, assigns shifts, matches volunteer skills to zone needs | Register volunteers with skills (array type); assign shifts (with exclusion constraint validation); view volunteer availability dashboard | Access to volunteer and shift tables |
| **Reporting / Analytics User** | Decision-makers (district collectors, NGO directors) who need aggregate visibility | View materialized views; run deficit reports (`fn_zone_deficit_report`); consumption trend analysis with window functions | Read-only access to views and materialized views |

---

## 5. Major Functionalities

### 5.1 Disaster & Zone Management
- Register new disaster events with name, type, severity, location, and `daterange` active period
- Define affected zones within a disaster, each with a severity level and a primary shelter assignment
- Track disaster lifecycle: `active` → `monitoring` → `resolved` → `closed`
- **Database complexity:** When a disaster is marked `resolved`, a trigger (`trg_cascade_disaster_close`) automatically cancels all pending resource requests for that disaster — zero application code

### 5.2 Shelter & Occupancy Management
- Register shelters with capacity, location (using custom `coordinates` composite type), and amenities
- Track real-time occupancy as affected persons are registered or released
- Prevent over-capacity at the database level using `CHECK` constraints
- **Database complexity:** A trigger (`trg_auto_update_shelter_occupancy`) automatically recalculates `current_occupancy` whenever an affected person is inserted into or removed from a shelter — the application never manually counts

### 5.3 Priority-Based Resource Allocation (Core Feature)
- Zone coordinators submit resource requests specifying: resource type, quantity, and urgency (`critical`, `high`, `medium`, `low`)
- A **generated column** (`priority_score`) automatically computes a numeric priority based on urgency and quantity — no Java computation
- Warehouse managers view requests ranked by `priority_score` and execute allocations
- **Database complexity:** The allocation operation is a single call to `fn_allocate_resources(request_id, source_shelter_id)`, which atomically:
  1. Acquires an **advisory lock** on the resource to prevent concurrent allocation race conditions
  2. Verifies source shelter has sufficient stock (using `SELECT ... FOR UPDATE` row locking)
  3. Deducts quantity from source shelter inventory
  4. Adds quantity to destination shelter inventory (using `UPSERT` / `ON CONFLICT DO UPDATE`)
  5. Logs the transfer in `distribution_log` (using `RETURNING` to get the log ID in one round-trip)
  6. Marks the request as fulfilled
  7. Returns success/failure with a message to the Java client
  - If any step fails, the entire transaction rolls back — **atomicity guaranteed by the database, not Java**

### 5.4 Volunteer Management & Conflict-Free Scheduling
- Register volunteers with name, contact, email, and **skills stored as a PostgreSQL array** (`TEXT[]`)
- Search for volunteers by skill using array operators (`@>`, `&&`, `= ANY()`)
- Assign volunteers to zone shifts with `tsrange` duty periods
- **Database complexity:** An **exclusion constraint** (`EXCLUDE USING gist (volunteer_id WITH =, duty_period WITH &&)`) prevents overlapping shifts for the same volunteer *at the database level* — the application cannot bypass this even with a bug. This is a uniquely PostgreSQL feature unavailable in MySQL, Oracle, or SQL Server.

### 5.5 Donation Processing & Inventory Management
- Record incoming donations (donor name, items as JSONB, quantities, date)
- Automatically update shelter inventory upon donation receipt using `UPSERT` (insert if not exists, add to quantity if exists)
- Set reorder levels per resource per shelter
- **Database complexity:** A trigger (`trg_low_stock_alert`) fires `AFTER UPDATE ON shelter_inventory` — if quantity drops below `reorder_level`, it:
  1. Inserts an alert record into the `alerts` table
  2. Calls `pg_notify('low_stock_alert', ...)` to **push a real-time notification to the Java application** via PostgreSQL's built-in pub/sub (`LISTEN/NOTIFY`) — the app doesn't poll; the database pushes

### 5.6 Affected Person Registration
- Register displaced persons at shelters with personal details and medical needs (stored as `JSONB` — flexible schema for conditions, medications, accessibility requirements)
- Query medical needs using JSONB operators (e.g., find all persons needing wheelchair-accessible shelters: `WHERE medical_needs @> '{"wheelchair": true}'`)
- **Database complexity:** `affected_persons` **inherits** from the `people` base table using PostgreSQL's `INHERITS` — querying `SELECT * FROM people` returns both volunteers and affected persons. This mirrors the OOP inheritance (`Person` → `Student`/`Warden`) in the existing Java project, but at the *database level*.

### 5.7 Analytics & Reporting
- **Zone Deficit Report:** `fn_zone_deficit_report(disaster_id)` — a stored function that uses CTEs to compare required vs. available resources per zone and classifies deficit urgency
- **Resource Consumption Trends:** Window functions compute 7-day moving averages and day-over-day changes in resource consumption per shelter
- **Resource Category Roll-Up:** A **recursive CTE** traverses the hierarchical `resource_categories` table (Medical → First Aid → Bandages) to aggregate total stock across an entire category tree
- **Top-N Urgent Requests Per Zone:** A **`LATERAL JOIN`** efficiently retrieves the top 3 most urgent pending requests for each zone in a single query
- **Shelter Occupancy Dashboard:** A view joining shelters, affected persons, and capacity to show real-time utilization percentages
- **Database complexity:** Materialized views (`mv_zone_resource_summary`, `mv_daily_distribution_stats`) pre-compute expensive analytics; refreshed on-demand using `REFRESH MATERIALIZED VIEW CONCURRENTLY` (non-blocking refresh — another PostgreSQL-specific feature)

### 5.8 Audit & Security
- **Automatic audit logging:** A trigger (`trg_audit_log`) fires on `INSERT`, `UPDATE`, and `DELETE` on all critical tables — records the operation type, table name, old row values, new row values, timestamp, and session user. Zero application code required.
- **Row-Level Security (RLS):** Zone coordinators can only see and modify data belonging to their assigned zone. PostgreSQL enforces this transparently — the application issues a plain `SELECT * FROM resource_requests` but the database automatically filters results based on `current_setting('app.current_zone_id')`.

---

## 6. Database Complexity Summary — Beyond Simple Read/Write

| Complexity Dimension | How This Project Goes Beyond Simple CRUD |
|---|---|
| **Multi-table atomic transactions** | Resource allocation spans 4 tables in a single atomic function — not 4 separate INSERT/UPDATE calls from Java |
| **Database-enforced business rules** | Exclusion constraints, CHECK constraints, generated columns, and triggers enforce rules that *cannot be bypassed* by application bugs |
| **Stored procedural logic (PL/pgSQL)** | 6+ stored functions containing allocation algorithms, deficit calculations, batch processing — the database *computes*, not just stores |
| **Trigger-driven automation** | 5 triggers handle audit logging, occupancy recalculation, low-stock alerts, allocation validation, and disaster cascade operations — automatically, without application intervention |
| **Advanced query patterns** | Recursive CTEs, window functions (moving averages, ranking), LATERAL JOINs, and JSONB path queries — not just `SELECT * WHERE id = ?` |
| **PostgreSQL-specific features (16)** | Table inheritance, range types, exclusion constraints, JSONB, array types, LISTEN/NOTIFY, advisory locks, RLS, custom types, generated columns, UPSERT, RETURNING, partitioning, recursive CTEs, materialized views, LATERAL JOIN |
| **Concurrency control** | Advisory locks and `SELECT...FOR UPDATE` prevent race conditions during simultaneous resource allocations |
| **Performance engineering** | Table partitioning, composite indexes, partial indexes, GiST indexes (range types), GIN indexes (JSONB, arrays) |
| **Real-time notifications** | `LISTEN/NOTIFY` pushes alerts from database to application — a fundamentally different pattern from polling |
| **Access control at the data level** | Row-Level Security filters data by zone — the application doesn't implement authorization, the database does |

> [!IMPORTANT]
> **The key design principle:** In this system, **~85% of business logic resides inside PostgreSQL** (stored functions, triggers, constraints, generated columns). Java serves as a **thin presentation layer** — it calls `SELECT * FROM fn_allocate_resources(?, ?)` and displays the result. This is the inverse of the existing Hostel Management System where Java contains all logic and the database is passive storage. This inversion is exactly what demonstrates mastery of database engineering.

---

## 7. Mapping to Existing Codebase (Reuse Strategy)

| Existing Component | Reuse in DRRAS | Modification Needed |
|---|---|---|
| `Person` (abstract model) | `Person` base class for volunteers + affected persons | Minimal — same fields |
| `Student` (model) | `Volunteer` (same fields + skills array, zone assignment) | Rename + add fields |
| `Warden` (model) | `Coordinator` (authentication, zone assignment) | Rename + add zone_id |
| `Room` (model) | `Shelter` (capacity, occupancy, location) | Rename + add coordinates |
| `Hostel` (model) | `Zone` / `Disaster` (grouping entity) | Structural mapping |
| `Allocation` (model) | `ResourceAllocation` / `Distribution` (assignment pattern) | Extend with priority, urgency |
| `Accommodation` (interface) | `AllocationService` (interface) | Direct mapping |
| `FileManager` (repository) | `PostgreSQLRepository` (DAO layer) | **Full rewrite** — calls stored functions instead of inline SQL |
| `HostelSystem` (service) | `DisasterReliefService` (thin service) | Simplified — delegates to DB functions |
| All UI controllers + FXML | Adapted for disaster domain (same table/form patterns) | Labels and fields change; layout patterns identical |
| Gradle build, CSS | ~95% reused | Swap MySQL driver → PostgreSQL driver |

---
