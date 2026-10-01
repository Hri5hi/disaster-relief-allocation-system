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

### Database Schema — All 14 Tables

The system uses **14 tables** organized into 6 functional groups. Each table is listed below with its key columns, data types, and relationships.

---

#### Group 1: Disaster & Zone Management (2 tables)

**Table 1 — `disasters`**

| Column | Type | Notes |
|---|---|---|
| `disaster_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `name` | `VARCHAR(100) NOT NULL` | e.g., "Kerala Floods 2026" |
| `type` | `VARCHAR(50)` | e.g., flood, earthquake, cyclone |
| `severity` | `VARCHAR(10)` | CHECK: `'low'`, `'medium'`, `'high'`, `'critical'` |
| `active_period` | `daterange` | PostgreSQL range type, e.g., `'[2026-07-01, 2026-07-15)'` |
| `status` | `VARCHAR(20)` | CHECK: `'active'`, `'monitoring'`, `'resolved'`, `'closed'` |
| `created_at` | `TIMESTAMPTZ` | Default `NOW()` |

**Table 2 — `zones`**

| Column | Type | Notes |
|---|---|---|
| `zone_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `disaster_id` | `INTEGER NOT NULL` | **FK → `disasters(disaster_id)`** |
| `name` | `VARCHAR(100) NOT NULL` | e.g., "Wayanad North Sector" |
| `severity` | `VARCHAR(10)` | Zone-level severity (can differ from disaster-level) |
| `primary_shelter_id` | `INTEGER` | **FK → `shelters(shelter_id)`** — the main shelter serving this zone |
| `coordinator_contact` | `VARCHAR(20)` | Phone number of the zone coordinator |

> **Relationship:** One disaster has many zones. Each zone belongs to exactly one disaster.

---

#### Group 2: Shelters & Inventory (4 tables)

**Table 3 — `shelters`**

| Column | Type | Notes |
|---|---|---|
| `shelter_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `name` | `VARCHAR(100) NOT NULL` | e.g., "Government School Hall, Meppadi" |
| `address` | `VARCHAR(255)` | Human-readable address displayed in the UI, e.g., "Meppadi, Wayanad, Kerala 673577". Entered by the coordinator on registration. See § UI Design Decision below. |
| `location` | `coordinates` | **Custom composite type** — `(latitude DOUBLE PRECISION, longitude DOUBLE PRECISION)`. Used for backend spatial queries and map pin rendering. Optionally auto-filled from address via Nominatim geocoding. |
| `capacity` | `INTEGER NOT NULL` | CHECK: `capacity > 0` |
| `current_occupancy` | `INTEGER DEFAULT 0` | Auto-updated by trigger; CHECK: `current_occupancy >= 0` |
| `status` | `VARCHAR(20)` | CHECK: `'operational'`, `'full'`, `'closed'` |

> **UI Design Decision — Shelter Location Entry (`shelter.fxml`):**  
> The shelter registration form exposes **two separate fields**: a free-text *Address* field and optional *Latitude / Longitude* fields. The form also includes an *"Auto-fill coordinates from address"* button which calls the [OpenStreetMap Nominatim API](https://nominatim.org/) (`GET /search?q=<address>&format=json`) to populate coordinates automatically when internet is available. If the API is unreachable (common during active disasters), the coordinator can enter coordinates manually or leave them blank — the system degrades gracefully since `location` is not enforced as NOT NULL at the form level. `address` is always the field displayed in all UI tables, reports, and printed forms; raw coordinates are never shown to end users.

**Table 4 — `resource_categories`** *(self-referencing for hierarchy)*

| Column | Type | Notes |
|---|---|---|
| `category_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `name` | `VARCHAR(100) NOT NULL` | e.g., "Medical", "First Aid", "Bandages" |
| `parent_id` | `INTEGER` | **FK → `resource_categories(category_id)`** — NULL for top-level categories |

> **Relationship:** Self-referencing parent-child hierarchy. Queried with **recursive CTEs** to roll up quantities across the tree (e.g., "How much total Medical supplies do we have?" sums across Medical → First Aid → Bandages + Antiseptic + ...).

**Table 5 — `resources`**

