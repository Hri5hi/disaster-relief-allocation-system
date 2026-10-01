package com.groupa13.disasterrelief.service.core;

/** User-safe result returned by services to JavaFX controllers. */
public record ServiceResult<T>(boolean success, T value, String message) {

    public static <T> ServiceResult<T> success(T value, String message) {
        return new ServiceResult<>(true, value, message);
    }

    public static <T> ServiceResult<T> failure(String message) {
        return new ServiceResult<>(false, null, message);
    }
}
