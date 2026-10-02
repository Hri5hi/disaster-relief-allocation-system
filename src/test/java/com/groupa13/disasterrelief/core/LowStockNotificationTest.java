package com.groupa13.disasterrelief.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.groupa13.disasterrelief.model.core.LowStockAlert;
import com.groupa13.disasterrelief.service.core.LowStockNotificationListener;

class LowStockNotificationTest {

    @Test
    void parsesSnakeCaseAndCamelCasePayloadsWithinTheBoundedContract() {
        assertEquals(new LowStockAlert(2, 7, 4, 10), LowStockAlert.fromJson("""
                {"shelter_id":2,"resource_id":7,"quantity":4,"reorder_level":10}
                """));
        assertEquals(new LowStockAlert(3, 8, 0, 5), LowStockAlert.fromJson("""
                { "shelterId": 3, "resourceId": 8, "quantity": 0, "reorderLevel": 5 }
                """));
    }

    @Test
    void rejectsMalformedOversizedDuplicateAndNegativePayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> LowStockAlert.fromJson("{}"));
        assertThrows(IllegalArgumentException.class,
                () -> LowStockAlert.fromJson("""
                        {"shelter_id":1,"shelterId":2,"resource_id":3,
                         "quantity":1,"reorder_level":2}
                        """));
        assertThrows(IllegalArgumentException.class,
                () -> LowStockAlert.fromJson("""
                        {"shelter_id":1,"resource_id":3,"quantity":-1,"reorder_level":2}
                        """));
        assertThrows(IllegalArgumentException.class,
                () -> LowStockAlert.fromJson("{" + " ".repeat(4_100) + "}"));
    }

    @Test
    void listenerBoundaryCanBeFakedAndClosedWithoutDatabaseOrJavaFxTypes() {
        AtomicReference<LowStockAlert> received = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean();
        LowStockNotificationListener fake = new LowStockNotificationListener() {
            @Override
            public void start(Consumer<LowStockAlert> onAlert) {
                onAlert.accept(new LowStockAlert(1, 2, 3, 4));
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        fake.start(received::set);
        fake.close();

        assertEquals(new LowStockAlert(1, 2, 3, 4), received.get());
        assertTrue(closed.get());
    }
}
