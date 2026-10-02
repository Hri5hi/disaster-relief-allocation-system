# Dev A Wave 2 Study Guide

This document explains the completed **Dev A work for Wave 2** of the Disaster
Relief Resource Allocation System (DRAS). It covers only Dev A's core domain,
controller, inventory, listener-boundary, and test responsibilities.

Wave 0 and Wave 1 are explained separately in
[WAVE_0_1_STUDY_GUIDE.md](WAVE_0_1_STUDY_GUIDE.md). Dev B's people modules and
Dev C's operations/security modules are outside this guide.

## 1. Big picture

Wave 1 created the PostgreSQL schema and shared database infrastructure. Wave 2
uses that foundation to build the Java backend for the six core tables and
connect the five Dev A JavaFX screens to real services.

```text
JavaFX controller
    │
    │ one asynchronous service call per Task
    ▼
Core service
    │ validation, transaction boundary, safe error message
    ▼
TransactionRunner / Database.inTransaction(...)
    │
    │ one transaction-scoped JDBC Connection
    ▼
Core DAO
    │ PreparedStatement only
    ▼
PostgreSQL core tables from V001–V003
```

The Wave 2 result is:

- immutable Java models for disasters, zones, shelters, categories, resources,
  and shelter inventory;
- prepared-JDBC DAOs for all core persistence operations;
- transactional services with validation and safe error translation;
- database-backed asynchronous behavior in all five Dev A controllers;
- atomic inventory replacement and strict low-stock filtering;
- a mockable low-stock notification boundary plus an unpooled PostgreSQL
  `LISTEN` implementation; and
- PostgreSQL 15 integration, concurrency, service, listener, and controller
  contract tests.

No Dev B or Dev C implementation file was changed for this work.

## 2. What Wave 2 builds on

Dev A Wave 2 depends on the following Wave 1 contracts:

| Existing contract | How Wave 2 uses it |
|---|---|
| `Database.inTransaction(UserSession, SqlWork<T>)` | Application services run DAO work in one safe transaction and preserve the future zone/RLS context. |
| `DatabaseException` | Services inspect SQLState/constraint metadata for logs while returning safe UI messages. |
| `ServiceResult<T>` | Every controller receives `success`, `value`, and a user-safe `message`. |
| Migrations V001–V003 | DAOs target the six core tables, enums, composite coordinate type, checks, FKs, and indexes already created in Wave 1. |
| Integer database IDs | Java models/services use `int`; controllers format display values such as `D1`, `S2`, and `R3`. |
| Testcontainers support | Wave 2 tests start isolated PostgreSQL 15 and migrate it from an empty database. |

Wave 2 did not edit V001, V002, or V003. A merged Flyway migration remains
immutable.

## 3. Wave 2 architecture and responsibilities

### Models

Models represent database values without JavaFX properties or JDBC objects.
They use Java records/enums, integer IDs, `LocalDate`, and `OffsetDateTime`.

### DAOs

DAOs contain SQL and row mapping. They receive an existing transaction-scoped
`Connection`; they do not open connections, commit, roll back, retain a
connection, or contain JavaFX logic.

### Services

Services validate input, open the transaction boundary, call DAOs, coordinate
multi-step rules, and translate expected database failures into
`ServiceResult` messages.

### Controllers

Controllers validate presentation input and start JavaFX `Task` objects. A
task calls one service method. `setOnSucceeded` updates controls on the JavaFX
thread, while `setOnFailed` logs unexpected failures and displays a generic
message without showing SQL or stack traces.

### PostgreSQL

PostgreSQL remains the final integrity authority. Java validation improves the
user experience, but database checks and FKs still reject invalid or racing
writes.

## 4. Core model files

All Wave 2 core models are under
`src/main/java/com/groupa13/disasterrelief/model/core/`.

