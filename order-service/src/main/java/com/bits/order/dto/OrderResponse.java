package com.bits.order.dto;

import com.bits.order.model.OrderStatus;
import com.bits.order.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    
    private Long id;
    private String orderNumber;
    private String userId;
    private String userEmail;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private OrderStatus status;
    private String paymentId;
    private PaymentStatus paymentStatus;
    private ShippingAddressResponse shippingAddress;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
