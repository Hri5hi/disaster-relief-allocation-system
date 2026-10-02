package com.groupa13.disasterrelief.dao.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.groupa13.disasterrelief.model.operations.DonationRecord;
import org.postgresql.util.PGobject;

public final class DonationDao {

    private static final String SELECT_BASE = """
            SELECT dr.donation_id, dr.donor_name, dr.items::text AS items_json,
                   dr.received_at, dr.receiving_shelter, s.name AS receiving_shelter_name
            FROM donation_records dr
            LEFT JOIN shelters s ON dr.receiving_shelter = s.shelter_id
            ORDER BY dr.received_at DESC, dr.donation_id DESC
            """;

    public int callProcessDonation(Connection connection, String donorName, int receivingShelterId,
            String itemsJson) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT fn_process_donation(?, ?, ?::jsonb) AS donation_id
                """)) {
            statement.setString(1, donorName);
            statement.setInt(2, receivingShelterId);
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(itemsJson);
            statement.setObject(3, jsonObject);

            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt("donation_id");
            }
        }
    }

    public List<DonationRecord> findAll(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BASE);
                ResultSet rows = statement.executeQuery()) {
            List<DonationRecord> list = new ArrayList<>();
            while (rows.next()) {
                list.add(map(rows));
            }
            return List.copyOf(list);
        }
    }

    private static DonationRecord map(ResultSet rows) throws SQLException {
        int donationId = rows.getInt("donation_id");
        String donorName = rows.getString("donor_name");
        String itemsJson = rows.getString("items_json");
        Timestamp receivedAt = rows.getTimestamp("received_at");
        int shelterIdVal = rows.getInt("receiving_shelter");
        Integer receivingShelterId = rows.wasNull() ? null : shelterIdVal;
        String shelterName = rows.getString("receiving_shelter_name");

        return new DonationRecord(
                donationId, donorName, itemsJson,
                receivedAt != null ? receivedAt.toString() : "",
                receivingShelterId, shelterName
        );
    }
}