| File | Purpose |
|---|---|
| [ActivePeriod.java](src/main/java/com/groupa13/disasterrelief/model/core/ActivePeriod.java) | Represents the PostgreSQL `daterange` as an inclusive start and exclusive end. It rejects a non-increasing bounded period and formats the value for display. |
| [Coordinates.java](src/main/java/com/groupa13/disasterrelief/model/core/Coordinates.java) | Represents latitude/longitude and rejects non-finite or out-of-range values. |
| [Disaster.java](src/main/java/com/groupa13/disasterrelief/model/core/Disaster.java) | Immutable disaster row: ID, name, type, severity, active period, status, and creation time. |
| [DisasterStatus.java](src/main/java/com/groupa13/disasterrelief/model/core/DisasterStatus.java) | Java enum matching PostgreSQL values `active`, `monitoring`, `resolved`, and `closed`. |
| [Severity.java](src/main/java/com/groupa13/disasterrelief/model/core/Severity.java) | Java enum matching `low`, `medium`, `high`, and `critical`. |
| [Zone.java](src/main/java/com/groupa13/disasterrelief/model/core/Zone.java) | Zone row plus joined disaster and optional primary-shelter display names. |
| [Shelter.java](src/main/java/com/groupa13/disasterrelief/model/core/Shelter.java) | Shelter row with optional coordinates, read-only current occupancy, and an occupancy-ratio helper. |
| [ShelterStatus.java](src/main/java/com/groupa13/disasterrelief/model/core/ShelterStatus.java) | Java enum matching `operational`, `full`, and `closed`. |
| [ResourceCategory.java](src/main/java/com/groupa13/disasterrelief/model/core/ResourceCategory.java) | One category-tree result with parent ID, depth, and full path such as `Medical > First Aid`. |
| [Resource.java](src/main/java/com/groupa13/disasterrelief/model/core/Resource.java) | Resource row with category ID, resolved category path, and unit. |
| [ShelterInventory.java](src/main/java/com/groupa13/disasterrelief/model/core/ShelterInventory.java) | Joined shelter/resource inventory row. `lowStock()` is true only when `quantity < reorderLevel`. |
| [EntityOption.java](src/main/java/com/groupa13/disasterrelief/model/core/EntityOption.java) | Stable integer ID plus display label for controller ComboBoxes. Controllers never parse IDs from a label. |
| [LowStockAlert.java](src/main/java/com/groupa13/disasterrelief/model/core/LowStockAlert.java) | Bounded notification DTO and strict parser for the future V006 JSON payload. It accepts snake_case or camelCase field names and rejects missing, duplicate, oversized, negative, or malformed values. |

## 5. Core DAO files and SQL behavior

All core DAOs are under
`src/main/java/com/groupa13/disasterrelief/dao/core/`.

| File | Main operations and important behavior |
|---|---|
| [JdbcSupport.java](src/main/java/com/groupa13/disasterrelief/dao/core/JdbcSupport.java) | Central helpers for nullable integers/text and PostgreSQL `daterange` binding through `PGobject`. |
| [DisasterDao.java](src/main/java/com/groupa13/disasterrelief/dao/core/DisasterDao.java) | Create, list, find, update, delete, and list open-disaster options. Reads range boundaries with `lower(...)`/`upper(...)`. |
| [ZoneDao.java](src/main/java/com/groupa13/disasterrelief/dao/core/ZoneDao.java) | Create/list/find/update/delete zones, list by disaster, and assign/clear the primary shelter. Joins disaster and shelter names for display. |
| [ShelterDao.java](src/main/java/com/groupa13/disasterrelief/dao/core/ShelterDao.java) | Create/list/find/update/delete shelters and list options. Coordinates use static `ROW(?, ?)::coordinates`; occupancy is never written by create/update methods. Delete succeeds only when occupancy is zero. |
| [ResourceCategoryDao.java](src/main/java/com/groupa13/disasterrelief/dao/core/ResourceCategoryDao.java) | Create/find/update/delete categories and retrieve the entire ordered hierarchy with one recursive CTE. Hierarchy moves lock categories in deterministic order before checking descendants, preventing concurrent moves from creating a cycle. |
| [ResourceDao.java](src/main/java/com/groupa13/disasterrelief/dao/core/ResourceDao.java) | Create/list/find/update/delete resources and list resource options. A recursive CTE supplies each resource's complete category path. |
| [InventoryDao.java](src/main/java/com/groupa13/disasterrelief/dao/core/InventoryDao.java) | List all inventory, list strictly below-threshold inventory, find/delete one composite-key row, and atomically replace quantity/threshold with one `INSERT ... ON CONFLICT DO UPDATE`. |

