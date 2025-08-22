package com.bits.payment.exception;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex, WebRequest req) {
        log.warn("Payment not found: {}", ex.getMessage());
        ErrorResponse res = ErrorResponse.builder()
            .timestamp(LocalDateTime.now()).status(HttpStatus.NOT_FOUND.value())
            .error("Not Found").message(ex.getMessage())
            .path(req.getDescription(false).replace("uri=", "")).build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
    }

    @ExceptionHandler(InvalidRefundException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefund(InvalidRefundException ex, WebRequest req) {
        log.warn("Invalid refund: {}", ex.getMessage());
        ErrorResponse res = ErrorResponse.builder()
            .timestamp(LocalDateTime.now()).status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Refund").message(ex.getMessage())
            .path(req.getDescription(false).replace("uri=", "")).build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(PaymentServiceException.class)
    public ResponseEntity<ErrorResponse> handleGeneral(PaymentServiceException ex, WebRequest req) {
        log.error("Service error: {}", ex.getMessage());
        ErrorResponse res = ErrorResponse.builder()
            .timestamp(LocalDateTime.now()).status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Error").message(ex.getMessage())
            .path(req.getDescription(false).replace("uri=", "")).build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((error) -> 
            errors.put(error.getField(), error.getDefaultMessage()));
        ErrorResponse res = ErrorResponse.builder()
            .timestamp(LocalDateTime.now()).status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Input validation failed: " + errors)
            .path(req.getDescription(false).replace("uri=", "")).build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex, WebRequest req) {
        log.error("Unexpected error: ", ex);
        ErrorResponse res = ErrorResponse.builder()
            .timestamp(LocalDateTime.now()).status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error").message("An unexpected error occurred")
            .path(req.getDescription(false).replace("uri=", "")).build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
    }
}
