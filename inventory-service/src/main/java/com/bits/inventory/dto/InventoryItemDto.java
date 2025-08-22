package com.bits.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemDto implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String productSku;
    private String warehouseLocation;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer minimumStockLevel;
    private Integer maximumStockLevel;
    private Integer reorderPoint;
    private Integer reorderQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean lowStock;
    private Boolean outOfStock;
    
    // Manual getters for compatibility
    public String getSku() {
        return productSku;
    }
    
    public void setSku(String sku) {
        this.productSku = sku;
    }
    
    // Manual builder methods for compatibility
    public static InventoryItemDtoBuilder builder() {
        return new InventoryItemDtoBuilder();
    }
    
    public static class InventoryItemDtoBuilder {
        private Long id;
        private String productSku;
        private String warehouseLocation;
        private Integer totalQuantity;
        private Integer availableQuantity;
        private Integer reservedQuantity;
        private Integer minimumStockLevel;
        private Integer maximumStockLevel;
        private Integer reorderPoint;
        private Integer reorderQuantity;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean lowStock;
        private Boolean outOfStock;
        
        public InventoryItemDtoBuilder id(Long id) {
            this.id = id;
            return this;
        }
        
        public InventoryItemDtoBuilder productSku(String productSku) {
            this.productSku = productSku;
            return this;
        }
        
        public InventoryItemDtoBuilder warehouseLocation(String warehouseLocation) {
            this.warehouseLocation = warehouseLocation;
            return this;
        }
        
        public InventoryItemDtoBuilder totalQuantity(Integer totalQuantity) {
            this.totalQuantity = totalQuantity;
            return this;
        }
        
        public InventoryItemDtoBuilder availableQuantity(Integer availableQuantity) {
            this.availableQuantity = availableQuantity;
            return this;
        }
        
        public InventoryItemDtoBuilder reservedQuantity(Integer reservedQuantity) {
            this.reservedQuantity = reservedQuantity;
            return this;
        }
        
        public InventoryItemDtoBuilder minimumStockLevel(Integer minimumStockLevel) {
            this.minimumStockLevel = minimumStockLevel;
            return this;
        }
        
        public InventoryItemDtoBuilder maximumStockLevel(Integer maximumStockLevel) {
            this.maximumStockLevel = maximumStockLevel;
            return this;
        }
        
        public InventoryItemDtoBuilder reorderPoint(Integer reorderPoint) {
            this.reorderPoint = reorderPoint;
            return this;
        }
        
        public InventoryItemDtoBuilder reorderQuantity(Integer reorderQuantity) {
            this.reorderQuantity = reorderQuantity;
            return this;
        }
        
        public InventoryItemDtoBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public InventoryItemDtoBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public InventoryItemDtoBuilder lowStock(Boolean lowStock) {
            this.lowStock = lowStock;
            return this;
        }
        
        public InventoryItemDtoBuilder outOfStock(Boolean outOfStock) {
            this.outOfStock = outOfStock;
            return this;
        }
        
        public InventoryItemDto build() {
            InventoryItemDto dto = new InventoryItemDto();
            dto.id = this.id;
            dto.productSku = this.productSku;
            dto.warehouseLocation = this.warehouseLocation;
            dto.totalQuantity = this.totalQuantity;
            dto.availableQuantity = this.availableQuantity;
            dto.reservedQuantity = this.reservedQuantity;
            dto.minimumStockLevel = this.minimumStockLevel;
            dto.maximumStockLevel = this.maximumStockLevel;
            dto.reorderPoint = this.reorderPoint;
            dto.reorderQuantity = this.reorderQuantity;
            dto.createdAt = this.createdAt;
            dto.updatedAt = this.updatedAt;
            dto.lowStock = this.lowStock;
            dto.outOfStock = this.outOfStock;
            return dto;
        }
    }
}
