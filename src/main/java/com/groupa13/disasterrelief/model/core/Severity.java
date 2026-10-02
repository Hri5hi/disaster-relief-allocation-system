package com.groupa13.disasterrelief.model.core;

import java.util.Locale;

public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public String databaseValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Severity fromDatabase(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return databaseValue();
    }
}
