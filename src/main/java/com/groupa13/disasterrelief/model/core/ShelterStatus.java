package com.groupa13.disasterrelief.model.core;

import java.util.Locale;

public enum ShelterStatus {
    OPERATIONAL,
    FULL,
    CLOSED;

    public String databaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ShelterStatus fromDatabase(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return databaseValue();
    }
}
