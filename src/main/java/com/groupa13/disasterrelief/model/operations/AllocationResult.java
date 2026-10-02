package com.groupa13.disasterrelief.model.operations;

/**
 * Return model for fn_allocate_resources atomic stored procedure.
 */
public record AllocationResult(
        int logId,
        int requestId,
        int sourceShelterId,
        int destShelterId,
        int resourceId,
        int quantity,
        String fulfilledAt
) {
}
