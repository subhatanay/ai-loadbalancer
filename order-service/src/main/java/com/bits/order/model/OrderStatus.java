package com.bits.order.model;

public enum OrderStatus {
    PENDING, 
    CONFIRMED, 
    PAYMENT_PROCESSING, 
    PAYMENT_COMPLETED, 
    PAYMENT_FAILED, 
    INVENTORY_RESERVED, 
    INVENTORY_FAILED, 
    PROCESSING, 
    SHIPPED, 
    DELIVERED, 
    CANCELLED, 
    REFUNDED
}
