package com.groupa13.disasterrelief.model.core;

import java.time.OffsetDateTime;

public record ShelterInventory(
        int shelterId,
        String shelterName,
        int resourceId,
        String resourceName,
        int quantity,
        int reorderLevel,
        OffsetDateTime lastUpdated) {

    public boolean lowStock() {
        return quantity < reorderLevel;
    }
}
