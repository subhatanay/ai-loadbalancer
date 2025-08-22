package com.bits.inventory.controller;

import com.bits.inventory.dto.*;
import com.bits.inventory.service.InventoryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InventoryController.class);
    
    @Autowired
    private InventoryService inventoryService;
    
    private final Counter inventoryCheckCounter;
    private final Counter reservationCounter;
    private final Timer inventoryCheckTimer;
    private final Timer reservationTimer;
    
    public InventoryController(MeterRegistry meterRegistry) {
        this.inventoryCheckCounter = Counter.builder("inventory_check_requests_total")
            .description("Total number of inventory check requests")
            .register(meterRegistry);
        
        this.reservationCounter = Counter.builder("inventory_reservation_requests_total")
            .description("Total number of inventory reservation requests")
            .register(meterRegistry);
        
        this.inventoryCheckTimer = Timer.builder("inventory_check_duration")
            .description("Time taken to check inventory")
            .register(meterRegistry);
        
        this.reservationTimer = Timer.builder("inventory_reservation_duration")
            .description("Time taken to process reservation")
            .register(meterRegistry);
    }
    
    @PostMapping("/check")
    public ResponseEntity<InventoryCheckResponse> checkInventory(@Valid @RequestBody InventoryCheckRequest request,
                                                               HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Checking inventory for product: {} by user: {}", request.getProductSku(), userId);
        
        inventoryCheckCounter.increment();
        
        try {
            return inventoryCheckTimer.recordCallable(() -> {
                InventoryCheckResponse response = inventoryService.checkAvailability(request);
                return ResponseEntity.ok(response);
            });
        } catch (Exception e) {
            logger.error("Error checking inventory", e);
            throw new RuntimeException("Error checking inventory", e);
        }
    }
    
    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserveInventory(@Valid @RequestBody ReservationRequest request,
                                                              HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Reserving inventory for order: {} by user: {}", request.getOrderId(), userId);
        
        reservationCounter.increment();
        
        try {
            return reservationTimer.recordCallable(() -> {
                ReservationResponse response = inventoryService.reserveInventory(request);
                
                if (response.getSuccess()) {
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
                }
            });
        } catch (Exception e) {
            logger.error("Error reserving inventory", e);
            throw new RuntimeException("Error reserving inventory", e);
        }
    }
    
    @PostMapping("/release/{reservationId}")
    public ResponseEntity<String> releaseInventory(@PathVariable String reservationId,
                                                  HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Releasing inventory reservation: {} by user: {}", reservationId, userId);
        
        boolean released = inventoryService.releaseReservation(reservationId);
        
        if (released) {
            return ResponseEntity.ok("Reservation released successfully");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Reservation not found or already released");
        }
    }
    
    @PostMapping("/confirm/{reservationId}")
    public ResponseEntity<String> confirmReservation(@PathVariable String reservationId,
                                                    HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Confirming inventory reservation: {} by user: {}", reservationId, userId);
        
        boolean confirmed = inventoryService.confirmReservation(reservationId);
        
        if (confirmed) {
            return ResponseEntity.ok("Reservation confirmed successfully");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Reservation not found or already confirmed");
        }
    }
    
    @PostMapping("/adjust")
    public ResponseEntity<String> adjustInventory(@Valid @RequestBody StockAdjustmentRequest request,
                                                 HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Adjusting inventory for product: {} by user: {}", request.getProductSku(), userId);
        
        // Set the performed by field
        request.setPerformedBy(userId);
        
        boolean adjusted = inventoryService.adjustStock(request);
        
        if (adjusted) {
            return ResponseEntity.ok("Inventory adjusted successfully");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Product not found");
        }
    }
    
    @GetMapping("/product/{productSku}")
    public ResponseEntity<InventoryItemDto> getInventoryByProductSku(@PathVariable String productSku,
                                                                   HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting inventory for product: {} by user: {}", productSku, userId);
        
        Optional<InventoryItemDto> inventory = inventoryService.getInventoryByProductSku(productSku);
        
        if (inventory.isPresent()) {
            return ResponseEntity.ok(inventory.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<InventoryItemDto>> getAllInventoryItems(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting all inventory items by user: {}", userId);
        
        List<InventoryItemDto> items = inventoryService.getAllInventoryItems();
        return ResponseEntity.ok(items);
    }
    
    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryItemDto>> getLowStockItems(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting low stock items by user: {}", userId);
        
        List<InventoryItemDto> items = inventoryService.getLowStockItems();
        return ResponseEntity.ok(items);
    }
    
    @PostMapping("/create")
    public ResponseEntity<InventoryItemDto> createInventoryItem(@Valid @RequestBody CreateTestItemRequest request,
                                                              HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Creating inventory item: {} by user: {}", request.getProductSku(), userId);
        
        InventoryItemDto item = inventoryService.createInventoryItem(request);
        return ResponseEntity.ok(item);
    }
    
    // Product Management APIs
    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request,
                                                        HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Creating product: {} by user: {}", request.getSku(), userId);
        
        try {
            ProductResponse product = inventoryService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(product);
        } catch (Exception e) {
            logger.error("Error creating product: {}", request.getSku(), e);
            throw new RuntimeException("Error creating product: " + e.getMessage(), e);
        }
    }
    
    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getAllProducts(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting all products by user: {}", userId);
        
        List<ProductResponse> products = inventoryService.getAllProducts();
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/products/{sku}")
    public ResponseEntity<ProductResponse> getProductBySku(@PathVariable String sku,
                                                          HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting product: {} by user: {}", sku, userId);
        
        Optional<ProductResponse> product = inventoryService.getProductBySku(sku);
        
        if (product.isPresent()) {
            return ResponseEntity.ok(product.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/products/category/{category}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable String category,
                                                                      HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting products by category: {} by user: {}", category, userId);
        
        List<ProductResponse> products = inventoryService.getProductsByCategory(category);
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<ProductResponse>> searchProducts(@RequestParam String query,
                                                               HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Searching products with query: {} by user: {}", query, userId);
        
        List<ProductResponse> products = inventoryService.searchProducts(query);
        return ResponseEntity.ok(products);
    }
    
    // Inventory Item Management APIs
    @PostMapping("/items")
    public ResponseEntity<InventoryItemDto> createInventoryItem(@Valid @RequestBody CreateInventoryItemRequest request,
                                                               HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Creating inventory item for product: {} at warehouse: {} by user: {}", 
                   request.getProductSku(), request.getWarehouseLocation(), userId);
        
        try {
            InventoryItemDto item = inventoryService.createInventoryItemFromRequest(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (Exception e) {
            logger.error("Error creating inventory item for product: {}", request.getProductSku(), e);
            throw new RuntimeException("Error creating inventory item: " + e.getMessage(), e);
        }
    }
    
    @GetMapping("/items")
    public ResponseEntity<List<InventoryItemDto>> getAllInventoryItemsDetailed(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting all detailed inventory items by user: {}", userId);
        
        List<InventoryItemDto> items = inventoryService.getAllInventoryItems();
        return ResponseEntity.ok(items);
    }
    
    @GetMapping("/items/warehouse/{warehouse}")
    public ResponseEntity<List<InventoryItemDto>> getInventoryByWarehouse(@PathVariable String warehouse,
                                                                         HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting inventory for warehouse: {} by user: {}", warehouse, userId);
        
        List<InventoryItemDto> items = inventoryService.getInventoryByWarehouse(warehouse);
        return ResponseEntity.ok(items);
    }
    
    // Simplified endpoints for load testing
    @GetMapping("")
    public ResponseEntity<List<InventoryItemDto>> getInventorySimple(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        logger.info("Getting inventory (simple endpoint) by user: {}", userId);
        
        List<InventoryItemDto> items = inventoryService.getAllInventoryItems();
        return ResponseEntity.ok(items);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Inventory Service is healthy");
    }
    
    // Removed extractUsername method - now using extractUserId for proper JWT-based authentication
    
    private String extractUserId(HttpServletRequest request) {
        // First try to get from request attributes (set by JWT filter)
        String userId = (String) request.getAttribute("userId");
        
        if (userId != null) {
            return userId;
        }
        
        // Fallback to security context (same as order service)
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        }
        
        throw new IllegalStateException("User ID not found in request context");
    }
}
