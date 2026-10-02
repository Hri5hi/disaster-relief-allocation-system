package com.groupa13.disasterrelief.model.core;

public record Resource(
        int resourceId,
        String name,
        int categoryId,
        String categoryPath,
        String unit) {
}
