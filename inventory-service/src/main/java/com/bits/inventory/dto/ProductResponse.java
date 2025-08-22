package com.bits.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    
    private Long id;
    private String sku;
    private String name;
    private String description;
    private String category;
    private String brand;
    private BigDecimal price;
    private BigDecimal weight;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
