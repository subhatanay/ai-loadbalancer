package com.bits.notification.exception;

public class TemplateRenderingException extends NotificationServiceException {
    public TemplateRenderingException(String message) {
        super(message);
    }
    
    public TemplateRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
