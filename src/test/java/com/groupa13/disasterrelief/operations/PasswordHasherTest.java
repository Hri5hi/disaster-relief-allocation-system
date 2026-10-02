package com.groupa13.disasterrelief.operations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.groupa13.disasterrelief.auth.PasswordHasher;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void hashesAndVerifiesPassword() {
        String password = "Password123!";
        String hash = PasswordHasher.hash(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$10$"));
        assertTrue(PasswordHasher.verify(password, hash));
        assertFalse(PasswordHasher.verify("WrongPassword", hash));
        assertFalse(PasswordHasher.verify("", hash));
        assertFalse(PasswordHasher.verify(null, hash));

        String staticHash = "$2a$10$HaUWHrSnxmG74qamG.rU/eJgyb57/emdR4f45/JjpeTtdvLD/wfr.";
        assertTrue(PasswordHasher.verify(password, staticHash));
    }
}
