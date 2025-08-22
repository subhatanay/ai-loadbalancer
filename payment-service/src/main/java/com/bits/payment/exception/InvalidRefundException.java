package com.bits.payment.exception;

public class InvalidRefundException extends PaymentServiceException {
    public InvalidRefundException(String message) { 
        super(message); 
    }
}
