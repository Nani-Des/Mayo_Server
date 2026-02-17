package com.mayo.notification.entity;

/**
 * Status of notification delivery
 */
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    SEND_FAILED,
    DELIVERY_FAILED,
    CANCELLED
}