package com.groupa13.disasterrelief.service.operations;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.operations.DonationDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.operations.DonationItem;
import com.groupa13.disasterrelief.model.operations.DonationRecord;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.UserSession;

public class DonationService {

    private static final Logger LOGGER = Logger.getLogger(DonationService.class.getName());

    private final DonationDao dao;

    public DonationService() {
        this(new DonationDao());
    }

    public DonationService(DonationDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    public ServiceResult<Integer> processDonation(String donorName, int receivingShelterId,
            List<DonationItem> items) {
        if (receivingShelterId <= 0) {
            return ServiceResult.failure("Select a valid receiving shelter");
        }
        if (items == null || items.isEmpty()) {
            return ServiceResult.failure("Add at least one item to the donation bundle");
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            DonationItem item = items.get(i);
            json.append(String.format("{\"resource_id\": %d, \"quantity\": %d}", item.resourceId(), item.quantity()));
            if (i < items.size() - 1) {
                json.append(", ");
            }
        }
        json.append("]");

        UserSession session = UserSession.getInstance();
        try {
            int donationId = Database.inTransaction(session,
                    connection -> dao.callProcessDonation(connection, donorName, receivingShelterId, json.toString()));
            return ServiceResult.success(donationId, "Donation #" + donationId + " processed and inventory updated");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to process donation", exception);
            return ServiceResult.failure("Donation processing failed: " + exception.getMessage());
        }
    }

    public ServiceResult<List<DonationRecord>> listDonations() {
        UserSession session = UserSession.getInstance();
        try {
            List<DonationRecord> list = Database.inTransaction(session, dao::findAll);
            return ServiceResult.success(list, "Donations loaded");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.WARNING, "Failed to list donations", exception);
            return ServiceResult.failure("Unable to load donation records");
        }
    }
}
