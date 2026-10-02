package com.groupa13.disasterrelief.model.core;

public record Shelter(
        int shelterId,
        String name,
        String address,
        Coordinates location,
        int capacity,
        int currentOccupancy,
        ShelterStatus status) {

    public double occupancyRatio() {
        return capacity == 0 ? 0 : Math.min(1.0, (double) currentOccupancy / capacity);
    }
}
