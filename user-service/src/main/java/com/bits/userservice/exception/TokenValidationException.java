package com.bits.userservice.exception;

public class TokenValidationException extends UserServiceException {
    public TokenValidationException(String message) {
        super(message);
    }
}
