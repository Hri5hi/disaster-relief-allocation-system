# UI Implementation Plan — Disaster Relief Allocation System

> **Source of truth:** `project_description_v2.md`
> **UI Reference:** Hostel-Management-System (layout patterns, CSS, component structure)
> **Scope:** FXML screens, controllers, CSS, components, navigation — UI only.

---

## 1. Existing Hostel UI Analysis

### 1.1 Hostel Project File Inventory

| File | Type | Purpose |
|------|------|---------|
| `login.fxml` | FXML | StackPane centered login card with warden ID + password fields |
| `dashboard.fxml` | FXML | BorderPane with header, 4 stat cards (GridPane), 3 nav buttons |
| `student.fxml` | FXML | BorderPane: left form panel (ScrollPane) + center TableView (8 cols) |
| `room.fxml` | FXML | BorderPane: left form panel + center TableView (6 cols) |
| `allocation.fxml` | FXML | BorderPane: left form panel + center TableView (7 cols) |
| `application.css` | CSS | 827-line dark theme — 13 sections covering all UI elements |
| `LoginController.java` | Controller | Async login via Task, navigates to dashboard |
| `DashboardController.java` | Controller | Loads 4 stats async, 3 navigation methods |
| `StudentController.java` | Controller | Full CRUD: add/update/delete/find/clear + TableView binding |
| `RoomController.java` | Controller | CRUD + showAvailable filter + TableView binding |
| `AllocationController.java` | Controller | CRUD + findByRoom/findByHostel filters + ComboBox for rooms |
| `Utility.java` | Base class | `changeScene()` method — loads FXML, applies CSS, sets stage |
| `Main.java` | Entry point | Launches login.fxml, sets min 900x650 |

### 1.2 Common UI Patterns Identified

1. **Layout Pattern:** `BorderPane` with `header-bar` (top), form panel (left), TableView (center)
2. **Navigation:** `Utility.changeScene()` — full scene replacement, back button to dashboard
3. **Forms:** `VBox` with `field-label` + `TextField`/`ComboBox` pairs, action buttons in `HBox` rows
4. **Tables:** `TableView` with `PropertyValueFactory` bindings, dual lists (`dataList` + `searchList`)
5. **Async Loading:** `javafx.concurrent.Task` for all DB operations, daemon threads
6. **Icons:** Ikonli FontAwesome5 (`FontIcon`) on every button
7. **Error Display:** `errorLabel` with `login-error` style class in every form
8. **CSS:** Single `application.css` loaded on every scene via `Utility.changeScene()`

---

## 2. Complete Screen Mapping

| # | Hostel Screen | Disaster Relief Screen | Action |
|---|--------------|----------------------|--------|
| 1 | `login.fxml` | `login.fxml` | **Modify** — change title, field labels (wardenId to userId) |
| 2 | `dashboard.fxml` | `dashboard.fxml` | **Redesign** — 6 stat cards, 8+ nav buttons for all modules |
| 3 | `student.fxml` | `volunteer.fxml` | **Modify** — change fields: add skills, availability, zone |
| 4 | `room.fxml` | `shelter.fxml` | **Modify** — change fields: add capacity, occupancy, coordinates, status |
| 5 | `allocation.fxml` | `resource_request.fxml` | **Redesign** — urgency, priority_score, resource type, zone |
| 6 | new | `disaster.fxml` | **New** — disaster CRUD with type, severity, daterange, status lifecycle |
| 7 | new | `zone.fxml` | **New** — zone CRUD linked to disasters and shelters |
| 8 | new | `resource.fxml` | **New** — resource + category management (hierarchical) |
| 9 | new | `inventory.fxml` | **New** — shelter_inventory view with reorder levels, low-stock alerts |
| 10 | new | `donation.fxml` | **New** — donation recording with JSONB items, donor info |
| 11 | new | `affected_person.fxml` | **New** — displaced person registration with medical needs (JSONB) |
| 12 | new | `distribution_log.fxml` | **New** — read-only log of all resource transfers |
| 13 | new | `volunteer_shift.fxml` | **New** — shift scheduling with tsrange, zone assignment |
| 14 | new | `audit_log.fxml` | **New** — read-only audit trail viewer |
| 15 | new | `analytics.fxml` | **New** — charts/reports: deficit reports, consumption trends |

---

## 3. Component Mapping

