package com.groupa13.disasterrelief.dao.core;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;

import com.groupa13.disasterrelief.model.core.ActivePeriod;
import org.postgresql.util.PGobject;

final class JdbcSupport {

    private JdbcSupport() {
    }

    static void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    static void setNullableText(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    static void setActivePeriod(PreparedStatement statement, int index, ActivePeriod period)
            throws SQLException {
        if (period == null) {
            statement.setNull(index, Types.OTHER);
            return;
        }
        PGobject value = new PGobject();
        value.setType("daterange");
        value.setValue("[" + boundary(period.startInclusive()) + ","
                + boundary(period.endExclusive()) + ")");
        statement.setObject(index, value);
    }

    static ActivePeriod activePeriod(ResultSet rows) throws SQLException {
        LocalDate start = rows.getObject("active_start", LocalDate.class);
        LocalDate end = rows.getObject("active_end", LocalDate.class);
        return start == null && end == null ? null : new ActivePeriod(start, end);
    }

    private static String boundary(LocalDate date) {
        return date == null ? "" : date.toString();
    }
}
