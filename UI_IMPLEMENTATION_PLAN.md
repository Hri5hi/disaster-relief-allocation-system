# UI Implementation Plan — Disaster Relief Allocation System

> **Source of truth:** `project_description_v2.md`  
> **UI Reference:** Hostel-Management-System (layout patterns, CSS, component structure)  
> **Key Architecture Upgrade:** Role-Based Access Control (RBAC) & Tailored Dashboards for 5 Target User Roles  
> **Scope:** FXML screens, controllers, CSS, components, navigation, role-based visibility — UI layer.

---

## 1. Role-Based Access Control (RBAC) & 5 Target User Dashboards

Per Section 4 of `project_description_v2.md`, the system supports **5 distinct user roles**. Rather than showing a static dashboard with identical buttons for everyone, the UI dynamically adapts stats, navigation cards, and feature access based on the logged-in user's role.

### 1.1 User Role Matrix & Screen Access

| Role | Primary Responsibility | Visible Dashboard Stats | Accessible Screens | Access Level |
|---|---|---|---|---|
| **1. System Administrator** (`SYSTEM_ADMIN`) | System configuration, disaster setup, overall supervision | Active Disasters, Total Shelters, Pending Requests, Active Volunteers, Audit Events | **ALL 15 Screens** (Full Unrestricted Access) | Unrestricted |
| **2. Zone Coordinator** (`ZONE_COORDINATOR`) | Zone-specific operations, requesting supplies, registering displaced people | Assigned Zone Severity, Zone Shelter Occupancy %, Pending Zone Requests, Affected Persons in Zone | `dashboard.fxml`, `resource_request.fxml`, `affected_person.fxml`, `shelter.fxml` (read-only), `analytics.fxml` (zone deficit tab) | Restricted to assigned zone via Row-Level Security (RLS) |
| **3. Warehouse / Inventory Manager** (`WAREHOUSE_MANAGER`) | Stock tracking, processing donations, fulfilling transfers | Low-Stock Alerts (`LISTEN/NOTIFY`), Pending Fulfillments, Total Items in Stock, Recent Donations | `dashboard.fxml`, `inventory.fxml`, `resource_request.fxml` (fulfillment mode), `donation.fxml`, `distribution_log.fxml` | Access to inventory, requests, donations & logs |
| **4. Volunteer Coordinator** (`VOLUNTEER_COORDINATOR`) | Volunteer registration, skill matching, shift scheduling | Total Volunteers, Shifts Scheduled Today, Unassigned Volunteers, Skill Types Count | `dashboard.fxml`, `volunteer.fxml`, `volunteer_shift.fxml` | Access to volunteers & shifts |
| **5. Reporting / Analytics User** (`ANALYTICS_USER`) | Executive decision-making, trend analysis, deficit tracking | Overall Deficit Severity, Critical Zone Count, 7-Day Supply Consumption Rate, Shelter Utilization % | `dashboard.fxml`, `analytics.fxml` (full suite), `distribution_log.fxml` (read-only), `shelter.fxml` (occupancy view) | Read-only access to views & materialized views |

---

## 2. Dynamic Role-Based Dashboard Architecture

### 2.1 Session Management (`UserSession.java`)
A singleton session manager holds the active session context upon login:
```java
public class UserSession {
    private String userId;
    private String userName;
    private UserRole role; // ENUM: SYSTEM_ADMIN, ZONE_COORDINATOR, WAREHOUSE_MANAGER, VOLUNTEER_COORDINATOR, ANALYTICS_USER
    private Integer assignedZoneId; // Non-null for ZONE_COORDINATOR
    
    // Singleton instance, getters, setters, clear()
}
```

### 2.2 Dashboard Adaptation Mechanism (`DashboardController.java`)
Upon loading `dashboard.fxml`:
1. `DashboardController` reads `UserSession.getInstance()`.
2. Sets top header badge: `"Logged in as: [UserName] ([Role])"`.
3. Loads **6 Role-Tailored Stat Cards** relevant to that role's priorities.
4. Filters the **Navigation Grid**: Only renders navigation cards/buttons the user is authorized to access (using `Node.setVisible(false)` and `Node.setManaged(false)` or dynamically populating a `FlowPane`).