| Column | Type | Notes |
|---|---|---|
| `resource_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `name` | `VARCHAR(100) NOT NULL` | e.g., "Paracetamol 500mg", "20L Water Drum" |
| `category_id` | `INTEGER NOT NULL` | **FK → `resource_categories(category_id)`** |
| `unit` | `VARCHAR(20)` | e.g., "packets", "litres", "pieces" |

**Table 6 — `shelter_inventory`**

| Column | Type | Notes |
|---|---|---|
| `shelter_id` | `INTEGER NOT NULL` | **FK → `shelters(shelter_id)`** |
| `resource_id` | `INTEGER NOT NULL` | **FK → `resources(resource_id)`** |
| `quantity` | `INTEGER NOT NULL` | CHECK: `quantity >= 0` |
| `reorder_level` | `INTEGER DEFAULT 10` | Threshold for low-stock alert trigger |
| `last_updated` | `TIMESTAMPTZ` | Default `NOW()` |
| | | **PK:** `(shelter_id, resource_id)` — composite primary key |

> **Relationship:** A junction table linking shelters to resources with quantities. `UPSERT` (`ON CONFLICT DO UPDATE`) is used when donations arrive — if the resource already exists at that shelter, the quantity is added; otherwise, a new row is inserted.

---

#### Group 3: People — Using PostgreSQL Table Inheritance (3 tables)

**Table 7 — `people`** *(base table)*

| Column | Type | Notes |
|---|---|---|
| `person_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `name` | `VARCHAR(100) NOT NULL` | Full name |
| `contact` | `VARCHAR(20)` | Phone number |
| `email` | `VARCHAR(100)` | Email address |

**Table 8 — `volunteers`** *(INHERITS people)*

| Column | Type | Notes |
|---|---|---|
| *All columns from `people`* | *Inherited* | `person_id`, `name`, `contact`, `email` are automatically present |
| `skills` | `TEXT[]` | **PostgreSQL array type** — e.g., `{'first_aid', 'driving', 'cooking'}` |
| `availability` | `tsrange` | **PostgreSQL range type** — e.g., `'[2026-07-09 08:00, 2026-07-20 18:00)'` |
| `assigned_zone` | `INTEGER` | **FK → `zones(zone_id)`** |

**Table 9 — `affected_persons`** *(INHERITS people)*

| Column | Type | Notes |
|---|---|---|
| *All columns from `people`* | *Inherited* | `person_id`, `name`, `contact`, `email` are automatically present |
| `medical_needs` | `JSONB` | Flexible structure, e.g., `{"conditions": ["diabetes"], "wheelchair": true}` |
| `shelter_id` | `INTEGER` | **FK → `shelters(shelter_id)`** |
| `registered_at` | `TIMESTAMPTZ` | Default `NOW()` |

> **Relationship:** `volunteers` and `affected_persons` both **inherit** from `people` using PostgreSQL's `INHERITS` keyword. Querying `SELECT * FROM people` returns rows from *all three tables* (people, volunteers, affected_persons). This is a uniquely PostgreSQL feature that mirrors the Java OOP inheritance (`Person` → `Student`/`Warden`) at the database level.

---

#### Group 4: Allocation & Distribution (3 tables)

**Table 10 — `resource_requests`**

