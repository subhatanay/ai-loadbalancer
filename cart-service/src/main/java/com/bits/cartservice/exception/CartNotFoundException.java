package com.bits.cartservice.exception;

public class CartNotFoundException extends CartServiceException {
    public CartNotFoundException(String message) {
        super(message);
    }
}
