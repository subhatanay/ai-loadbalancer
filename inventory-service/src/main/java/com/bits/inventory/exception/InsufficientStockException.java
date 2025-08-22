package com.bits.inventory.exception;

public class InsufficientStockException extends InventoryServiceException {
    
    public InsufficientStockException(String message) {
        super(message);
    }
}