Every value is bound through a `PreparedStatement`. The only ordinary
`Statement` in Dev A Wave 2 executes the constant command
`LISTEN low_stock_alert`; it never contains user input.

### Atomic inventory replacement

The inventory write uses one statement:

```sql
INSERT INTO shelter_inventory (
    shelter_id, resource_id, quantity, reorder_level
)
VALUES (?, ?, ?, ?)
ON CONFLICT (shelter_id, resource_id)
DO UPDATE SET quantity = EXCLUDED.quantity,
              reorder_level = EXCLUDED.reorder_level,
              last_updated = now()
RETURNING shelter_id, resource_id;
```

This means two concurrent writers cannot create duplicate rows for the same
`(shelter_id, resource_id)` pair. The method is deliberately named
`upsertReplacement`: it sets an absolute quantity. Dev C's later donation
function owns incremental stock additions.

### Category traversal and cycle safety

The category DAO uses a recursive CTE that starts at root categories, carries
an integer path and a name path, and returns parents before children in one
query. It avoids an N+1 query pattern.

Before moving a category, the DAO locks hierarchy rows in ID order. The service
then asks whether the proposed parent is the category itself or one of its
descendants. Locking is important because two concurrent moves that look safe
individually must not commit a combined cycle.

## 6. Service and transaction files

All files below are under
`src/main/java/com/groupa13/disasterrelief/service/core/`.

| File | Purpose |
|---|---|
| [TransactionRunner.java](src/main/java/com/groupa13/disasterrelief/service/core/TransactionRunner.java) | Injectable transaction boundary. Application services delegate to `Database.inTransaction(UserSession, ...)`; tests can use an explicit datasource without changing service logic. |
| [CoreServiceSupport.java](src/main/java/com/groupa13/disasterrelief/service/core/CoreServiceSupport.java) | Shared required/optional text checks, positive-ID validation, and safe database-failure logging/result creation. |
| [DisasterService.java](src/main/java/com/groupa13/disasterrelief/service/core/DisasterService.java) | Disaster CRUD, lookup, open-disaster options, active-period validation, and safe delete-in-use messages. |
| [ZoneService.java](src/main/java/com/groupa13/disasterrelief/service/core/ZoneService.java) | Zone CRUD, disaster filtering, duplicate-name translation, and explicit primary-shelter assignment/clearing after validating both IDs in one transaction. |
| [ShelterService.java](src/main/java/com/groupa13/disasterrelief/service/core/ShelterService.java) | Shelter CRUD/options, coordinate/capacity validation, read-only occupancy handling, and occupied-shelter deletion protection. |
| [ResourceCategoryService.java](src/main/java/com/groupa13/disasterrelief/service/core/ResourceCategoryService.java) | Category create/read/tree/update/delete and descendant-cycle rejection. |
| [ResourceService.java](src/main/java/com/groupa13/disasterrelief/service/core/ResourceService.java) | Resource CRUD/options, text validation, category-FK error translation, and in-use delete messages. |
| [InventoryService.java](src/main/java/com/groupa13/disasterrelief/service/core/InventoryService.java) | Inventory list/find/delete, strict low-stock list, nonnegative validation, and absolute replacement UPSERT. |
| [LowStockNotificationListener.java](src/main/java/com/groupa13/disasterrelief/service/core/LowStockNotificationListener.java) | Mockable `start(Consumer<LowStockAlert>)`/`close()` boundary consumed by the inventory controller. |
| [PostgresLowStockNotificationListener.java](src/main/java/com/groupa13/disasterrelief/service/core/PostgresLowStockNotificationListener.java) | Real PostgreSQL `LISTEN low_stock_alert` implementation using a dedicated unpooled connection, a daemon worker, bounded reconnect backoff, bounded payload parsing, and clean shutdown. |
| [ServiceResult.java](src/main/java/com/groupa13/disasterrelief/service/core/ServiceResult.java) | Wave 1 shared result contract reused by all Wave 2 services. |

### Expected error flow

