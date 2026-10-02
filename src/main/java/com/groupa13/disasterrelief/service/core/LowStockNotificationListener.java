package com.groupa13.disasterrelief.service.core;

import java.util.function.Consumer;

import com.groupa13.disasterrelief.model.core.LowStockAlert;

/** Mockable boundary consumed by InventoryController and implemented without Hikari. */
public interface LowStockNotificationListener extends AutoCloseable {

    void start(Consumer<LowStockAlert> onAlert);

    @Override
    void close();
}
