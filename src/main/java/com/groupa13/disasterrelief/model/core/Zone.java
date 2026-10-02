package com.groupa13.disasterrelief.model.core;

public record Zone(
        int zoneId,
        int disasterId,
        String disasterName,
        String name,
        Severity severity,
        Integer primaryShelterId,
        String primaryShelterName,
        String coordinatorContact) {
}
