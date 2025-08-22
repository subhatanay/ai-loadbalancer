package com.bits.inventory.event;

import com.bits.inventory.enums.AlertType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlertEvent {
    
    private String eventId;
    private String alertId;
    private String productSku;
    private String warehouseLocation;
    private AlertType alertType;
    private Integer currentQuantity;
    private Integer thresholdQuantity;
    private String message;
    private LocalDateTime timestamp;
}
