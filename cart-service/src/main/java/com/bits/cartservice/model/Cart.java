package com.bits.cartservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "cart", timeToLive = 7200) // 2 hours TTL
public class Cart implements Serializable {

    @Id
    private String id; // Format: cart:{userId} or cart:{sessionId}

    @Indexed
    private String userId; // Null for anonymous users

    @Indexed
    private String sessionId; // For anonymous users

    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Builder.Default
    private Integer totalItems = 0;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @Builder.Default
    private CartStatus status = CartStatus.ACTIVE;

    public void addItem(CartItem item) {
        // Check if item already exists
        for (CartItem existingItem : items) {
            if (existingItem.getProductId().equals(item.getProductId())) {
                existingItem.setQuantity(existingItem.getQuantity() + item.getQuantity());
                existingItem.setSubtotal(existingItem.getPrice().multiply(BigDecimal.valueOf(existingItem.getQuantity())));
                updateTotals();
                return;
            }
        }

        // Add new item
        items.add(item);
        updateTotals();
    }

    public void removeItem(String productId) {
        items.removeIf(item -> item.getProductId().equals(productId));
        updateTotals();
    }

    public void updateItemQuantity(String productId, Integer quantity) {
        for (CartItem item : items) {
            if (item.getProductId().equals(productId)) {
                if (quantity <= 0) {
                    removeItem(productId);
                } else {
                    item.setQuantity(quantity);
                    item.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
                    updateTotals();
                }
                return;
            }
        }
    }

    public void clearCart() {
        items.clear();
        totalAmount = BigDecimal.ZERO;
        totalItems = 0;
        status = CartStatus.CLEARED;
        updatedAt = LocalDateTime.now();
    }

    private void updateTotals() {
        totalAmount = items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalItems = items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        updatedAt = LocalDateTime.now();
    }
}
