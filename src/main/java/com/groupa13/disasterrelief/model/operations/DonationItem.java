package com.groupa13.disasterrelief.model.operations;

/**
 * Line item in an incoming donation bundle.
 */
public record DonationItem(int resourceId, String resourceName, int quantity) {
    public DonationItem {
        if (resourceId <= 0) {
            throw new IllegalArgumentException("Resource ID must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public DonationItem(int resourceId, int quantity) {
        this(resourceId, null, quantity);
    }
}