---

## 3. Detailed Screen Breakdown per Target User

### 3.1 System Administrator Dashboard (`SYSTEM_ADMIN`)
* **Dashboard Header:** "System Administration & Overview"
* **Stat Widgets:** Active Disasters | Total Shelters | Total Pending Requests | Total Volunteers | Total Donations | System Audit Events
* **Navigation Cards:**
  - 🚨 Disaster Management (`disaster.fxml`)
  - 🗺️ Zone Management (`zone.fxml`)
  - 🏫 Shelter Management (`shelter.fxml`)
  - 📦 Resource Master & Categories (`resource.fxml`)
  - 📊 Inventory Overview (`inventory.fxml`)
  - 📋 Resource Requests (`resource_request.fxml`)
  - 🚚 Distribution Log (`distribution_log.fxml`)
  - 🤝 Volunteer Directory (`volunteer.fxml`)
  - 📅 Volunteer Shifts (`volunteer_shift.fxml`)
  - 🏠 Affected Persons (`affected_person.fxml`)
  - 🎁 Donation Intake (`donation.fxml`)
  - 📈 Analytics & Reports (`analytics.fxml`)
  - 📜 Audit Log Viewer (`audit_log.fxml`)

### 3.2 Zone Coordinator Dashboard (`ZONE_COORDINATOR`)
* **Dashboard Header:** "Zone Operations — [Zone Name]"
* **Stat Widgets:** Zone Severity Level | Assigned Shelters | Pending Zone Requests | Affected Persons Registered
* **Navigation Cards:**
  - 📋 Submit & Track Resource Requests (`resource_request.fxml` — pre-filtered to current zone)
  - 🏠 Register Displaced Persons (`affected_person.fxml` — pre-filtered to zone shelters with JSONB medical needs)
  - 🏫 View Zone Shelters (`shelter.fxml` — read-only zone capacity)
  - 📊 Zone Deficit Status (`analytics.fxml` — zone deficit breakdown tab)

### 3.3 Warehouse / Inventory Manager Dashboard (`WAREHOUSE_MANAGER`)
* **Dashboard Header:** "Inventory & Fulfillment Center"
* **Stat Widgets:** Low-Stock Alerts (`LISTEN/NOTIFY` badge) | Pending Fulfillments | Total Stock Units | Donations Received Today
* **Navigation Cards:**
  - 📊 Shelter Inventory Management (`inventory.fxml` — edit stock, set reorder levels)
  - ⚡ Fulfill Resource Requests (`resource_request.fxml` — triggers `fn_allocate_resources()` atomic transfer)
  - 🎁 Record Incoming Donations (`donation.fxml` — donor info + JSONB item payloads with UPSERT)
  - 🚚 Resource Transfer Log (`distribution_log.fxml` — read-only log of stock transfers)

### 3.4 Volunteer Coordinator Dashboard (`VOLUNTEER_COORDINATOR`)
* **Dashboard Header:** "Volunteer Management & Deployment"
* **Stat Widgets:** Total Volunteers Registered | Active Shifts Today | Available Unassigned Volunteers | Skills Covered
* **Navigation Cards:**
  - 🤝 Volunteer Directory (`volunteer.fxml` — search by `TEXT[]` skills array like `{'first_aid', 'driving'}`)
  - 📅 Shift Scheduling (`volunteer_shift.fxml` — conflict-free shift assignment with `tsrange` exclusion constraints)
  - 🔍 Volunteer Availability View (`volunteer.fxml` — availability search tab)

### 3.5 Reporting / Analytics User Dashboard (`ANALYTICS_USER`)
* **Dashboard Header:** "Executive Analytics & Decision Support"
* **Stat Widgets:** Critical Zone Count | Total Resource Deficit | 7-Day Supply Consumption Rate | Shelter Occupancy Rate
* **Navigation Cards:**
  - 📈 Analytics Suite (`analytics.fxml` — Bar/Line/Pie charts, 7-day moving averages, category roll-ups)
  - 🏫 Shelter Occupancy Dashboard (`v_shelter_dashboard` view)
  - 🗺️ Zone Deficit Summary (`fn_zone_deficit_report()` output)
  - 🚚 Distribution Analytics (`mv_daily_distribution_stats` materialized view)