```text
Invalid UI/service input
    └─ ServiceResult.failure("short validation message")

Expected PostgreSQL constraint failure
    ├─ SQLState and constraint name retained for logs
    └─ ServiceResult.failure("safe domain message")

Unexpected runtime failure
    ├─ JavaFX Task enters setOnFailed
    ├─ exception is logged
    └─ UI receives a generic operation-failed message
```

Raw SQL, SQLState codes, constraint internals, credentials, and stack traces are
not displayed to users.

## 7. Controller replacements

Wave 2 updated exactly the five controllers owned by Dev A. Their existing FXML
files were not changed.

| File | Database-backed behavior added |
|---|---|
| [DisasterController.java](src/main/java/com/groupa13/disasterrelief/controller/DisasterController.java) | Asynchronous list/create/update/delete/find; integer-to-display ID formatting; required-name and exclusive-end date validation; confirmation before resolved/closed status changes. |
| [ZoneController.java](src/main/java/com/groupa13/disasterrelief/controller/ZoneController.java) | Asynchronous zone CRUD/find, open-disaster options, shelter options, stable `EntityOption` IDs, and joined disaster/primary-shelter display values. |
| [ShelterController.java](src/main/java/com/groupa13/disasterrelief/controller/ShelterController.java) | Asynchronous shelter CRUD/find, address/capacity/coordinate validation, database occupancy display, and no occupancy write path. Existing geocoding now uses `HttpClient.sendAsync` rather than a database Task. |
| [ResourceController.java](src/main/java/com/groupa13/disasterrelief/controller/ResourceController.java) | Asynchronous category loading/add/subcategory/delete plus resource CRUD/find. The tree and ComboBox use real category records and stable IDs. |
| [InventoryController.java](src/main/java/com/groupa13/disasterrelief/controller/InventoryController.java) | Asynchronous option loading, inventory list/find/delete/replacement UPSERT, numeric validation, low-stock filtering/badge, row highlighting, listener startup/close, and notification-driven targeted refresh. |

All five controllers removed their sample database rows and placeholder backend
paths. Controllers contain no JDBC imports and never call a DAO directly.

## 8. Low-stock notification flow

Dev A owns the Java listener boundary and inventory-controller integration. Dev
C owns the database trigger that will emit the notification in V006.

```text
Dev C V006 trigger (later)
    │ pg_notify('low_stock_alert', JSON payload)
    ▼
Dedicated PGSimpleDataSource connection
    │ LISTEN low_stock_alert
    ▼
PostgresLowStockNotificationListener daemon worker
    │ parse bounded payload
    ▼
InventoryController callback
    │ Platform.runLater(...)
    ▼
InventoryService.findInventory(shelterId, resourceId)
    │
    ▼
Replace the affected table row with current database state
```

The notification is only a refresh hint. The controller does not trust the
quantity inside the payload as the final value; it rereads the affected row
from PostgreSQL.

The listener never borrows a Hikari transaction connection. It uses one
dedicated `PGSimpleDataSource` connection because `LISTEN` is a long-lived
session operation. If the connection fails, retry delay grows from 250 ms to a
bounded maximum of 5 seconds. `close()` stops the worker and closes the active
connection.

## 9. Tests added or updated

Wave 2 added 13 focused tests. Together with the nine Wave 1 tests, the clean
repository suite contains 22 passing tests.

| File | What it proves |
|---|---|
| [CoreDomainIntegrationTest.java](src/test/java/com/groupa13/disasterrelief/core/CoreDomainIntegrationTest.java) | PostgreSQL-backed CRUD and safe constraint messages; primary-shelter assign/clear; read-only occupancy and occupied-delete protection; ordered category paths; category-cycle rejection; in-use deletes; resource CRUD; atomic inventory replacement; strict low-stock filtering; negative-value rejection; and concurrent UPSERT correctness. |
| [CoreServiceTest.java](src/test/java/com/groupa13/disasterrelief/core/CoreServiceTest.java) | Invalid input is rejected before the transaction/DAO boundary, date/coordinate messages are safe, and database failures do not leak SQLState or internal messages. |
| [LowStockNotificationTest.java](src/test/java/com/groupa13/disasterrelief/core/LowStockNotificationTest.java) | Valid snake_case/camelCase alert parsing, malformed/duplicate/negative/oversized payload rejection, and a mockable/closable listener interface. |
| [CoreControllerContractTest.java](src/test/java/com/groupa13/disasterrelief/core/CoreControllerContractTest.java) | All five controllers use asynchronous services; contain no sample backend, TODO, stack-trace, JDBC, or `DriverManager` path; still implement every FXML action; keep occupancy read-only; and reread inventory after notifications. |
| [CorePlatformIntegrationTest.java](src/test/java/com/groupa13/disasterrelief/core/CorePlatformIntegrationTest.java) | Updated so the Dev A V001–V003 contract is asserted as a required subset while later Wave 2 migrations, such as Dev B's V004, may also be present. |

