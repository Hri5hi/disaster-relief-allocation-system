package com.groupa13.disasterrelief.service.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import javax.sql.DataSource;

import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.db.SqlWork;
import com.groupa13.disasterrelief.util.UserSession;

/** Injectable transaction boundary used by core services and integration tests. */
public interface TransactionRunner {

    <T> T run(SqlWork<T> work);

    static TransactionRunner application() {
        return new TransactionRunner() {
            @Override
            public <T> T run(SqlWork<T> work) {
                return Database.inTransaction(UserSession.getInstance(), work);
            }
        };
    }

    static TransactionRunner forDataSource(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new TransactionRunner() {
            @Override
            public <T> T run(SqlWork<T> work) {
                Objects.requireNonNull(work, "work");
                try (Connection connection = dataSource.getConnection()) {
                    boolean originalAutoCommit = connection.getAutoCommit();
                    Throwable failure = null;
                    try {
                        connection.setAutoCommit(false);
                        T result = work.execute(connection);
                        connection.commit();
                        return result;
                    } catch (SQLException | RuntimeException exception) {
                        failure = exception;
                        try {
                            connection.rollback();
                        } catch (SQLException rollbackFailure) {
                            exception.addSuppressed(rollbackFailure);
                        }
                        if (exception instanceof SQLException sqlException) {
                            throw DatabaseException.fromSql("Database transaction failed", sqlException);
                        }
                        throw exception;
                    } finally {
                        try {
                            connection.setAutoCommit(originalAutoCommit);
                        } catch (SQLException restoreFailure) {
                            if (failure != null) {
                                failure.addSuppressed(restoreFailure);
                            } else {
                                throw DatabaseException.fromSql(
                                        "Could not restore database connection", restoreFailure);
                            }
                        }
                    }
                } catch (SQLException exception) {
                    throw DatabaseException.fromSql("Could not open database connection", exception);
                }
            }
        };
    }
}
