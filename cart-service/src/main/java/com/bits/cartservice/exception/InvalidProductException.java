package com.bits.cartservice.exception;

public class InvalidProductException extends CartServiceException {
    public InvalidProductException(String message) {
        super(message);
    }
}