The existing support and configuration tests remain in use:

- [PostgresTestSupport.java](src/test/java/com/groupa13/disasterrelief/support/PostgresTestSupport.java)
- [DatabaseConfigTest.java](src/test/java/com/groupa13/disasterrelief/core/DatabaseConfigTest.java)

## 10. Acceptance gates and verification

[GATES.md](GATES.md) was updated to record the Dev A Wave 2 completion
contract. Its six gates cover:

1. valid executable gate definitions;
2. PostgreSQL core persistence, hierarchy, inventory, threshold, and
   concurrency behavior;
3. service validation/error translation and listener behavior;
4. all five controller replacements;
5. the complete clean test/build; and
6. the final ownership/domain review.

The final commands passed:

```bash
./gradlew test --tests 'com.groupa13.disasterrelief.core.CoreDomainIntegrationTest'
./gradlew test --tests 'com.groupa13.disasterrelief.core.CoreServiceTest' \
    --tests 'com.groupa13.disasterrelief.core.LowStockNotificationTest'
./gradlew test --tests 'com.groupa13.disasterrelief.core.CoreControllerContractTest'
./gradlew clean test build
```

Final measured result:

```text
22 tests
0 failures
0 errors
0 skipped
6/6 Wave 2 acceptance gates met
```

The complete gate set was also rerun unchanged with the gate verifier after the
final review.

## 11. Complete Dev A Wave 2 file list

### Created model files

```text
src/main/java/com/groupa13/disasterrelief/model/core/ActivePeriod.java
src/main/java/com/groupa13/disasterrelief/model/core/Coordinates.java
src/main/java/com/groupa13/disasterrelief/model/core/Disaster.java
src/main/java/com/groupa13/disasterrelief/model/core/DisasterStatus.java
src/main/java/com/groupa13/disasterrelief/model/core/EntityOption.java
src/main/java/com/groupa13/disasterrelief/model/core/LowStockAlert.java
src/main/java/com/groupa13/disasterrelief/model/core/Resource.java
src/main/java/com/groupa13/disasterrelief/model/core/ResourceCategory.java
src/main/java/com/groupa13/disasterrelief/model/core/Severity.java
src/main/java/com/groupa13/disasterrelief/model/core/Shelter.java
src/main/java/com/groupa13/disasterrelief/model/core/ShelterInventory.java
src/main/java/com/groupa13/disasterrelief/model/core/ShelterStatus.java
src/main/java/com/groupa13/disasterrelief/model/core/Zone.java
```

### Created DAO files

```text
src/main/java/com/groupa13/disasterrelief/dao/core/DisasterDao.java
src/main/java/com/groupa13/disasterrelief/dao/core/InventoryDao.java
src/main/java/com/groupa13/disasterrelief/dao/core/JdbcSupport.java
src/main/java/com/groupa13/disasterrelief/dao/core/ResourceCategoryDao.java
src/main/java/com/groupa13/disasterrelief/dao/core/ResourceDao.java
src/main/java/com/groupa13/disasterrelief/dao/core/ShelterDao.java
src/main/java/com/groupa13/disasterrelief/dao/core/ZoneDao.java
```

### Created service/listener files

```text
src/main/java/com/groupa13/disasterrelief/service/core/CoreServiceSupport.java
src/main/java/com/groupa13/disasterrelief/service/core/DisasterService.java
src/main/java/com/groupa13/disasterrelief/service/core/InventoryService.java
src/main/java/com/groupa13/disasterrelief/service/core/LowStockNotificationListener.java
src/main/java/com/groupa13/disasterrelief/service/core/PostgresLowStockNotificationListener.java
src/main/java/com/groupa13/disasterrelief/service/core/ResourceCategoryService.java
src/main/java/com/groupa13/disasterrelief/service/core/ResourceService.java
src/main/java/com/groupa13/disasterrelief/service/core/ShelterService.java
src/main/java/com/groupa13/disasterrelief/service/core/TransactionRunner.java
src/main/java/com/groupa13/disasterrelief/service/core/ZoneService.java
```

