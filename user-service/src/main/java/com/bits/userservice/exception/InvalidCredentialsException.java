package com.bits.userservice.exception;

public class InvalidCredentialsException extends UserServiceException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
