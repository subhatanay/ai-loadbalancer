package com.bits.order.exception;

public class OrderNotFoundException extends OrderServiceException {
    
    public OrderNotFoundException(String message) {
        super(message);
    }
}
