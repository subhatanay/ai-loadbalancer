package com.bits.cartservice.exception;

public class CartOperationException extends CartServiceException {
    public CartOperationException(String message) {
        super(message);
    }
    
    public CartOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
