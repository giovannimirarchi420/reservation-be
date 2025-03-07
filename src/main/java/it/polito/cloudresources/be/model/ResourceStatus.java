package it.polito.cloudresources.be.model;

/**
 * Enumeration of possible resource statuses
 */
public enum ResourceStatus {
    ACTIVE,     // Resource is operational and available for booking
    MAINTENANCE, // Resource is under maintenance
    UNAVAILABLE  // Resource is not available for booking
}
