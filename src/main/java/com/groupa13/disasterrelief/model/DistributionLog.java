package com.groupa13.disasterrelief.model;

/**
 * Model for the 'distribution_log' table (partitioned by month).
 * Schema: project_description_v2.md Table 11
 *
 * Read-only from the UI — records are created by fn_allocate_resources() during fulfillment.
 * Table is range-partitioned by distributed_at for efficient historical queries.
 */
public class DistributionLog {

    private int logId;
    private int sourceShelter;    // FK → shelters(shelter_id) — where resources came from
    private int destShelter;      // FK → shelters(shelter_id) — where resources went
    private int resourceId;       // FK → resources(resource_id)
    private int quantity;
    private String distributedAt; // TIMESTAMPTZ as display string (partition key)
    private String sourceShelterName;  // Display-friendly (joined from shelters)
    private String destShelterName;    // Display-friendly (joined from shelters)
    private String resourceName;       // Display-friendly (joined from resources)

    public DistributionLog() {
    }

    public DistributionLog(int logId, int sourceShelter, int destShelter,
                           int resourceId, int quantity, String distributedAt) {
        this.logId = logId;
        this.sourceShelter = sourceShelter;
        this.destShelter = destShelter;
        this.resourceId = resourceId;
        this.quantity = quantity;
        this.distributedAt = distributedAt;
    }

    // --- Getters ---

    public int getLogId() {
        return logId;
    }

    public int getSourceShelter() {
        return sourceShelter;
    }

    public int getDestShelter() {
        return destShelter;
    }

    public int getResourceId() {
        return resourceId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getDistributedAt() {
        return distributedAt;
    }

    public String getSourceShelterName() {
        return sourceShelterName;
    }

    public String getDestShelterName() {
        return destShelterName;
    }

    public String getResourceName() {
        return resourceName;
    }

    // --- Setters ---

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public void setSourceShelter(int sourceShelter) {
        this.sourceShelter = sourceShelter;
    }

    public void setDestShelter(int destShelter) {
        this.destShelter = destShelter;
    }

    public void setResourceId(int resourceId) {
        this.resourceId = resourceId;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setDistributedAt(String distributedAt) {
        this.distributedAt = distributedAt;
    }

    public void setSourceShelterName(String sourceShelterName) {
        this.sourceShelterName = sourceShelterName;
    }

    public void setDestShelterName(String destShelterName) {
        this.destShelterName = destShelterName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    @Override
    public String toString() {
        return "Transfer #" + logId;
    }
}
