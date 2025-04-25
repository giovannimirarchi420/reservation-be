package it.polito.cloudresources.be.model;

/**
 * Enumeration of supported webhook event types
 */
public enum WebhookEventType {
    EVENT_CREATED,
    EVENT_UPDATED,
    EVENT_DELETED,
    EVENT_START,
    EVENT_END,
    RESOURCE_CREATED,
    RESOURCE_UPDATED,
    RESOURCE_STATUS_CHANGED,
    RESOURCE_DELETED,
    ALL
}