| Column | Type | Notes |
|---|---|---|
| `request_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `zone_id` | `INTEGER NOT NULL` | **FK → `zones(zone_id)`** |
| `resource_id` | `INTEGER NOT NULL` | **FK → `resources(resource_id)`** |
| `quantity_requested` | `INTEGER NOT NULL` | CHECK: `quantity_requested > 0` |
| `urgency` | `VARCHAR(10) NOT NULL` | CHECK: `'critical'`, `'high'`, `'medium'`, `'low'` |
| `priority_score` | `INTEGER` | **Generated column** — auto-computed as urgency weight + quantity. No Java code needed. |
| `status` | `VARCHAR(20)` | CHECK: `'pending'`, `'fulfilled'`, `'cancelled'` |
| `requested_at` | `TIMESTAMPTZ` | Default `NOW()` |
| `fulfilled_at` | `TIMESTAMPTZ` | Set by `fn_allocate_resources()` upon fulfillment |

**Table 11 — `distribution_log`** *(partitioned by month)*

| Column | Type | Notes |
|---|---|---|
| `log_id` | `SERIAL` | Auto-incrementing ID |
| `source_shelter` | `INTEGER NOT NULL` | **FK → `shelters(shelter_id)`** — where resources came from |
| `dest_shelter` | `INTEGER NOT NULL` | **FK → `shelters(shelter_id)`** — where resources went |
| `resource_id` | `INTEGER NOT NULL` | **FK → `resources(resource_id)`** |
| `quantity` | `INTEGER NOT NULL` | CHECK: `quantity > 0` |
| `distributed_at` | `TIMESTAMPTZ NOT NULL` | Default `NOW()`; **partition key** |

> **Partitioning:** This table is **range-partitioned by `distributed_at`** (one partition per month), since it's the highest-volume table and historical queries benefit from partition pruning.

**Table 12 — `volunteer_shifts`**

| Column | Type | Notes |
|---|---|---|
| `shift_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `volunteer_id` | `INTEGER NOT NULL` | **FK → `volunteers(person_id)`** |
| `zone_id` | `INTEGER NOT NULL` | **FK → `zones(zone_id)`** |
| `duty_period` | `tsrange NOT NULL` | **PostgreSQL range type** — e.g., `'[2026-07-09 08:00, 2026-07-09 20:00)'` |
| | | **Exclusion constraint:** `EXCLUDE USING gist (volunteer_id WITH =, duty_period WITH &&)` — same volunteer cannot have overlapping shifts |

> **Relationship:** Each shift assigns one volunteer to one zone for a time range. The **exclusion constraint** is enforced by a GiST index and is a uniquely PostgreSQL feature — it guarantees at the database level that no volunteer is double-booked, even if the application has a bug.

---

#### Group 5: Donations (1 table)

**Table 13 — `donation_records`**

| Column | Type | Notes |
|---|---|---|
| `donation_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `donor_name` | `VARCHAR(100)` | Name of the donating individual/organization |
| `items` | `JSONB` | Flexible: `[{"resource_id": 5, "quantity": 100}, {"resource_id": 8, "quantity": 50}]` |
| `received_at` | `TIMESTAMPTZ` | Default `NOW()` |
| `receiving_shelter` | `INTEGER` | **FK → `shelters(shelter_id)`** |

---

#### Group 6: Audit (1 table)

**Table 14 — `audit_log`** *(trigger-populated)*

| Column | Type | Notes |
|---|---|---|
| `log_id` | `SERIAL PRIMARY KEY` | Auto-incrementing ID |
| `table_name` | `VARCHAR(50) NOT NULL` | Which table was modified |
| `operation` | `VARCHAR(10) NOT NULL` | `'INSERT'`, `'UPDATE'`, or `'DELETE'` |
| `old_values` | `JSONB` | Previous row state (NULL for INSERT) |
| `new_values` | `JSONB` | New row state (NULL for DELETE) |
| `changed_by` | `VARCHAR(100)` | `session_user` captured by trigger |
| `changed_at` | `TIMESTAMPTZ` | Default `NOW()` |

> **Note:** This table is never written to by Java. It is populated entirely by the `trg_audit_log` trigger which fires on INSERT, UPDATE, and DELETE on all critical tables.

---

### Entity-Relationship Diagram

```
disasters ──1:N──→ zones ──1:N──→ resource_requests
   │                 │
   │                 ├──N:1──→ shelters ──1:N──→ shelter_inventory ←──N:1── resources ←──N:1── resource_categories
   │                 │            │                                                              ↑ (self-ref)
   │                 │            ├──1:N──→ affected_persons (INHERITS people)                   │
   │                 │            │                                                     parent_id ┘
   │                 │            └──1:N──→ donation_records
   │                 │
   │                 └──1:N──→ volunteer_shifts ←──N:1── volunteers (INHERITS people)
   │
   └── active_period (daterange) ──→ queried with: @> CURRENT_DATE

distribution_log (partitioned by month):
   source_shelter ──→ shelters
   dest_shelter   ──→ shelters
   resource_id    ──→ resources

ALL key tables ──trigger──→ audit_log
```

### Views and Materialized Views

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
