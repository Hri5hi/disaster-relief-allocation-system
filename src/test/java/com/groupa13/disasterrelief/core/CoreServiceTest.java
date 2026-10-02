package com.groupa13.disasterrelief.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.db.SqlWork;
import com.groupa13.disasterrelief.model.core.ActivePeriod;
import com.groupa13.disasterrelief.model.core.DisasterStatus;
import com.groupa13.disasterrelief.model.core.Severity;
import com.groupa13.disasterrelief.model.core.ShelterStatus;
import com.groupa13.disasterrelief.service.core.DisasterService;
import com.groupa13.disasterrelief.service.core.InventoryService;
import com.groupa13.disasterrelief.service.core.ResourceCategoryService;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.core.ShelterService;
import com.groupa13.disasterrelief.service.core.TransactionRunner;
import com.groupa13.disasterrelief.service.core.ZoneService;

class CoreServiceTest {

    @Test
    void invalidInputIsRejectedBeforeAnyDaoOrTransactionCall() {
        AtomicBoolean called = new AtomicBoolean();
        TransactionRunner mustNotRun = new TransactionRunner() {
            @Override
            public <T> T run(SqlWork<T> work) {
                called.set(true);
                throw new AssertionError("validation reached the transaction boundary");
            }
        };

        assertFalse(new DisasterService(mustNotRun).createDisaster(
                " ", "Flood", Severity.HIGH, null, null, DisasterStatus.ACTIVE).success());
        assertFalse(new ZoneService(mustNotRun).createZone(
                0, "North", Severity.HIGH, null, null).success());
        assertFalse(new ShelterService(mustNotRun).createShelter(
                "Camp", "Address", 91.0, 76.0, 10, ShelterStatus.OPERATIONAL).success());
        assertFalse(new ResourceCategoryService(mustNotRun).updateCategory(
                1, "Medical", 0).success());
        assertFalse(new InventoryService(mustNotRun).replaceInventory(
                1, 1, -1, 10).success());
        assertFalse(called.get());
    }

    @Test
    void dateRangeAndCoordinateValidationMessagesAreUserSafe() {
        TransactionRunner mustNotRun = new TransactionRunner() {
            @Override
            public <T> T run(SqlWork<T> work) {
                throw new AssertionError("invalid input reached the database");
            }
        };
        ServiceResult<?> badRange = new DisasterService(mustNotRun).createDisaster(
                "Flood", "Flood", Severity.HIGH,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 2),
                DisasterStatus.ACTIVE);
        assertEquals("Active-period end must be after its start", badRange.message());

        ServiceResult<?> partialCoordinates = new ShelterService(mustNotRun).createShelter(
                "Camp", "Address", 11.0, null, 10, ShelterStatus.OPERATIONAL);
        assertEquals("Both latitude and longitude are required", partialCoordinates.message());

        assertThrows(IllegalArgumentException.class, () -> new ActivePeriod(
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 2)));
    }

    @Test
    void databaseFailuresAreTranslatedWithoutLeakingSqlState() {
        TransactionRunner unavailable = new TransactionRunner() {
            @Override
            public <T> T run(SqlWork<T> work) {
                throw DatabaseException.fromSql("internal",
                        new SQLException("connection details", "08006"));
            }
        };

        ServiceResult<?> result = new InventoryService(unavailable).listInventory();

        assertFalse(result.success());
        assertEquals("Could not load inventory", result.message());
        assertFalse(result.message().contains("08006"));
        assertFalse(result.message().contains("connection details"));
    }
}
