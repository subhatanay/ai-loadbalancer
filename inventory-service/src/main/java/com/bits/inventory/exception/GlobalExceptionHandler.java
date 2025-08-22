package com.bits.inventory.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(InventoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInventoryNotFoundException(
            InventoryNotFoundException ex, HttpServletRequest request) {
        
        logger.warn("Inventory not found: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .error("INVENTORY_NOT_FOUND")
            .message(ex.getMessage())
            .status(HttpStatus.NOT_FOUND.value())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(InventoryValidationException.class)
    public ResponseEntity<ErrorResponse> handleInventoryValidationException(
            InventoryValidationException ex, HttpServletRequest request) {
        
        logger.warn("Inventory validation error: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .error("VALIDATION_ERROR")
            .message(ex.getMessage())
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(
            InsufficientStockException ex, HttpServletRequest request) {
        
        logger.warn("Insufficient stock: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .error("INSUFFICIENT_STOCK")
            .message(ex.getMessage())
            .status(HttpStatus.CONFLICT.value())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(InventoryServiceException.class)
    public ResponseEntity<ErrorResponse> handleInventoryServiceException(
            InventoryServiceException ex, HttpServletRequest request) {
        
        logger.error("Inventory service error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .error("INVENTORY_SERVICE_ERROR")
            .message(ex.getMessage())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        logger.warn("Validation error: {}", ex.getMessage());
        
        List<String> details = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.add(error.getField() + ": " + error.getDefaultMessage());
        }
        
        ErrorResponse error = ErrorResponse.builder()
            .error("VALIDATION_ERROR")
            .message("Validation failed")
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .details(details)
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        
        logger.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
            .error("INVALID_ARGUMENT")
            .message(ex.getMessage())
            .status(HttpStatus.BAD_REQUEST.value())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .error("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred")
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .path(request.getRequestURI())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
