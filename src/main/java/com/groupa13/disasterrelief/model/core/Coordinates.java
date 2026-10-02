package com.groupa13.disasterrelief.model.core;

public record Coordinates(double latitude, double longitude) {

    public Coordinates {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    public String displayValue() {
        return latitude + ", " + longitude;
    }
}
