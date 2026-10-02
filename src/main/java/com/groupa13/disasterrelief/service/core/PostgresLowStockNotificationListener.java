package com.groupa13.disasterrelief.service.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.db.DatabaseConfig;
import com.groupa13.disasterrelief.model.core.LowStockAlert;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Dedicated unpooled LISTEN connection with bounded reconnect backoff. The
 * callback runs on the listener thread; JavaFX callers must schedule UI work.
 */
public final class PostgresLowStockNotificationListener
        implements LowStockNotificationListener {

    private static final Logger LOGGER = Logger.getLogger(
            PostgresLowStockNotificationListener.class.getName());
    private static final long INITIAL_BACKOFF_MILLIS = 250;
    private static final long MAX_BACKOFF_MILLIS = 5_000;

    private final ConnectionFactory connections;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Connection> activeConnection = new AtomicReference<>();

    public PostgresLowStockNotificationListener(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(config.jdbcUrl());
        dataSource.setUser(config.username());
        dataSource.setPassword(config.password());
        this.connections = dataSource::getConnection;
        this.executor = newListenerExecutor();
    }

    PostgresLowStockNotificationListener(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.executor = newListenerExecutor();
    }

    @Override
    public void start(Consumer<LowStockAlert> onAlert) {
        Objects.requireNonNull(onAlert, "onAlert");
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Low-stock listener has already been started");
        }
        executor.submit(() -> listen(onAlert));
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            executor.shutdownNow();
            return;
        }
        closeQuietly(activeConnection.getAndSet(null));
        executor.shutdownNow();
        try {
            executor.awaitTermination(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void listen(Consumer<LowStockAlert> onAlert) {
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        while (running.get()) {
            try (Connection connection = connections.open()) {
                activeConnection.set(connection);
                connection.setAutoCommit(true);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN low_stock_alert");
                }
                PGConnection postgres = connection.unwrap(PGConnection.class);
                backoffMillis = INITIAL_BACKOFF_MILLIS;
                while (running.get()) {
                    PGNotification[] notifications = postgres.getNotifications(1_000);
                    if (notifications == null) {
                        continue;
                    }
                    for (PGNotification notification : notifications) {
                        deliver(notification.getParameter(), onAlert);
                    }
                }
            } catch (SQLException exception) {
                if (running.get()) {
                    LOGGER.log(Level.WARNING, "Low-stock listener disconnected; retrying",
                            exception);
                }
            } finally {
                activeConnection.set(null);
            }
            if (running.get() && !pause(backoffMillis)) {
                return;
            }
            backoffMillis = Math.min(MAX_BACKOFF_MILLIS, backoffMillis * 2);
        }
    }

    private static void deliver(String payload, Consumer<LowStockAlert> onAlert) {
        try {
            onAlert.accept(LowStockAlert.fromJson(payload));
        } catch (IllegalArgumentException exception) {
            LOGGER.log(Level.WARNING, "Ignored malformed low-stock notification payload");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Low-stock notification callback failed", exception);
        }
    }

    private boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static ExecutorService newListenerExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "low-stock-listener");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            LOGGER.log(Level.FINE, "Could not close low-stock listener connection", exception);
        }
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open() throws SQLException;
    }
}
