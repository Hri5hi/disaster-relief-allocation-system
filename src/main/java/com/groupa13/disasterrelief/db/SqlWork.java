package com.groupa13.disasterrelief.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Work executed with one transaction-scoped JDBC connection. */
@FunctionalInterface
public interface SqlWork<T> {
    T execute(Connection connection) throws SQLException;
}
