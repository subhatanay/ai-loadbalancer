package com.bits.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    @Valid
    private List<OrderItemRequest> items;
    
    @Valid
    @NotNull(message = "Shipping address is required")
    private ShippingAddressRequest shippingAddress;
    
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
