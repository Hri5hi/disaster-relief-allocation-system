package com.groupa13.disasterrelief.model.core;

import java.time.OffsetDateTime;

public record Disaster(
        int disasterId,
        String name,
        String type,
        Severity severity,
        ActivePeriod activePeriod,
        DisasterStatus status,
        OffsetDateTime createdAt) {
}
