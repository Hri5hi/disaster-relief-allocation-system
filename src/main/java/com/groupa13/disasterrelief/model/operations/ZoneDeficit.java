package com.groupa13.disasterrelief.model.operations;

/**
 * Return model for fn_zone_deficit_report stored function.
 */
public record ZoneDeficit(
        int zoneId,
        String zoneName,
        int resourceId,
        String resourceName,
        long requiredQty,
        long availableQty,
        long deficitQty,
        String urgencyLevel
) {
}
