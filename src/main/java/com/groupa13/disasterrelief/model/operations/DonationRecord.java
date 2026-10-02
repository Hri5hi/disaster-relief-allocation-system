package com.groupa13.disasterrelief.model.operations;

/**
 * Model for donation_records table.
 */
public record DonationRecord(
        int donationId,
        String donorName,
        String itemsJson,
        String receivedAt,
        Integer receivingShelterId,
        String receivingShelterName
) {
    public DonationRecord {
        if (itemsJson == null || itemsJson.isBlank()) {
            throw new IllegalArgumentException("Items JSON cannot be blank");
        }
    }
}
