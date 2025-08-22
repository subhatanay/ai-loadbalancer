package com.bits.payment.exception;

public class PaymentNotFoundException extends PaymentServiceException {
    public PaymentNotFoundException(String message) { 
        super(message); 
    }
}
