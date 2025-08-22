package com.bits.cartservice.exception;

public class TokenValidationException extends CartServiceException {
    public TokenValidationException(String message) {
        super(message);
    }
    
    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