| Hostel Component | Disaster Component | Changes |
|-----------------|-------------------|---------|
| Login card (`login-card` VBox) | Login card | Change title to "Disaster Relief Allocation System", field: userId |
| Stat card (`stat-card` VBox) | Stat card | Reuse pattern. New stats: Active Disasters, Total Shelters, Pending Requests, Volunteers, Affected Persons, Low-Stock Alerts |
| Nav button (`btn-nav`) | Nav button | Add more buttons: 8-10 modules instead of 3 |
| Form panel (`form-panel`) | Form panel | Reuse layout. Change fields per screen |
| TableView + columns | TableView + columns | Reuse pattern. Change columns per entity |
| Header bar (`header-bar`) | Header bar | Reuse as-is |
| Back button (`btn-back`) | Back button | Reuse unchanged |
| Error label (`login-error`) | Error/status label | Reuse unchanged |
| Action buttons (Add/Update/Delete/Find/Clear) | Action buttons | Reuse pattern with domain-specific labels |
| Status bar (`status-bar`) | Status bar | Reuse. Add notification area for LISTEN/NOTIFY alerts |

---

## 4. Controller Mapping

| Hostel Controller | Disaster Controller | Reusable Logic | Modifications |
|------------------|--------------------|-|-|
| `LoginController` | `LoginController` | Async login Task, error handling, scene change | Change field refs, service call |
| `DashboardController` | `DashboardController` | Async stats loading, nav methods | 6 stats instead of 4, 8+ nav methods |
| `StudentController` | `VolunteerController` | CRUD pattern, TableView binding, dual-list search, clear | Different fields: skills, availability, zone |
| `RoomController` | `ShelterController` | CRUD + filter pattern, TableView binding | Fields: capacity, occupancy, coordinates, status |
| `AllocationController` | `ResourceRequestController` | CRUD + ComboBox filter pattern | Fields: zone, resource, urgency, priority_score |
| `Utility` | `Utility` | `changeScene()` fully reusable | Change package, service reference |
| `Main` | `Main` | App launch pattern | Change title, package |
| new | `DisasterController` | Use StudentController CRUD pattern | Type, severity, daterange, status lifecycle |
| new | `ZoneController` | Use RoomController CRUD pattern | Disaster dropdown, shelter dropdown |
| new | `ResourceController` | Use StudentController CRUD pattern | Category hierarchy, unit field |
| new | `InventoryController` | Use AllocationController pattern | Shelter + resource ComboBoxes |
| new | `DonationController` | Use StudentController CRUD pattern | Donor name, items, receiving shelter |
| new | `AffectedPersonController` | Use StudentController CRUD pattern | Shelter dropdown, medical needs |
| new | `DistributionLogController` | Read-only TableView, no form | Date filter only |
| new | `VolunteerShiftController` | Use AllocationController pattern | Volunteer + zone ComboBoxes, duty period |
| new | `AuditLogController` | Read-only TableView with filters | Table, operation, date filters |
| new | `AnalyticsController` | New — JavaFX Charts | BarChart, PieChart, LineChart |

---

## 5. CSS Reuse

### 5.1 Fully Reusable (No Changes Needed)

All 13 sections of the Hostel `application.css` (827 lines) can be copied as-is:
- Root and Globals (dark gradient background, font family)
- Typography (page-title, section-title, stat-number, stat-label)
- Panels and Cards (header-bar, card, form-panel, panel-card, status-bar)
- Text Fields and Inputs (dark theme fields with blue focus borders)
- Combo Box and Spinner styles
- Buttons (btn-primary, btn-success, btn-danger, btn-neutral, btn-outline, btn-nav)
- Table View (dark rows, blue headers, hover/selected states)
- Scroll Bars (minimal dark theme)
- Login Scene (login-root, login-card, btn-login)
- Dashboard (stat-card variants)
- Status Indicators (active, inactive, pending)
- Tooltips and Alerts (dialog-pane)
- Form Layout Helpers (field-label, button-row)

### 5.2 New Classes to Add

```css
/* 14. DISASTER RELIEF EXTENSIONS */
.stat-card-red    { -fx-border-color: #ef4444 #334155 #334155 #334155; -fx-border-width: 3 1 1 1; }
.stat-card-teal   { -fx-border-color: #14b8a6 #334155 #334155 #334155; -fx-border-width: 3 1 1 1; }
.stat-card-amber  { -fx-border-color: #f59e0b #334155 #334155 #334155; -fx-border-width: 3 1 1 1; }
.status-critical  { -fx-text-fill: #ef4444; -fx-font-weight: bold; }
.status-high      { -fx-text-fill: #f97316; -fx-font-weight: bold; }
.status-medium    { -fx-text-fill: #eab308; -fx-font-weight: bold; }
.status-low       { -fx-text-fill: #22c55e; -fx-font-weight: bold; }
.status-operational { -fx-text-fill: #22c55e; }
.status-full      { -fx-text-fill: #ef4444; }
.status-closed    { -fx-text-fill: #6b7280; }
.status-resolved  { -fx-text-fill: #3b82f6; }
.status-monitoring { -fx-text-fill: #f59e0b; }
.btn-nav-red      { /* same as btn-nav but #ef4444 background */ }
.btn-nav-teal     { /* same as btn-nav but #14b8a6 background */ }
.btn-nav-amber    { /* same as btn-nav but #f59e0b background */ }
.badge            { /* small count badge for alerts */ }
.chart-container  { /* wrapper for JavaFX charts */ }
.json-field       { /* styled TextArea for JSONB input */ }
.priority-bar     { /* visual priority score indicator */ }
```

