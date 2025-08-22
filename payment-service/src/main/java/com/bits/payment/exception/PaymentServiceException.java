package com.bits.payment.exception;

public class PaymentServiceException extends RuntimeException {
    public PaymentServiceException(String message) { 
        super(message); 
    }
    public PaymentServiceException(String message, Throwable throwable) { 
        super(message, throwable); 
    }
}
