package com.groupa13.disasterrelief.service.core;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.db.DatabaseException;

final class CoreServiceSupport {

    private CoreServiceSupport() {
    }

    static String requiredText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(
                    field + " must be at most " + maximumLength + " characters");
        }
        return trimmed;
    }

    static String optionalText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, field, maximumLength);
    }

    static void positiveId(int id, String field) {
        if (id <= 0) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    static <T> ServiceResult<T> validationFailure(IllegalArgumentException exception) {
        return ServiceResult.failure(exception.getMessage());
    }

    static <T> ServiceResult<T> databaseFailure(Logger logger, DatabaseException exception,
            String message) {
        logger.log(Level.WARNING,
                () -> message + " [sqlState=" + exception.sqlState()
                        + ", constraint=" + exception.constraintName() + "]");
        return ServiceResult.failure(message);
    }
}