### 5.3 Strategy
- Copy `application.css` to disaster project as-is
- Append new classes in section 14
- **One developer (Dev A) owns CSS exclusively**

---

## 6. Missing Screens (Required by V2 Spec)

| Screen | V2 Section | Content |
|--------|-----------|---------|
| `disaster.fxml` | 5.1 | CRUD: name, type, severity, active_period (dates), status lifecycle |
| `zone.fxml` | 5.1 | CRUD: name, severity, primary_shelter (ComboBox), coordinator_contact |
| `resource.fxml` | 5.5 | CRUD: name, category (hierarchical), unit. Category tree section |
| `inventory.fxml` | 5.5 | Shelter + resource ComboBoxes, quantity, reorder_level. Low-stock highlight |
| `donation.fxml` | 5.5 | donor_name, items (resource+qty pairs), receiving_shelter |
| `affected_person.fxml` | 5.6 | name, contact, email, shelter, medical_needs (JSON text area) |
| `distribution_log.fxml` | 5.3 | Read-only: source, dest, resource, quantity, timestamp. Date filter |
| `volunteer_shift.fxml` | 5.4 | volunteer + zone ComboBoxes, duty_period start/end |
| `audit_log.fxml` | 5.8 | Read-only: table, operation, old/new values, user, timestamp |
| `analytics.fxml` | 5.7 | Charts: zone deficit, shelter occupancy, resource consumption trends |

---

## 7. Navigation Flow

```
Login
  |
Dashboard (6 stat cards + nav grid)
  +-- Disaster Management      (disaster.fxml)
  +-- Zone Management           (zone.fxml)
  +-- Shelter Management        (shelter.fxml)
  +-- Resources                 (resource.fxml)
  +-- Inventory                 (inventory.fxml)
  +-- Resource Requests         (resource_request.fxml)
  +-- Distribution Log          (distribution_log.fxml)
  +-- Volunteers                (volunteer.fxml)
  +-- Volunteer Shifts          (volunteer_shift.fxml)
  +-- Affected Persons          (affected_person.fxml)
  +-- Donations                 (donation.fxml)
  +-- Analytics                 (analytics.fxml)
  +-- Audit Log                 (audit_log.fxml)
```

Every sub-screen has a **Back -> Dashboard** button using `Utility.changeScene()`.

---

## 8. Developer Work Split

### Principles
- No FXML file edited by both developers
- No controller edited by both developers
- Only Dev A owns `application.css` and `Utility.java`
- Both can work independently with zero merge conflicts

### Developer A — Core Infrastructure + Entity Screens

| Category | Files |
|----------|-------|
| Shared | `Main.java`, `Utility.java`, `application.css`, `login.fxml`, `LoginController`, `dashboard.fxml`, `DashboardController` |
| Disaster | `disaster.fxml`, `DisasterController.java` |
| Zone | `zone.fxml`, `ZoneController.java` |
| Shelter | `shelter.fxml`, `ShelterController.java` |
| Resource | `resource.fxml`, `ResourceController.java` |
| Inventory | `inventory.fxml`, `InventoryController.java` |
| Analytics | `analytics.fxml`, `AnalyticsController.java` |

**Total: 8 FXML files, 8 controllers, CSS, Utility, Main**

### Developer B — People, Allocation, Logging Screens

| Category | Files |
|----------|-------|
| Volunteer | `volunteer.fxml`, `VolunteerController.java` |
| Shifts | `volunteer_shift.fxml`, `VolunteerShiftController.java` |
| Affected | `affected_person.fxml`, `AffectedPersonController.java` |
| Requests | `resource_request.fxml`, `ResourceRequestController.java` |
| Dist. Log | `distribution_log.fxml`, `DistributionLogController.java` |
| Donations | `donation.fxml`, `DonationController.java` |
| Audit | `audit_log.fxml`, `AuditLogController.java` |

**Total: 7 FXML files, 7 controllers**

### Merge Conflict Prevention

