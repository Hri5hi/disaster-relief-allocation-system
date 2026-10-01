package com.groupa13.disasterrelief.db;

import java.sql.SQLException;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Unchecked boundary exception for database startup, migration, and transaction
 * failures. SQL diagnostics remain available for logs without being exposed to
 * JavaFX controllers as user-facing text.
 */
public final class DatabaseException extends RuntimeException {

    private final String sqlState;
    private final String constraintName;

    public DatabaseException(String message) {
        this(message, null, null, null);
    }

    public DatabaseException(String message, Throwable cause) {
        this(message, cause, sqlState(cause), constraintName(cause));
    }

    private DatabaseException(String message, Throwable cause, String sqlState,
            String constraintName) {
        super(message, cause);
        this.sqlState = sqlState;
        this.constraintName = constraintName;
    }

    public String sqlState() {
        return sqlState;
    }

    public String constraintName() {
        return constraintName;
    }

    public boolean isIntegrityViolation() {
        return sqlState != null && sqlState.startsWith("23");
    }

    public static DatabaseException fromSql(String message, SQLException cause) {
        return new DatabaseException(message, cause, cause.getSQLState(), constraintName(cause));
    }

    private static String sqlState(Throwable cause) {
        return cause instanceof SQLException sqlException ? sqlException.getSQLState() : null;
    }

    private static String constraintName(Throwable cause) {
        if (cause instanceof PSQLException postgresException) {
            ServerErrorMessage serverError = postgresException.getServerErrorMessage();
            return serverError == null ? null : serverError.getConstraint();
        }
        return null;
    }
}
