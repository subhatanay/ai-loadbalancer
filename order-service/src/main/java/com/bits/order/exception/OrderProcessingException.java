package com.bits.order.exception;

public class OrderProcessingException extends OrderServiceException {
    
    public OrderProcessingException(String message) {
        super(message);
    }
    
    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
