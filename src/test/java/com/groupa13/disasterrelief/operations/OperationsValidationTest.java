package com.groupa13.disasterrelief.operations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.groupa13.disasterrelief.dao.operations.ResourceRequestDao;
import com.groupa13.disasterrelief.model.operations.DonationItem;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.service.operations.AllocationService;
import com.groupa13.disasterrelief.service.operations.DonationService;
import com.groupa13.disasterrelief.service.operations.ResourceRequestService;
import com.groupa13.disasterrelief.util.UserRole;
import com.groupa13.disasterrelief.util.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationsValidationTest {

    @BeforeEach
    @AfterEach
    void resetSession() {
        UserSession.getInstance().clear();
    }

    @Test
    void resourceRequestServiceValidatesInputs() {
        ResourceRequestService service = new ResourceRequestService(new ResourceRequestDao());

        ServiceResult<?> badZone = service.submitRequest(-1, 1, 1, 10, "high");
        assertFalse(badZone.success());
        assertTrue(badZone.message().contains("Invalid zone"));

        ServiceResult<?> badRes = service.submitRequest(1, -1, 1, 10, "high");
        assertFalse(badRes.success());
        assertTrue(badRes.message().contains("Invalid resource"));

        ServiceResult<?> badShelter = service.submitRequest(1, 1, -1, 10, "high");
        assertFalse(badShelter.success());
        assertTrue(badShelter.message().contains("Invalid destination shelter"));

        ServiceResult<?> badQty = service.submitRequest(1, 1, 1, -5, "high");
        assertFalse(badQty.success());
        assertTrue(badQty.message().contains("Requested quantity must be positive"));

        ServiceResult<?> badUrgency = service.submitRequest(1, 1, 1, 10, "");
        assertFalse(badUrgency.success());
        assertTrue(badUrgency.message().contains("Urgency level is required"));

        // Zone coordinator restricted to their assigned zone
        UserSession session = UserSession.getInstance();
        session.setRole(UserRole.ZONE_COORDINATOR);
        session.setAssignedZoneId(2);

        ServiceResult<?> wrongZone = service.submitRequest(1, 1, 1, 10, "high");
        assertFalse(wrongZone.success());
        assertTrue(wrongZone.message().contains("assigned zone"));
    }

    @Test
    void allocationServiceValidatesRolesAndIds() {
        AllocationService service = new AllocationService(new ResourceRequestDao());

        ServiceResult<?> badReqId = service.allocateResources(-1, 1);
        assertFalse(badReqId.success());
        assertTrue(badReqId.message().contains("Invalid request ID"));

        ServiceResult<?> badSource = service.allocateResources(1, -1);
        assertFalse(badSource.success());
        assertTrue(badSource.message().contains("valid source shelter"));

        // Unauthorized role
        UserSession.getInstance().setRole(UserRole.ZONE_COORDINATOR);
        ServiceResult<?> unauth = service.allocateResources(1, 1);
        assertFalse(unauth.success());
        assertTrue(unauth.message().contains("Only Warehouse Managers"));
    }

    @Test
    void donationServiceValidatesInputs() {
        DonationService service = new DonationService();

        ServiceResult<?> badShelter = service.processDonation("Donor", -1, List.of(new DonationItem(1, 10)));
        assertFalse(badShelter.success());
        assertTrue(badShelter.message().contains("valid receiving shelter"));

        ServiceResult<?> emptyItems = service.processDonation("Donor", 1, List.of());
        assertFalse(emptyItems.success());
        assertTrue(emptyItems.message().contains("at least one item"));
    }
}
