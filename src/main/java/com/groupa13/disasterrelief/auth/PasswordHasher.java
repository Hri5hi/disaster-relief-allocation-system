package com.groupa13.disasterrelief.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt password hashing and verification utility.
 * Uses standard BCrypt logarithmic cost factor 10.
 */
public final class PasswordHasher {

    private static final int LOG_ROUNDS = 10;

    private PasswordHasher() {
    }

    /** Hashes a plaintext password using BCrypt with salt. */
    public static String hash(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(LOG_ROUNDS));
    }

    /** Verifies a plaintext password against a stored BCrypt hash. */
    public static boolean verify(String plaintext, String hashed) {
        if (plaintext == null || hashed == null || hashed.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plaintext, hashed);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
