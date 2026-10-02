package com.groupa13.disasterrelief.model.core;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record LowStockAlert(int shelterId, int resourceId, int quantity, int reorderLevel) {

    private static final int MAX_PAYLOAD_LENGTH = 4_096;
    private static final Pattern INTEGER_PROPERTY = Pattern.compile(
            "\\\"([A-Za-z_][A-Za-z0-9_]*)\\\"\\s*:\\s*(-?\\d+)");

    public LowStockAlert {
        if (shelterId <= 0 || resourceId <= 0 || quantity < 0 || reorderLevel < 0) {
            throw new IllegalArgumentException("Low-stock alert contains invalid values");
        }
    }

    /** Parses the bounded four-integer payload emitted by the future V006 trigger. */
    public static LowStockAlert fromJson(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Low-stock alert payload is missing or too large");
        }
        Map<String, Integer> values = new HashMap<>();
        Matcher matcher = INTEGER_PROPERTY.matcher(payload);
        while (matcher.find()) {
            String key = normalizeKey(matcher.group(1));
            if (key != null && values.putIfAbsent(key, Integer.parseInt(matcher.group(2))) != null) {
                throw new IllegalArgumentException("Low-stock alert contains a duplicate field");
            }
        }
        if (values.size() != 4 || !payload.strip().startsWith("{")
                || !payload.strip().endsWith("}")) {
            throw new IllegalArgumentException("Low-stock alert payload is malformed");
        }
        return new LowStockAlert(
                require(values, "shelterId"),
                require(values, "resourceId"),
                require(values, "quantity"),
                require(values, "reorderLevel"));
    }

    private static String normalizeKey(String key) {
        return switch (key) {
            case "shelter_id", "shelterId" -> "shelterId";
            case "resource_id", "resourceId" -> "resourceId";
            case "quantity" -> "quantity";
            case "reorder_level", "reorderLevel" -> "reorderLevel";
            default -> null;
        };
    }

    private static int require(Map<String, Integer> values, String key) {
        Integer value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Low-stock alert is missing " + key);
        }
        return value;
    }
}
