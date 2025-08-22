package com.bits.inventory.service;

import com.bits.inventory.dto.*;
import com.bits.inventory.enums.MovementType;
import com.bits.inventory.enums.ProductStatus;
import com.bits.inventory.enums.ReservationStatus;
import com.bits.inventory.model.InventoryItem;
import com.bits.inventory.model.InventoryReservation;
import com.bits.inventory.model.InventoryTransaction;
import com.bits.inventory.model.Product;
import com.bits.inventory.repository.InventoryItemRepository;
import com.bits.inventory.repository.InventoryReservationRepository;
import com.bits.inventory.repository.InventoryTransactionRepository;
import com.bits.inventory.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@Transactional
public class InventoryService {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InventoryService.class);
    
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    
    @Autowired
    private InventoryReservationRepository reservationRepository;
    
    @Autowired
    private InventoryTransactionRepository transactionRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private InventoryEventPublisher eventPublisher;
    
    @Autowired
    private InventoryValidationService validationService;
    
    @Cacheable(value = "inventory-check", key = "#request.productSku + '_' + #request.warehouseLocation")
    public InventoryCheckResponse checkAvailability(InventoryCheckRequest request) {
        logger.info("Checking availability for product: {} quantity: {}", request.getProductSku(), request.getQuantity());
        
        Optional<InventoryItem> inventoryItem = findInventoryItem(request.getProductSku(), request.getWarehouseLocation());
        
        if (inventoryItem.isEmpty()) {
            return InventoryCheckResponse.builder()
                .productSku(request.getProductSku())
                .available(false)
                .availableQuantity(0)
                .requestedQuantity(request.getQuantity())
                .warehouseLocation(request.getWarehouseLocation())
                .message("Product not found in inventory")
                .build();
        }
        
        InventoryItem item = inventoryItem.get();
        boolean available = item.canReserve(request.getQuantity());
        
        return InventoryCheckResponse.builder()
            .productSku(request.getProductSku())
            .available(available)
            .availableQuantity(item.getAvailableQuantity())
            .requestedQuantity(request.getQuantity())
            .warehouseLocation(item.getWarehouseLocation())
            .message(available ? "Stock available" : "Insufficient stock")
            .build();
    }
    
    @Transactional
    @CacheEvict(value = {"inventory-check", "inventory-items"}, allEntries = true)
    public ReservationResponse reserveInventory(ReservationRequest request) {
        logger.info("Reserving inventory for order: {} product: {} quantity: {}", 
            request.getOrderId(), request.getProductSku(), request.getQuantity());
        
        // Validate request
        validationService.validateReservationRequest(request);
        
        Optional<InventoryItem> inventoryItem = findInventoryItem(request.getProductSku(), request.getWarehouseLocation());
        
        if (inventoryItem.isEmpty()) {
            return ReservationResponse.builder()
                .orderId(request.getOrderId())
                .productSku(request.getProductSku())
                .success(false)
                .message("Product not found in inventory")
                .build();
        }
        
        InventoryItem item = inventoryItem.get();
        
        if (!item.canReserve(request.getQuantity())) {
            return ReservationResponse.builder()
                .orderId(request.getOrderId())
                .productSku(request.getProductSku())
                .success(false)
                .message("Insufficient stock available")
                .build();
        }
        
        // Create reservation
        String reservationId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(request.getReservationDurationMinutes());
        
        InventoryReservation reservation = InventoryReservation.builder()
            .reservationId(reservationId)
            .orderId(request.getOrderId())
            .productSku(request.getProductSku())
            .warehouseLocation(item.getWarehouseLocation())
            .reservedQuantity(request.getQuantity())
            .status(ReservationStatus.ACTIVE)
            .expiresAt(expiresAt)
            .build();
        
        // Reserve quantity
        item.reserveQuantity(request.getQuantity());
        
        // Save changes
        inventoryItemRepository.save(item);
        reservationRepository.save(reservation);
        
        // Record transaction
        recordTransaction(item, MovementType.RESERVATION, -request.getQuantity(), request.getOrderId(), "ORDER", "Inventory reserved for order");
        
        // Publish event
        eventPublisher.publishInventoryReservedEvent(reservation);
        
        return ReservationResponse.builder()
            .reservationId(reservationId)
            .orderId(request.getOrderId())
            .productSku(request.getProductSku())
            .reservedQuantity(request.getQuantity())
            .warehouseLocation(item.getWarehouseLocation())
            .status(ReservationStatus.ACTIVE)
            .expiresAt(expiresAt)
            .createdAt(LocalDateTime.now())
            .success(true)
            .message("Inventory reserved successfully")
            .build();
    }
    
    @Transactional
    @CacheEvict(value = {"inventory-check", "inventory-items"}, allEntries = true)
    public boolean releaseReservation(String reservationId) {
        logger.info("Releasing reservation: {}", reservationId);
        
        Optional<InventoryReservation> reservationOpt = reservationRepository.findByReservationId(reservationId);
        if (reservationOpt.isEmpty()) {
            logger.warn("Reservation not found: {}", reservationId);
            return false;
        }
        
        InventoryReservation reservation = reservationOpt.get();
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            logger.warn("Reservation is not active: {} status: {}", reservationId, reservation.getStatus());
            return false;
        }
        
        Optional<InventoryItem> inventoryItem = inventoryItemRepository.findByProductSkuAndWarehouseLocation(
            reservation.getProductSku(), reservation.getWarehouseLocation());
        
        if (inventoryItem.isEmpty()) {
            logger.error("Inventory item not found for reservation: {}", reservationId);
            return false;
        }
        
        InventoryItem item = inventoryItem.get();
        item.releaseReservation(reservation.getReservedQuantity());
        reservation.release();
        
        inventoryItemRepository.save(item);
        reservationRepository.save(reservation);
        
        // Record transaction
        recordTransaction(item, MovementType.RELEASE, reservation.getReservedQuantity(), 
            reservation.getOrderId(), "ORDER", "Reservation released");
        
        // Publish event
        eventPublisher.publishInventoryReleasedEvent(reservation);
        
        return true;
    }
    
    @Transactional
    @CacheEvict(value = {"inventory-check", "inventory-items"}, allEntries = true)
    public boolean confirmReservation(String reservationId) {
        logger.info("Confirming reservation: {}", reservationId);
        
        Optional<InventoryReservation> reservationOpt = reservationRepository.findByReservationId(reservationId);
        if (reservationOpt.isEmpty()) {
            return false;
        }
        
        InventoryReservation reservation = reservationOpt.get();
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            return false;
        }
        
        Optional<InventoryItem> inventoryItem = inventoryItemRepository.findByProductSkuAndWarehouseLocation(
            reservation.getProductSku(), reservation.getWarehouseLocation());
        
        if (inventoryItem.isEmpty()) {
            return false;
        }
        
        InventoryItem item = inventoryItem.get();
        item.confirmReservation(reservation.getReservedQuantity());
        reservation.confirm();
        
        inventoryItemRepository.save(item);
        reservationRepository.save(reservation);
        
        // Record transaction
        recordTransaction(item, MovementType.OUTBOUND, -reservation.getReservedQuantity(), 
            reservation.getOrderId(), "ORDER", "Reservation confirmed - stock deducted");
        
        // Publish event
        eventPublisher.publishInventoryConfirmedEvent(reservation);
        
        return true;
    }
    
    @Transactional
    @CacheEvict(value = {"inventory-check", "inventory-items"}, allEntries = true)
    public boolean adjustStock(StockAdjustmentRequest request) {
        logger.info("Adjusting stock for product: {} adjustment: {}", 
            request.getProductSku(), request.getQuantityAdjustment());
        
        Optional<InventoryItem> inventoryItem = findInventoryItem(request.getProductSku(), request.getWarehouseLocation());
        
        if (inventoryItem.isEmpty()) {
            logger.error("Product not found for stock adjustment: {}", request.getProductSku());
            return false;
        }
        
        InventoryItem item = inventoryItem.get();
        item.adjustStock(request.getQuantityAdjustment());
        
        inventoryItemRepository.save(item);
        
        // Record transaction
        recordTransaction(item, MovementType.ADJUSTMENT, request.getQuantityAdjustment(), 
            null, "ADJUSTMENT", request.getReason());
        
        // Publish event
        eventPublisher.publishInventoryAdjustedEvent(item, request.getQuantityAdjustment(), request.getReason());
        
        return true;
    }
    
    // Temporarily disable caching to debug the issue
    // @Cacheable(value = "inventory-items", key = "#productSku")
    public Optional<InventoryItemDto> getInventoryByProductSku(String productSku) {
        logger.info("Getting inventory for product SKU: {}", productSku);
        
        try {
            // Find all items with the same SKU across all warehouses
            List<InventoryItem> items = inventoryItemRepository.findAll().stream()
                .filter(item -> productSku.equals(item.getProductSku()))
                .collect(Collectors.toList());
            
            logger.info("Found {} items for SKU: {}", items.size(), productSku);
            
            if (items.isEmpty()) {
                logger.info("No items found for SKU: {}", productSku);
                return Optional.empty();
            }
            
            // If only one item, return it directly
            if (items.size() == 1) {
                logger.info("Single item found for SKU: {}", productSku);
                return Optional.of(mapToInventoryItemDto(items.get(0)));
            }
            
            // Aggregate quantities from multiple warehouses
            logger.info("Aggregating {} items for SKU: {}", items.size(), productSku);
            InventoryItem firstItem = items.get(0);
            int totalAvailable = items.stream().mapToInt(InventoryItem::getAvailableQuantity).sum();
            int totalReserved = items.stream().mapToInt(InventoryItem::getReservedQuantity).sum();
            int totalQuantity = items.stream().mapToInt(InventoryItem::getTotalQuantity).sum();
            
            logger.info("Aggregated quantities - Available: {}, Reserved: {}, Total: {}", 
                totalAvailable, totalReserved, totalQuantity);
            
            // Create aggregated DTO directly instead of using builder
            InventoryItemDto aggregatedDto = new InventoryItemDto();
            aggregatedDto.setId(firstItem.getId());
            aggregatedDto.setProductSku(firstItem.getProductSku());
            aggregatedDto.setSku(firstItem.getProductSku());
            aggregatedDto.setWarehouseLocation("MULTIPLE_WAREHOUSES");
            aggregatedDto.setAvailableQuantity(totalAvailable);
            aggregatedDto.setReservedQuantity(totalReserved);
            aggregatedDto.setTotalQuantity(totalQuantity);
            aggregatedDto.setMinimumStockLevel(firstItem.getMinimumStockLevel());
            aggregatedDto.setMaximumStockLevel(firstItem.getMaximumStockLevel());
            aggregatedDto.setReorderPoint(firstItem.getReorderPoint());
            aggregatedDto.setReorderQuantity(firstItem.getReorderQuantity());
            aggregatedDto.setCreatedAt(firstItem.getCreatedAt());
            aggregatedDto.setUpdatedAt(firstItem.getUpdatedAt());
            aggregatedDto.setLowStock(totalAvailable <= firstItem.getMinimumStockLevel());
            aggregatedDto.setOutOfStock(totalAvailable == 0);
            
            logger.info("Successfully created aggregated DTO for SKU: {}", productSku);
            return Optional.of(aggregatedDto);
            
        } catch (Exception e) {
            logger.error("Error getting inventory for SKU: {}", productSku, e);
            return Optional.empty();
        }
    }
    
    @Cacheable(value = "inventory-items")
    public List<InventoryItemDto> getAllInventoryItems() {
        return inventoryItemRepository.findAll().stream()
            .map(this::mapToInventoryItemDto)
            .collect(Collectors.toList());
    }
    
    @Cacheable(value = "low-stock-items")
    public List<InventoryItemDto> getLowStockItems() {
        return inventoryItemRepository.findLowStockItems().stream()
            .map(this::mapToInventoryItemDto)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public InventoryItemDto createInventoryItem(InventoryItem item) {
        InventoryItem saved = inventoryItemRepository.save(item);
        return mapToInventoryItemDto(saved);
    }
    
    private Optional<InventoryItem> findInventoryItem(String productSku, String warehouseLocation) {
        if (warehouseLocation != null && !warehouseLocation.trim().isEmpty()) {
            return inventoryItemRepository.findByProductSkuAndWarehouseLocation(productSku, warehouseLocation);
        } else {
            // When no warehouse is specified, find all inventory items for the product
            // and return the first one with available stock
            List<InventoryItem> allItems = inventoryItemRepository.findAll()
                .stream()
                .filter(item -> item.getProductSku().equals(productSku))
                .filter(item -> item.getAvailableQuantity() > 0)
                .collect(Collectors.toList());
            
            return allItems.isEmpty() ? Optional.empty() : Optional.of(allItems.get(0));
        }
    }
    
    private void recordTransaction(InventoryItem item, MovementType type, Integer quantityChange, 
                                 String referenceId, String referenceType, String notes) {
        InventoryTransaction transaction = InventoryTransaction.builder()
            .transactionId(UUID.randomUUID().toString())
            .productSku(item.getProductSku())
            .warehouseLocation(item.getWarehouseLocation())
            .movementType(type)
            .quantityChange(quantityChange)
            .previousQuantity(item.getTotalQuantity() - quantityChange)
            .newQuantity(item.getTotalQuantity())
            .referenceId(referenceId)
            .referenceType(referenceType)
            .notes(notes)
            .performedBy("SYSTEM")
            .build();
        
        transactionRepository.save(transaction);
    }
    
    private InventoryItemDto mapToInventoryItemDto(InventoryItem item) {
        return InventoryItemDto.builder()
            .id(item.getId())
            .productSku(item.getProductSku())
            .warehouseLocation(item.getWarehouseLocation())
            .totalQuantity(item.getTotalQuantity())
            .availableQuantity(item.getAvailableQuantity())
            .reservedQuantity(item.getReservedQuantity())
            .minimumStockLevel(item.getMinimumStockLevel())
            .maximumStockLevel(item.getMaximumStockLevel())
            .reorderPoint(item.getReorderPoint())
            .reorderQuantity(item.getReorderQuantity())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .lowStock(item.isLowStock())
            .outOfStock(item.isOutOfStock())
            .build();
    }
    
    @Transactional
    public InventoryItemDto createInventoryItem(CreateTestItemRequest request) {
        logger.info("Creating test inventory item: {}", request.getProductSku());
        
        // Check if item already exists in the specified warehouse
        Optional<InventoryItem> existing = inventoryItemRepository.findByProductSkuAndWarehouseLocation(
            request.getProductSku(), request.getWarehouseLocation());
        if (existing.isPresent()) {
            logger.warn("Inventory item already exists: {} in warehouse: {}", 
                request.getProductSku(), request.getWarehouseLocation());
            return mapToInventoryItemDto(existing.get());
        }
        
        InventoryItem item = InventoryItem.builder()
            .productSku(request.getProductSku())
            .availableQuantity(request.getAvailableQuantity())
            .reservedQuantity(0)
            .totalQuantity(request.getAvailableQuantity())
            .warehouseLocation(request.getWarehouseLocation())
            .minimumStockLevel(request.getReorderPoint())
            .maximumStockLevel(request.getMaxStock())
            .reorderPoint(request.getReorderPoint())
            .reorderQuantity(request.getMaxStock() - request.getReorderPoint())
            .build();
        
        InventoryItem savedItem = inventoryItemRepository.save(item);
        logger.info("Created inventory item: {} with quantity: {}", savedItem.getProductSku(), savedItem.getAvailableQuantity());
        
        return mapToInventoryItemDto(savedItem);
    }
    
    // Product Management Methods
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        logger.info("Creating product with SKU: {}", request.getSku());
        
        // Check if product already exists
        if (productRepository.existsBySku(request.getSku())) {
            throw new RuntimeException("Product with SKU " + request.getSku() + " already exists");
        }
        
        Product product = Product.builder()
            .sku(request.getSku())
            .name(request.getName())
            .description(request.getDescription())
            .category(request.getCategory())
            .brand(request.getBrand())
            .price(request.getPrice())
            .weight(request.getWeight())
            .status(ProductStatus.valueOf(request.getStatus()))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        
        Product savedProduct = productRepository.save(product);
        logger.info("Created product: {} with ID: {}", savedProduct.getSku(), savedProduct.getId());
        
        return mapToProductResponse(savedProduct);
    }
    
    public List<ProductResponse> getAllProducts() {
        logger.info("Getting all products");
        List<Product> products = productRepository.findAll();
        return products.stream()
            .map(this::mapToProductResponse)
            .collect(Collectors.toList());
    }
    
    public Optional<ProductResponse> getProductBySku(String sku) {
        logger.info("Getting product by SKU: {}", sku);
        Optional<Product> product = productRepository.findBySku(sku);
        return product.map(this::mapToProductResponse);
    }
    
    public List<ProductResponse> getProductsByCategory(String category) {
        logger.info("Getting products by category: {}", category);
        List<Product> products = productRepository.findByCategory(category);
        return products.stream()
            .map(this::mapToProductResponse)
            .collect(Collectors.toList());
    }
    
    public List<ProductResponse> searchProducts(String query) {
        logger.info("Searching products with query: {}", query);
        List<Product> products = productRepository.findByNameContaining(query);
        return products.stream()
            .map(this::mapToProductResponse)
            .collect(Collectors.toList());
    }
    
    // Enhanced Inventory Management Methods
    @Transactional
    @CacheEvict(value = {"inventory-check", "inventory-items"}, allEntries = true)
    public InventoryItemDto createInventoryItemFromRequest(CreateInventoryItemRequest request) {
        logger.info("Creating inventory item for product: {} at warehouse: {}", 
            request.getProductSku(), request.getWarehouseLocation());
        
        // Check if product exists
        if (!productRepository.existsBySku(request.getProductSku())) {
            throw new RuntimeException("Product with SKU " + request.getProductSku() + " does not exist");
        }
        
        // Check if inventory item already exists for this product and warehouse
        Optional<InventoryItem> existing = inventoryItemRepository
            .findByProductSkuAndWarehouseLocation(request.getProductSku(), request.getWarehouseLocation());
        
        if (existing.isPresent()) {
            throw new RuntimeException("Inventory item already exists for product " + 
                request.getProductSku() + " at warehouse " + request.getWarehouseLocation());
        }
        
        InventoryItem item = InventoryItem.builder()
            .productSku(request.getProductSku())
            .warehouseLocation(request.getWarehouseLocation())
            .totalQuantity(request.getTotalQuantity())
            .availableQuantity(request.getAvailableQuantity())
            .reservedQuantity(request.getReservedQuantity())
            .minimumStockLevel(request.getMinimumStockLevel())
            .maximumStockLevel(request.getMaximumStockLevel())
            .reorderPoint(request.getReorderPoint())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .version(0L)
            .build();
        
        InventoryItem savedItem = inventoryItemRepository.save(item);
        logger.info("Created inventory item: {} at warehouse: {} with quantity: {}", 
            savedItem.getProductSku(), savedItem.getWarehouseLocation(), savedItem.getAvailableQuantity());
        
        return mapToInventoryItemDto(savedItem);
    }
    
    public List<InventoryItemDto> getInventoryByWarehouse(String warehouse) {
        logger.info("Getting inventory for warehouse: {}", warehouse);
        List<InventoryItem> items = inventoryItemRepository.findAll().stream()
            .filter(item -> warehouse.equals(item.getWarehouseLocation()))
            .collect(Collectors.toList());
        
        return items.stream()
            .map(this::mapToInventoryItemDto)
            .collect(Collectors.toList());
    }
    
    // Helper Methods
    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
            .id(product.getId())
            .sku(product.getSku())
            .name(product.getName())
            .description(product.getDescription())
            .category(product.getCategory())
            .brand(product.getBrand())
            .price(product.getPrice())
            .weight(product.getWeight())
            .status(product.getStatus().toString())
            .createdAt(product.getCreatedAt())
            .updatedAt(product.getUpdatedAt())
            .build();
    }
}
