package com.groupa13.disasterrelief.model.operations;

/**
 * Metric snapshot for role-based dashboard cards.
 */
public record DashboardMetrics(
        int activeDisasters,
        int pendingRequests,
        int totalVolunteers,
        int operationalShelters,
        int lowStockAlerts,
        int totalDistributions
) {
}
