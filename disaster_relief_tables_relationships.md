# Disaster Relief Allocation System
## Database Tables and Relationships

### 1. Number of Tables

The Disaster Relief Allocation System contains **14 database tables/entities**.

The tables are:

1. `disasters`
2. `zones`
3. `shelters`
4. `resource_categories`
5. `resources`
6. `shelter_inventory`
7. `people`
8. `volunteers`
9. `affected_persons`
10. `resource_requests`
11. `distribution_log`
12. `volunteer_shifts`
13. `donation_records`
14. `audit_log`

---

## 2. Relationships Between Tables

### 2.1 Disasters → Zones
- **Cardinality:** 1 : N
- One disaster can affect multiple zones.
- Each zone belongs to one disaster.
- **Foreign Key:** `zones.disaster_id` references `disasters.disaster_id`.

### 2.2 Zones → Shelters
- **Cardinality:** N : 1 for the primary-shelter reference.
- A zone can identify a primary shelter.
- A shelter may serve as the primary shelter for multiple zones.
- **Foreign Key:** `zones.primary_shelter_id` references `shelters.shelter_id`.

### 2.3 Resource Categories → Resource Categories
- **Cardinality:** 1 : N (recursive relationship).
- A resource category can have multiple child categories.
- A child category can have one parent category.
- **Foreign Key:** `resource_categories.parent_id` references `resource_categories.category_id`.

### 2.4 Resource Categories → Resources
- **Cardinality:** 1 : N
- One resource category can contain multiple resources.
- Each resource belongs to one category.
- **Foreign Key:** `resources.category_id` references `resource_categories.category_id`.

### 2.5 Shelters → Shelter Inventory
- **Cardinality:** 1 : N
- A shelter can maintain inventory records for multiple resources.
- Each inventory record belongs to one shelter.
- **Foreign Key:** `shelter_inventory.shelter_id` references `shelters.shelter_id`.

### 2.6 Resources → Shelter Inventory
- **Cardinality:** 1 : N
- One resource can appear in the inventories of multiple shelters.
- Each inventory record refers to one resource.
- **Foreign Key:** `shelter_inventory.resource_id` references `resources.resource_id`.
- Together with the previous relationship, this resolves the **M : N relationship between Shelters and Resources**.

### 2.7 People → Volunteers
- **Type:** Inheritance / ISA relationship.
- `volunteers` is a specialized entity derived from `people`.
- A volunteer inherits the common person information from `people`.
- **Foreign Key:** `volunteers.person_id` references `people(person_id)`. The subclass PK and FK are the same column — a volunteer row shares its `person_id` with the corresponding `people` row.

### 2.8 People → Affected Persons
- **Type:** Inheritance / ISA relationship.
- `affected_persons` is a specialized entity derived from `people`.
- An affected person inherits the common person information from `people`.
- **Foreign Key:** `affected_persons.person_id` references `people(person_id)`. The subclass PK and FK are the same column — an affected person row shares its `person_id` with the corresponding `people` row.

### 2.9 Zones → Volunteers
- **Cardinality:** 1 : N
- A zone can have multiple assigned volunteers.
- A volunteer is assigned to a zone.
- **Foreign Key:** `volunteers.assigned_zone` references `zones.zone_id`.

### 2.10 Shelters → Affected Persons
- **Cardinality:** 1 : N
- A shelter can accommodate multiple affected persons.
- An affected person is associated with a shelter.
- **Foreign Key:** `affected_persons.shelter_id` references `shelters.shelter_id`.

### 2.11 Zones → Resource Requests
- **Cardinality:** 1 : N
- A zone can create multiple resource requests.
- Each resource request belongs to one zone.
- **Foreign Key:** `resource_requests.zone_id` references `zones.zone_id`.

### 2.12 Resources → Resource Requests
- **Cardinality:** 1 : N
- A resource can be requested in multiple resource requests.
- Each request refers to one resource.
- **Foreign Key:** `resource_requests.resource_id` references `resources.resource_id`.

### 2.13 Shelters → Distribution Log (Source)
- **Cardinality:** 1 : N
- A shelter can act as the source for multiple distributions.
- Each distribution has one source shelter.
- **Foreign Key:** `distribution_log.source_shelter` references `shelters.shelter_id`.