---

## 4. Complete Screen Inventory (14 FXML Screens)

| # | Screen FXML | Purpose | Primary Target Roles | Key Controls & Layout |
|---|------------|---------|----------------------|----------------------|
| 1 | `login.fxml` | User authentication & role selection | All Roles | StackPane card, User ID, Password, Role selector |
| 2 | `dashboard.fxml` | Dynamic role-adapted dashboard | All Roles (Custom View) | Header bar, 4-6 Stat Cards, Dynamic Nav FlowPane |
| 3 | `disaster.fxml` | Disaster lifecycle & active periods | Admin | TableView, daterange picker, status ComboBox |
| 4 | `zone.fxml` | Zone creation & primary shelter assignment | Admin | TableView, disaster dropdown, shelter dropdown |
| 5 | `shelter.fxml` | Shelter registration, capacity, occupancy & location | Admin, Zone Coord, Analytics | TableView, occupancy bar, address text field, lat/lng fields, *"Auto-fill coordinates"* button (Nominatim API) |
| 6 | `resource.fxml` | Resource catalog & hierarchical categories | Admin, Warehouse Mgr | Category TreeView, resource TableView, unit input |
| 7 | `inventory.fxml` | Shelter inventory & reorder levels | Warehouse Mgr, Admin | TableView with low-stock highlight, reorder level editor |
| 8 | `resource_request.fxml` | Priority request submission & fulfillment | Zone Coord, Warehouse Mgr, Admin | Priority score column, urgency tags, Fulfill button |
| 9 | `volunteer.fxml` | Volunteer directory & skill matching | Volunteer Coord, Admin | TableView, skill tags (`TEXT[]`), availability search |
| 10 | `volunteer_shift.fxml` | Conflict-free shift scheduling | Volunteer Coord, Admin | Date/time pickers (`tsrange`), zone dropdown |
| 11 | `affected_person.fxml` | Displaced person registration | Zone Coord, Admin | Form panel, medical needs JSON text area |
| 12 | `donation.fxml` | Donation intake & stock integration | Warehouse Mgr, Admin | Donor info, item table builder (JSONB), receive button |
| 13 | `distribution_log.fxml` | Read-only resource movement history | Warehouse Mgr, Admin, Analytics | Partitioned date range filter, transfer TableView |
| 14 | `audit_log.fxml` | System mutation audit trail | Admin | Table, operation, user & date filters, JSON diff viewer |
| 15 | `analytics.fxml` | Executive reports & chart dashboards | Analytics User, Admin, Zone Coord | JavaFX BarChart, LineChart, PieChart, Deficit table |

---

## 5. Screen Security & Navigation Guards

To prevent unauthorized manual scene changes or button bypassing:

```java
public class Utility {
    
    public static void changeScene(Event event, String fxmlPath, String title, UserRole... allowedRoles) {
        UserRole currentRole = UserSession.getInstance().getRole();
        
        // Authorization Guard
        if (allowedRoles.length > 0 && !Arrays.asList(allowedRoles).contains(currentRole)) {
            showErrorAlert("Access Denied", "Your role (" + currentRole + ") does not have permission to access this screen.");
            return;
        }
        
        // Load FXML and apply CSS
        // ...
    }
}
```

---

## 6. CSS Extensions for Role UI & Status Badges

Add the following to section 14 of `application.css`:

```css
/* ==========================================================================
   14. ROLE-BASED DASHBOARD & STATUS EXTENSIONS
   ========================================================================== */

/* Role Badges */
.role-badge-admin       { -fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #ef4444; -fx-border-color: #ef4444; -fx-border-radius: 3; -fx-padding: 3 8; }
.role-badge-zone        { -fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #f59e0b; -fx-border-color: #f59e0b; -fx-border-radius: 3; -fx-padding: 3 8; }
.role-badge-warehouse   { -fx-background-color: rgba(20, 184, 166, 0.15); -fx-text-fill: #14b8a6; -fx-border-color: #14b8a6; -fx-border-radius: 3; -fx-padding: 3 8; }
.role-badge-volunteer   { -fx-background-color: rgba(59, 130, 246, 0.15); -fx-text-fill: #3b82f6; -fx-border-color: #3b82f6; -fx-border-radius: 3; -fx-padding: 3 8; }
.role-badge-analytics   { -fx-background-color: rgba(168, 85, 247, 0.15); -fx-text-fill: #a855f7; -fx-border-color: #a855f7; -fx-border-radius: 3; -fx-padding: 3 8; }

/* Urgency Status Colors */
.status-critical   { -fx-text-fill: #ef4444; -fx-font-weight: bold; }
.status-high       { -fx-text-fill: #f97316; -fx-font-weight: bold; }
.status-medium     { -fx-text-fill: #eab308; -fx-font-weight: bold; }
.status-low        { -fx-text-fill: #22c55e; -fx-font-weight: bold; }

/* Low-Stock Alert Highlight */
.row-low-stock     { -fx-background-color: rgba(239, 68, 68, 0.1); }
.alert-pulse-badge { -fx-background-color: #ef4444; -fx-text-fill: white; -fx-background-radius: 10; -fx-padding: 2 6; }
```

---

## 7. Revised Developer Work Split

### Developer A — Foundation, Infrastructure & Core Asset Modules
* **Shared Infrastructure:** `Main.java`, `Utility.java` (with RBAC security guards), `UserSession.java`, `application.css`, `login.fxml` + `LoginController.java`, `dashboard.fxml` + `DashboardController.java` (dynamic role layout engine).
* **Target Screens (8 Screens):**
  1. `disaster.fxml` (`DisasterController.java`) — Admin
  2. `zone.fxml` (`ZoneController.java`) — Admin
  3. `shelter.fxml` (`ShelterController.java`) — Admin, Zone Coord, Analytics
  4. `resource.fxml` (`ResourceController.java`) — Admin, Warehouse Mgr
  5. `inventory.fxml` (`InventoryController.java`) — Warehouse Mgr, Admin
  6. `donation.fxml` (`DonationController.java`) — Warehouse Mgr, Admin
  7. `analytics.fxml` (`AnalyticsController.java`) — Analytics User, Admin, Zone Coord

### Developer B — People, Operations & Logging Modules
* **Target Screens (7 Screens):**
  1. `volunteer.fxml` (`VolunteerController.java`) — Volunteer Coord, Admin
  2. `volunteer_shift.fxml` (`VolunteerShiftController.java`) — Volunteer Coord, Admin
  3. `affected_person.fxml` (`AffectedPersonController.java`) — Zone Coord, Admin
  4. `resource_request.fxml` (`ResourceRequestController.java`) — Zone Coord, Warehouse Mgr, Admin
  5. `distribution_log.fxml` (`DistributionLogController.java`) — Warehouse Mgr, Admin, Analytics
  6. `audit_log.fxml` (`AuditLogController.java`) — Admin

---

## 8. Implementation Order & Checklist

### Phase 1 — Authentication & Dynamic Dashboard Engine (Days 1-2)
- [ ] Implement `UserSession.java` singleton & `UserRole` enum.
- [ ] Enhance `LoginController.java` with role selection & authentication token parsing.
- [ ] Build dynamic role layout engine in `DashboardController.java`.
- [ ] Add RBAC authorization guards to `Utility.changeScene()`.
- [ ] Port `application.css` and append role badge classes.

### Phase 2 — Role-Specific Screens (Days 3-6)
- [ ] **Zone Coordinator View:** Build `resource_request.fxml` and `affected_person.fxml` with zone context filtering.
- [ ] **Warehouse Manager View:** Build `inventory.fxml`, fulfillment action in `resource_request.fxml`, and `donation.fxml`.
- [ ] **Volunteer Coordinator View:** Build `volunteer.fxml` (skills matching) and `volunteer_shift.fxml` (conflict-free calendar).
- [ ] **Analytics View:** Build `analytics.fxml` charts (deficit, occupancy, 7-day consumption).
- [ ] **System Admin View:** Build `disaster.fxml`, `zone.fxml`, `resource.fxml`, and `audit_log.fxml`.