| Shared Resource | Owner | Rule |
|----------------|-------|------|
| `application.css` | Dev A | Dev B does NOT edit |
| `Utility.java` | Dev A | Dev B uses but does NOT modify |
| `dashboard.fxml` | Dev A | Dev B provides nav method names; Dev A wires them |
| `DashboardController` | Dev A | Dev A adds all nav methods |
| Model classes | Split by entity | Each dev creates only their own models |

---

## 9. Development Order

### Phase 1 — Foundation (Dev A, Days 1-2)
1. Project setup: build.gradle, packages, resource directories
2. `Utility.java` adapted from hostel
3. `application.css` copied + disaster extensions added
4. `Main.java` entry point
5. `login.fxml` + `LoginController.java`
6. `dashboard.fxml` + `DashboardController.java` (placeholder stats + all nav buttons)

### Phase 2 — Core CRUD Screens (Both, Days 2-5)

**Dev A:** disaster, zone, shelter, resource, inventory

**Dev B:** volunteer, affected_person, resource_request, donation

### Phase 3 — Scheduling and Logging (Dev B, Days 5-7)
1. `volunteer_shift.fxml` + controller
2. `distribution_log.fxml` + controller
3. `audit_log.fxml` + controller

### Phase 4 — Analytics and Polish (Dev A, Days 5-7)
1. `analytics.fxml` + controller with charts
2. Dashboard stat wiring
3. CSS polish

### Phase 5 — Integration (Both, Days 7-8)
1. Wire all navigation from dashboard
2. Cross-screen testing
3. Final adjustments

---

## 10. Implementation Checklist

### Foundation
- [ ] Project skeleton (build.gradle, packages, resources)
- [ ] `Utility.java` adapted
- [ ] `application.css` ported + extensions
- [ ] `Main.java` created
- [ ] `login.fxml` + `LoginController.java`
- [ ] `dashboard.fxml` + `DashboardController.java`

### Dev A Screens
- [ ] `disaster.fxml` + `DisasterController.java`
- [ ] `zone.fxml` + `ZoneController.java`
- [ ] `shelter.fxml` + `ShelterController.java`
- [ ] `resource.fxml` + `ResourceController.java`
- [ ] `inventory.fxml` + `InventoryController.java`
- [ ] `analytics.fxml` + `AnalyticsController.java`

### Dev B Screens
- [ ] `volunteer.fxml` + `VolunteerController.java`
- [ ] `affected_person.fxml` + `AffectedPersonController.java`
- [ ] `resource_request.fxml` + `ResourceRequestController.java`
- [ ] `volunteer_shift.fxml` + `VolunteerShiftController.java`
- [ ] `distribution_log.fxml` + `DistributionLogController.java`
- [ ] `donation.fxml` + `DonationController.java`
- [ ] `audit_log.fxml` + `AuditLogController.java`

### Integration
- [ ] All nav buttons on dashboard functional
- [ ] Back buttons on all screens return to dashboard
- [ ] CSS consistent across all screens
- [ ] All tables load data asynchronously
- [ ] Error labels work on all forms
- [ ] ComboBoxes populated correctly

---

## Appendix: Target File Tree

```
src/main/
  java/com/groupa13/disasterrelief/
    controller/
      LoginController.java          (Dev A)
      DashboardController.java      (Dev A)
      DisasterController.java       (Dev A)
      ZoneController.java           (Dev A)
      ShelterController.java        (Dev A)
      ResourceController.java       (Dev A)
      InventoryController.java      (Dev A)
      AnalyticsController.java      (Dev A)
      VolunteerController.java      (Dev B)
      AffectedPersonController.java (Dev B)
      ResourceRequestController.java(Dev B)
      VolunteerShiftController.java (Dev B)
      DistributionLogController.java(Dev B)
      DonationController.java       (Dev B)
      AuditLogController.java       (Dev B)
      Utility.java                  (Dev A)
    model/
    dao/
    service/
    util/
      Main.java                     (Dev A)
  resources/
    fxml/
      login.fxml                    (Dev A)
      dashboard.fxml                (Dev A)
      disaster.fxml                 (Dev A)
      zone.fxml                     (Dev A)
      shelter.fxml                  (Dev A)
      resource.fxml                 (Dev A)
      inventory.fxml                (Dev A)
      analytics.fxml                (Dev A)
      volunteer.fxml                (Dev B)
      affected_person.fxml          (Dev B)
      resource_request.fxml         (Dev B)
      volunteer_shift.fxml          (Dev B)
      distribution_log.fxml         (Dev B)
      donation.fxml                 (Dev B)
      audit_log.fxml                (Dev B)
    css/
      application.css               (Dev A)
    images/
```