### 2.14 Shelters → Distribution Log (Destination)
- **Cardinality:** 1 : N
- A shelter can receive resources through multiple distributions.
- Each distribution has one destination shelter.
- **Foreign Key:** `distribution_log.dest_shelter` references `shelters.shelter_id`.
- This is a second relationship between the same two entities, with the roles **source shelter** and **destination shelter**.

### 2.15 Resources → Distribution Log
- **Cardinality:** 1 : N
- One resource can be involved in multiple distribution transactions.
- Each distribution records one resource.
- **Foreign Key:** `distribution_log.resource_id` references `resources.resource_id`.

### 2.16 Volunteers → Volunteer Shifts
- **Cardinality:** 1 : N
- A volunteer can have multiple shifts.
- Each shift belongs to one volunteer.
- **Foreign Key:** `volunteer_shifts.volunteer_id` references `volunteers.person_id`.

### 2.17 Zones → Volunteer Shifts
- **Cardinality:** 1 : N
- A zone can have multiple volunteer shifts.
- Each shift is associated with one zone.
- **Foreign Key:** `volunteer_shifts.zone_id` references `zones.zone_id`.

### 2.18 Shelters → Donation Records
- **Cardinality:** 1 : N
- A shelter can receive multiple donations.
- Each donation record is associated with one receiving shelter.
- **Foreign Key:** `donation_records.receiving_shelter` references `shelters.shelter_id`.

---

## 3. Important Relationship Structures

### Shelter ↔ Resource

`Shelters` and `Resources` have an **M : N relationship**, which is resolved using `shelter_inventory`.

```text
SHELTERS 1 ───── N SHELTER_INVENTORY N ───── 1 RESOURCES
```

The composite key of `shelter_inventory` identifies the combination of a shelter and a resource.

### Shelter ↔ Distribution Log

`distribution_log` has two separate relationships with `shelters`:

```text
SHELTERS 1 ───── N DISTRIBUTION_LOG
   Source Shelter

SHELTERS 1 ───── N DISTRIBUTION_LOG
   Destination Shelter
```

This represents the movement of a resource from one shelter to another.

### Resource Category Hierarchy

`resource_categories` has a recursive relationship:

```text
RESOURCE_CATEGORIES
        │
        └──── 1 : N ────> RESOURCE_CATEGORIES
```

This allows categories and subcategories to be represented hierarchically.

### People Specialization

```text
                 PEOPLE
                /      \
               /        \
              ▼          ▼
        VOLUNTEERS   AFFECTED_PERSONS
```

The specialized entities inherit common attributes from `people`.

---

## 4. Relationship Summary

| No. | Parent / Main Entity | Related Entity | Cardinality / Type |
|---:|---|---|---|
| 1 | Disasters | Zones | 1 : N |
| 2 | Zones | Shelters | N : 1 |
| 3 | Resource Categories | Resource Categories | 1 : N, Recursive |
| 4 | Resource Categories | Resources | 1 : N |
| 5 | Shelters | Shelter Inventory | 1 : N |
| 6 | Resources | Shelter Inventory | 1 : N |
| 7 | People | Volunteers | ISA / Inheritance |
| 8 | People | Affected Persons | ISA / Inheritance |
| 9 | Zones | Volunteers | 1 : N |
| 10 | Shelters | Affected Persons | 1 : N |
| 11 | Zones | Resource Requests | 1 : N |
| 12 | Resources | Resource Requests | 1 : N |
| 13 | Shelters | Distribution Log (Source) | 1 : N |
| 14 | Shelters | Distribution Log (Destination) | 1 : N |
| 15 | Resources | Distribution Log | 1 : N |
| 16 | Volunteers | Volunteer Shifts | 1 : N |
| 17 | Zones | Volunteer Shifts | 1 : N |
| 18 | Shelters | Donation Records | 1 : N |

**Total:** 14 tables/entities and 18 relationship connections.

> **Note:** `audit_log` is included as a table but is not listed with a direct foreign-key relationship because it records database operations generically rather than representing a direct entity-to-entity relationship in the core ER model.
