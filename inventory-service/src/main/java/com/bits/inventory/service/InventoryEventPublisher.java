package com.bits.inventory.service;

import com.bits.inventory.event.*;
import com.bits.inventory.model.InventoryItem;
import com.bits.inventory.model.InventoryReservation;
import com.bits.inventory.model.LowStockAlert;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryEventPublisher {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InventoryEventPublisher.class);
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${app.kafka.topics.inventory-reserved:inventory-reserved}")
    private String inventoryReservedTopic;
    
    @Value("${app.kafka.topics.inventory-released:inventory-released}")
    private String inventoryReleasedTopic;
    
    @Value("${app.kafka.topics.inventory-confirmed:inventory-confirmed}")
    private String inventoryConfirmedTopic;
    
    @Value("${app.kafka.topics.inventory-adjusted:inventory-adjusted}")
    private String inventoryAdjustedTopic;
    
    @Value("${app.kafka.topics.low-stock-alert:low-stock-alert}")
    private String lowStockAlertTopic;
    
    public void publishInventoryReservedEvent(InventoryReservation reservation) {
        try {
            InventoryReservedEvent event = InventoryReservedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .reservationId(reservation.getReservationId())
                .orderId(reservation.getOrderId())
                .productSku(reservation.getProductSku())
                .reservedQuantity(reservation.getReservedQuantity())
                .warehouseLocation(reservation.getWarehouseLocation())
                .expiresAt(reservation.getExpiresAt())
                .timestamp(LocalDateTime.now())
                .build();
            
            publishEvent(inventoryReservedTopic, event.getEventId(), event);
            logger.info("Published inventory reserved event for reservation: {}", reservation.getReservationId());
            
        } catch (Exception e) {
            logger.error("Error publishing inventory reserved event for reservation: {}", 
                reservation.getReservationId(), e);
        }
    }
    
    public void publishInventoryReleasedEvent(InventoryReservation reservation) {
        try {
            InventoryReleasedEvent event = InventoryReleasedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .reservationId(reservation.getReservationId())
                .orderId(reservation.getOrderId())
                .productSku(reservation.getProductSku())
                .releasedQuantity(reservation.getReservedQuantity())
                .warehouseLocation(reservation.getWarehouseLocation())
                .reason("Reservation released")
                .timestamp(LocalDateTime.now())
                .build();
            
            publishEvent(inventoryReleasedTopic, event.getEventId(), event);
            logger.info("Published inventory released event for reservation: {}", reservation.getReservationId());
            
        } catch (Exception e) {
            logger.error("Error publishing inventory released event for reservation: {}", 
                reservation.getReservationId(), e);
        }
    }
    
    public void publishInventoryConfirmedEvent(InventoryReservation reservation) {
        try {
            InventoryConfirmedEvent event = InventoryConfirmedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .reservationId(reservation.getReservationId())
                .orderId(reservation.getOrderId())
                .productSku(reservation.getProductSku())
                .confirmedQuantity(reservation.getReservedQuantity())
                .warehouseLocation(reservation.getWarehouseLocation())
                .timestamp(LocalDateTime.now())
                .build();
            
            publishEvent(inventoryConfirmedTopic, event.getEventId(), event);
            logger.info("Published inventory confirmed event for reservation: {}", reservation.getReservationId());
            
        } catch (Exception e) {
            logger.error("Error publishing inventory confirmed event for reservation: {}", 
                reservation.getReservationId(), e);
        }
    }
    
    public void publishInventoryAdjustedEvent(InventoryItem item, Integer adjustment, String reason) {
        try {
            InventoryAdjustedEvent event = InventoryAdjustedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .productSku(item.getProductSku())
                .warehouseLocation(item.getWarehouseLocation())
                .quantityAdjustment(adjustment)
                .previousQuantity(item.getTotalQuantity() - adjustment)
                .newQuantity(item.getTotalQuantity())
                .reason(reason)
                .performedBy("SYSTEM")
                .timestamp(LocalDateTime.now())
                .build();
            
            publishEvent(inventoryAdjustedTopic, event.getEventId(), event);
            logger.info("Published inventory adjusted event for product: {} adjustment: {}", 
                item.getProductSku(), adjustment);
            
        } catch (Exception e) {
            logger.error("Error publishing inventory adjusted event for product: {}", 
                item.getProductSku(), e);
        }
    }
    
    public void publishLowStockAlertEvent(LowStockAlert alert) {
        try {
            LowStockAlertEvent event = LowStockAlertEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .alertId(alert.getAlertId())
                .productSku(alert.getProductSku())
                .warehouseLocation(alert.getWarehouseLocation())
                .alertType(alert.getAlertType())
                .currentQuantity(alert.getCurrentQuantity())
                .thresholdQuantity(alert.getThresholdQuantity())
                .message(alert.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
            
            publishEvent(lowStockAlertTopic, event.getEventId(), event);
            logger.info("Published low stock alert event for product: {}", alert.getProductSku());
            
        } catch (Exception e) {
            logger.error("Error publishing low stock alert event for product: {}", 
                alert.getProductSku(), e);
        }
    }
    
    private void publishEvent(String topic, String key, Object event) throws JsonProcessingException {
        String eventJson = objectMapper.writeValueAsString(event);
        
        // Add metadata
        Map<String, Object> eventWithMetadata = new HashMap<>();
        eventWithMetadata.put("data", event);
        eventWithMetadata.put("metadata", createMetadata());
        
        String finalEventJson = objectMapper.writeValueAsString(eventWithMetadata);
        
        kafkaTemplate.send(topic, key, finalEventJson)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Failed to publish event to topic: {} key: {}", topic, key, ex);
                } else {
                    logger.debug("Successfully published event to topic: {} key: {}", topic, key);
                }
            });
    }
    
    private Map<String, Object> createMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("service", "inventory-service");
        metadata.put("version", "1.0");
        metadata.put("timestamp", LocalDateTime.now().toString());
        metadata.put("source", "inventory-event-publisher");
        return metadata;
    }
}