`ServiceResult.java` was created in Wave 1 and reused unchanged in Wave 2.

### Updated controllers

```text
src/main/java/com/groupa13/disasterrelief/controller/DisasterController.java
src/main/java/com/groupa13/disasterrelief/controller/InventoryController.java
src/main/java/com/groupa13/disasterrelief/controller/ResourceController.java
src/main/java/com/groupa13/disasterrelief/controller/ShelterController.java
src/main/java/com/groupa13/disasterrelief/controller/ZoneController.java
```

### Created or updated tests

```text
src/test/java/com/groupa13/disasterrelief/core/CoreControllerContractTest.java
src/test/java/com/groupa13/disasterrelief/core/CoreDomainIntegrationTest.java
src/test/java/com/groupa13/disasterrelief/core/CoreServiceTest.java
src/test/java/com/groupa13/disasterrelief/core/LowStockNotificationTest.java
src/test/java/com/groupa13/disasterrelief/core/CorePlatformIntegrationTest.java (updated)
```

### Acceptance and study documentation

```text
GATES.md (updated for Wave 2)
DEV_A_WAVE_2_STUDY_GUIDE.md (this file)
```

## 12. What Dev A Wave 2 intentionally does not contain

The following work belongs to other developers or later waves and was not
implemented here:

- Dev B's people, volunteer, affected-person, and shift DAOs/services/controllers.
- Dev C's V005–V008 migrations and operations tables.
- Authentication, BCrypt login, navigation authorization, and seed accounts.
- Resource requests, allocation, donations, distribution logs, audit, RLS, and
  analytics.
- The V006 `trg_low_stock_alert` database trigger and its final Dev C dashboard
  alert adapter.
- Editing Dev B/C-owned migrations or controller files.

The Java listener is complete and ready for the V006 trigger, but it does not
invent or duplicate Dev C's database-owned trigger.

## 13. Recommended study order

1. Review the Wave 1 transaction contract in
   [WAVE_0_1_STUDY_GUIDE.md](WAVE_0_1_STUDY_GUIDE.md).
2. Read the core records/enums to understand Java/database type mapping.
3. Read `JdbcSupport`, then one simple DAO such as `DisasterDao`.
4. Read `ResourceCategoryDao` and `InventoryDao` for the recursive CTE,
   hierarchy lock, UPSERT, and strict threshold query.
5. Read `TransactionRunner`, `CoreServiceSupport`, and the corresponding
   services to see validation and error translation.
6. Trace one screen end-to-end, such as
   `InventoryController → InventoryService → InventoryDao → PostgreSQL`.
7. Read the listener interface, PostgreSQL listener, and notification refresh
   path together.
8. Finish with the four Wave 2 test classes and `GATES.md`; they show how every
   completion claim is proved.

## 14. Quick self-check questions

1. Why do DAOs receive a `Connection` instead of opening their own connection?
2. Why does `TransactionRunner.application()` pass the current `UserSession` to
   `Database.inTransaction`?
3. Why is `daterange` bound with a `PGobject` instead of string-concatenated SQL?
4. Why must the inventory method be called replacement rather than increment?
5. How does `ON CONFLICT` prevent duplicate inventory rows during concurrent
   writes?
6. Why is equality (`quantity == reorder_level`) not considered low stock?
7. Why does a category move lock the hierarchy before checking descendants?
8. Why is shelter occupancy displayed but never sent by the controller to the
   service?
9. Why does the low-stock listener use an unpooled connection?
10. Why does the inventory controller reread PostgreSQL after receiving a
    notification?
11. Which part of the low-stock feature remains Dev C's responsibility?
12. Which tests prove that the five controllers no longer use sample backend
    data?

If you can answer these questions and trace one CRUD request from controller to
PostgreSQL and back, you understand Dev A's Wave 2 implementation.
