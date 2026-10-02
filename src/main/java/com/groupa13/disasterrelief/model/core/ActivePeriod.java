package com.groupa13.disasterrelief.model.core;

import java.time.LocalDate;

/** PostgreSQL daterange boundaries, using an inclusive lower and exclusive upper date. */
public record ActivePeriod(LocalDate startInclusive, LocalDate endExclusive) {

    public ActivePeriod {
        if (startInclusive == null && endExclusive == null) {
            throw new IllegalArgumentException("At least one active-period boundary is required");
        }
        if (startInclusive != null && endExclusive != null
                && !startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("Active-period end must be after its start");
        }
    }

    public String displayValue() {
        return (startInclusive == null ? "…" : startInclusive.toString())
                + " → "
                + (endExclusive == null ? "…" : endExclusive.toString());
    }
}