### Phase 3 — Verification & Role Testing (Days 7-8)
- [ ] Login test as `SYSTEM_ADMIN`: Verify all 15 screens accessible.
- [ ] Login test as `ZONE_COORDINATOR`: Verify dashboard shows only Zone requests, Affected Persons, Shelter status, and Zone deficit. Attempt unauthorized navigation guard test.
- [ ] Login test as `WAREHOUSE_MANAGER`: Verify Low-Stock alert highlights, donation intake, and fulfillment action calling `fn_allocate_resources()`.
- [ ] Login test as `VOLUNTEER_COORDINATOR`: Verify volunteer skill array searching and shift exclusion validation.
- [ ] Login test as `ANALYTICS_USER`: Verify read-only charts, deficit reports, and materialized view dashboard rendering.

---

## 9. Shelter Registration Form — Location & Geocoding Design

> **Relevant screen:** `shelter.fxml` (managed by Developer A — `ShelterController.java`)

### 9.1 The Problem
Shelter location is stored in the database as a PostgreSQL custom composite type `coordinates(latitude, longitude)`. Displaying raw lat/lng values (`11.5123, 76.0456`) in UI tables, dropdowns, and reports is unreadable to field coordinators.

### 9.2 Solution — Two Decoupled Fields

The `shelters` table stores location in two complementary columns:

| Column | Type | Purpose | Who sees it |
|---|---|---|---|
| `address` | `VARCHAR(255)` | Human-readable location text | **UI tables, reports, dropdowns, printed forms** |
| `location` | `coordinates` composite | Lat/lng for backend spatial queries | Internal only — never displayed raw |

### 9.3 Shelter Registration Form Layout (`shelter.fxml`)

```
┌─────────────────────────────────────────────────┐
│  Register / Edit Shelter                         │
├─────────────────────────────────────────────────┤
│  Shelter Name      [ ________________________ ]  │
│                                                  │
│  Address           [ ________________________ ]  │
│                    [ ________________________ ]  │
│                          ↑ free text, displayed  │
│                            in all UI tables      │
│                                                  │
│  Coordinates (optional)                          │
│  Latitude   [ _________ ]  Longitude [ _______ ] │
│                                                  │
│  [ 📍 Auto-fill from Address ]  ← Nominatim btn  │
│                                                  │
│  Capacity   [ _____ ]   Status [ Operational ▼ ] │
│                                                  │
│            [ Cancel ]  [ Save Shelter ]          │
└─────────────────────────────────────────────────┘
```

### 9.4 "Auto-fill coordinates" Button Behaviour (`ShelterController.java`)

```java
@FXML
private void handleAutoFillCoordinates() {
    String address = addressField.getText().trim();
    if (address.isEmpty()) {
        showWarning("Enter an address first.");
        return;
    }
    try {
        // Call Nominatim — free, no API key required
        String url = "https://nominatim.openstreetmap.org/search?q="
                   + URLEncoder.encode(address, StandardCharsets.UTF_8)
                   + "&format=json&limit=1";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "DRRAS/1.0")   // Nominatim requires User-Agent
                .GET().build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        JSONArray results = new JSONArray(response.body());
        if (!results.isEmpty()) {
            JSONObject first = results.getJSONObject(0);
            latitudeField.setText(first.getString("lat"));
            longitudeField.setText(first.getString("lon"));
        } else {
            showWarning("Address not found. Enter coordinates manually.");
        }
    } catch (Exception e) {
        // Graceful degradation — API unreachable (no network in disaster zone)
        showWarning("Geocoding unavailable offline. Enter coordinates manually if needed.");
    }
}
```

### 9.5 Offline Degradation Rule
- `address` is **always required** (NOT NULL in practice) — coordinators always know the physical address.
- `location` (coordinates) is **optional** — if offline during registration, leave blank. The system still works; coordinates are only needed if spatial proximity queries are run later.
- The button is disabled with a tooltip *"Requires internet connection"* if the app detects no network at startup.
