package com.bits.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTestItemRequest {
    
    @NotBlank(message = "Product SKU is required")
    private String productSku;
    
    @NotBlank(message = "Product name is required")
    private String productName;
    
    @NotNull(message = "Available quantity is required")
    @Positive(message = "Available quantity must be positive")
    private Integer availableQuantity;
    
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    @Builder.Default
    private BigDecimal price = BigDecimal.valueOf(0.0);
    
    @Builder.Default
    private String warehouseLocation = "MAIN_WAREHOUSE";
    
    @NotNull
    @Builder.Default
    private Integer reorderPoint = 10;
    
    @NotNull
    @Builder.Default
    private Integer maxStock = 100;
}
