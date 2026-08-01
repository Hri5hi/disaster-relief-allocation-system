package com.groupa13.disasterrelief.model;

/**
 * Model for the 'resource_requests' table.
 * Schema: project_description_v2.md Table 10
 *
 * priority_score is a PostgreSQL generated column (auto-computed from urgency weight + quantity).
 * status lifecycle: pending → fulfilled | cancelled
 */
public class ResourceRequest {

    private int requestId;
    private int zoneId;
    private int resourceId;
    private int quantityRequested;
    private String urgency;       // CHECK: 'critical', 'high', 'medium', 'low'
    private int priorityScore;    // Generated column — computed by DB
    private String status;        // CHECK: 'pending', 'fulfilled', 'cancelled'
    private String requestedAt;   // TIMESTAMPTZ as display string
    private String fulfilledAt;   // TIMESTAMPTZ, set by fn_allocate_resources()
    private String zoneName;      // Display-friendly (joined from zones)
    private String resourceName;  // Display-friendly (joined from resources)

    public ResourceRequest() {
    }

    public ResourceRequest(int requestId, int zoneId, int resourceId, int quantityRequested,
                           String urgency, int priorityScore, String status,
                           String requestedAt, String fulfilledAt) {
        this.requestId = requestId;
        this.zoneId = zoneId;
        this.resourceId = resourceId;
        this.quantityRequested = quantityRequested;
        this.urgency = urgency;
        this.priorityScore = priorityScore;
        this.status = status;
        this.requestedAt = requestedAt;
        this.fulfilledAt = fulfilledAt;
    }

    // --- Getters ---

    public int getRequestId() {
        return requestId;
    }

    public int getZoneId() {
        return zoneId;
    }

    public int getResourceId() {
        return resourceId;
    }

    public int getQuantityRequested() {
        return quantityRequested;
    }

    public String getUrgency() {
        return urgency;
    }

    public int getPriorityScore() {
        return priorityScore;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestedAt() {
        return requestedAt;
    }

    public String getFulfilledAt() {
        return fulfilledAt;
    }

    public String getZoneName() {
        return zoneName;
    }

    public String getResourceName() {
        return resourceName;
    }

    // --- Setters ---

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    public void setResourceId(int resourceId) {
        this.resourceId = resourceId;
    }

    public void setQuantityRequested(int quantityRequested) {
        this.quantityRequested = quantityRequested;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public void setPriorityScore(int priorityScore) {
        this.priorityScore = priorityScore;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRequestedAt(String requestedAt) {
        this.requestedAt = requestedAt;
    }

    public void setFulfilledAt(String fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    @Override
    public String toString() {
        return "Request #" + requestId + " [" + urgency + "]";
    }
}
