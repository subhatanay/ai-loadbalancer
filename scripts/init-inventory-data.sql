-- =====================================================
-- AI Load Balancer - Inventory Service Data Initialization
-- =====================================================
-- This script initializes the inventory_db with sample inventory items
-- across multiple warehouses for testing the e-commerce system

-- Connect to inventory database
\c inventory_db;

-- Clear existing data (optional - uncomment if needed)
-- TRUNCATE TABLE inventory_items RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE products RESTART IDENTITY CASCADE;

-- =====================================================
-- PRODUCTS TABLE DATA
-- =====================================================

INSERT INTO products (sku, name, description, category, price, status, created_at, updated_at) VALUES
-- Electronics
('LAPTOP-001', 'Gaming Laptop Pro', 'High-performance gaming laptop with RTX 4070', 'ELECTRONICS', 1299.99, 'ACTIVE', NOW(), NOW()),
('LAPTOP-002', 'Business Laptop', 'Professional laptop for business use', 'ELECTRONICS', 899.99, 'ACTIVE', NOW(), NOW()),
('PHONE-001', 'Smartphone X1', 'Latest flagship smartphone with 5G', 'ELECTRONICS', 699.99, 'ACTIVE', NOW(), NOW()),
('PHONE-002', 'Budget Phone', 'Affordable smartphone for everyday use', 'ELECTRONICS', 299.99, 'ACTIVE', NOW(), NOW()),
('TABLET-001', 'Pro Tablet', 'Professional tablet for creative work', 'ELECTRONICS', 599.99, 'ACTIVE', NOW(), NOW()),
('HEADPHONES-001', 'Wireless Headphones', 'Noise-cancelling wireless headphones', 'ELECTRONICS', 199.99, 'ACTIVE', NOW(), NOW()),
('MONITOR-001', '4K Monitor', '32-inch 4K professional monitor', 'ELECTRONICS', 449.99, 'ACTIVE', NOW(), NOW()),
('KEYBOARD-001', 'Mechanical Keyboard', 'RGB mechanical gaming keyboard', 'ELECTRONICS', 129.99, 'ACTIVE', NOW(), NOW()),

-- Clothing
('SHIRT-001', 'Cotton T-Shirt', 'Premium cotton t-shirt in multiple colors', 'CLOTHING', 29.99, 'ACTIVE', NOW(), NOW()),
('JEANS-001', 'Denim Jeans', 'Classic fit denim jeans', 'CLOTHING', 79.99, 'ACTIVE', NOW(), NOW()),
('JACKET-001', 'Winter Jacket', 'Waterproof winter jacket', 'CLOTHING', 149.99, 'ACTIVE', NOW(), NOW()),
('SHOES-001', 'Running Shoes', 'Professional running shoes', 'CLOTHING', 119.99, 'ACTIVE', NOW(), NOW()),

-- Books
('BOOK-001', 'Programming Guide', 'Complete guide to modern programming', 'BOOKS', 49.99, 'ACTIVE', NOW(), NOW()),
('BOOK-002', 'Business Strategy', 'Strategic thinking for business leaders', 'BOOKS', 39.99, 'ACTIVE', NOW(), NOW()),
('BOOK-003', 'Science Fiction Novel', 'Bestselling science fiction adventure', 'BOOKS', 19.99, 'ACTIVE', NOW(), NOW()),

-- Home & Garden
('CHAIR-001', 'Office Chair', 'Ergonomic office chair with lumbar support', 'HOME_GARDEN', 299.99, 'ACTIVE', NOW(), NOW()),
('DESK-001', 'Standing Desk', 'Adjustable height standing desk', 'HOME_GARDEN', 399.99, 'ACTIVE', NOW(), NOW()),
('LAMP-001', 'LED Desk Lamp', 'Adjustable LED desk lamp', 'HOME_GARDEN', 79.99, 'ACTIVE', NOW(), NOW()),

-- Sports & Outdoors
('BIKE-001', 'Mountain Bike', 'Professional mountain bike', 'SPORTS', 899.99, 'ACTIVE', NOW(), NOW()),
('TENT-001', 'Camping Tent', '4-person waterproof camping tent', 'SPORTS', 199.99, 'ACTIVE', NOW(), NOW()),
('BACKPACK-001', 'Hiking Backpack', '50L hiking backpack', 'SPORTS', 129.99, 'ACTIVE', NOW(), NOW())
ON CONFLICT (sku) DO NOTHING;

-- =====================================================
-- INVENTORY ITEMS TABLE DATA
-- =====================================================

INSERT INTO inventory_items (
    product_sku, 
    warehouse_location, 
    total_quantity, 
    available_quantity, 
    reserved_quantity, 
    minimum_stock_level, 
    maximum_stock_level, 
    reorder_point, 
    reorder_quantity,
    created_at, 
    updated_at,
    version
) VALUES

-- LAPTOP-001 (Gaming Laptop Pro) - Multi-warehouse
('LAPTOP-001', 'WAREHOUSE_US_EAST', 50, 45, 5, 10, 100, 15, 25, NOW(), NOW(), 0),
('LAPTOP-001', 'WAREHOUSE_US_WEST', 30, 28, 2, 10, 100, 15, 25, NOW(), NOW(), 0),
('LAPTOP-001', 'WAREHOUSE_EU_CENTRAL', 25, 23, 2, 8, 80, 12, 20, NOW(), NOW(), 0),

-- LAPTOP-002 (Business Laptop) - Multi-warehouse
('LAPTOP-002', 'WAREHOUSE_US_EAST', 40, 38, 2, 8, 80, 12, 20, NOW(), NOW(), 0),
('LAPTOP-002', 'WAREHOUSE_US_WEST', 35, 33, 2, 8, 80, 12, 20, NOW(), NOW(), 0),

-- PHONE-001 (Smartphone X1) - Multi-warehouse
('PHONE-001', 'WAREHOUSE_US_EAST', 200, 180, 20, 50, 500, 75, 100, NOW(), NOW(), 0),
('PHONE-001', 'WAREHOUSE_US_WEST', 150, 140, 10, 50, 500, 75, 100, NOW(), NOW(), 0),
('PHONE-001', 'WAREHOUSE_EU_CENTRAL', 100, 95, 5, 30, 300, 50, 75, NOW(), NOW(), 0),
('PHONE-001', 'WAREHOUSE_ASIA_PACIFIC', 180, 170, 10, 40, 400, 60, 80, NOW(), NOW(), 0),

-- PHONE-002 (Budget Phone) - Multi-warehouse
('PHONE-002', 'WAREHOUSE_US_EAST', 300, 280, 20, 75, 600, 100, 150, NOW(), NOW(), 0),
('PHONE-002', 'WAREHOUSE_US_WEST', 250, 240, 10, 75, 600, 100, 150, NOW(), NOW(), 0),
('PHONE-002', 'WAREHOUSE_EU_CENTRAL', 200, 190, 10, 50, 400, 75, 100, NOW(), NOW(), 0),

-- TABLET-001 (Pro Tablet) - Multi-warehouse
('TABLET-001', 'WAREHOUSE_US_EAST', 80, 75, 5, 20, 200, 30, 40, NOW(), NOW(), 0),
('TABLET-001', 'WAREHOUSE_US_WEST', 60, 58, 2, 20, 200, 30, 40, NOW(), NOW(), 0),

-- HEADPHONES-001 (Wireless Headphones) - Single warehouse
('HEADPHONES-001', 'WAREHOUSE_US_EAST', 75, 70, 5, 25, 200, 40, 50, NOW(), NOW(), 0),

-- MONITOR-001 (4K Monitor) - Multi-warehouse
('MONITOR-001', 'WAREHOUSE_US_EAST', 40, 35, 5, 10, 100, 15, 25, NOW(), NOW(), 0),
('MONITOR-001', 'WAREHOUSE_US_WEST', 30, 28, 2, 10, 100, 15, 25, NOW(), NOW(), 0),

-- KEYBOARD-001 (Mechanical Keyboard) - Single warehouse
('KEYBOARD-001', 'WAREHOUSE_US_EAST', 100, 95, 5, 30, 300, 50, 75, NOW(), NOW(), 0),

-- SHIRT-001 (Cotton T-Shirt) - Multi-warehouse
('SHIRT-001', 'WAREHOUSE_US_EAST', 500, 480, 20, 100, 1000, 150, 200, NOW(), NOW(), 0),
('SHIRT-001', 'WAREHOUSE_US_WEST', 300, 290, 10, 100, 1000, 150, 200, NOW(), NOW(), 0),
('SHIRT-001', 'WAREHOUSE_EU_CENTRAL', 400, 380, 20, 80, 800, 120, 160, NOW(), NOW(), 0),

-- JEANS-001 (Denim Jeans) - Multi-warehouse
('JEANS-001', 'WAREHOUSE_US_EAST', 200, 190, 10, 50, 500, 75, 100, NOW(), NOW(), 0),
('JEANS-001', 'WAREHOUSE_US_WEST', 150, 145, 5, 50, 500, 75, 100, NOW(), NOW(), 0),

-- JACKET-001 (Winter Jacket) - Multi-warehouse
('JACKET-001', 'WAREHOUSE_US_EAST', 80, 75, 5, 20, 200, 30, 40, NOW(), NOW(), 0),
('JACKET-001', 'WAREHOUSE_EU_CENTRAL', 100, 95, 5, 25, 250, 40, 50, NOW(), NOW(), 0),

-- SHOES-001 (Running Shoes) - Multi-warehouse
('SHOES-001', 'WAREHOUSE_US_EAST', 120, 110, 10, 30, 300, 50, 75, NOW(), NOW(), 0),
('SHOES-001', 'WAREHOUSE_US_WEST', 100, 95, 5, 30, 300, 50, 75, NOW(), NOW(), 0),

-- BOOK-001 (Programming Guide) - Single warehouse
('BOOK-001', 'WAREHOUSE_US_EAST', 100, 95, 5, 20, 200, 30, 50, NOW(), NOW(), 0),

-- BOOK-002 (Business Strategy) - Single warehouse
('BOOK-002', 'WAREHOUSE_US_EAST', 80, 75, 5, 15, 150, 25, 40, NOW(), NOW(), 0),

-- BOOK-003 (Science Fiction Novel) - Multi-warehouse
('BOOK-003', 'WAREHOUSE_US_EAST', 200, 190, 10, 50, 500, 75, 100, NOW(), NOW(), 0),
('BOOK-003', 'WAREHOUSE_US_WEST', 150, 145, 5, 50, 500, 75, 100, NOW(), NOW(), 0),

-- CHAIR-001 (Office Chair) - Multi-warehouse
('CHAIR-001', 'WAREHOUSE_US_EAST', 60, 55, 5, 15, 150, 25, 35, NOW(), NOW(), 0),
('CHAIR-001', 'WAREHOUSE_US_WEST', 40, 38, 2, 15, 150, 25, 35, NOW(), NOW(), 0),

-- DESK-001 (Standing Desk) - Multi-warehouse
('DESK-001', 'WAREHOUSE_US_EAST', 30, 28, 2, 8, 80, 12, 20, NOW(), NOW(), 0),
('DESK-001', 'WAREHOUSE_US_WEST', 25, 23, 2, 8, 80, 12, 20, NOW(), NOW(), 0),

-- LAMP-001 (LED Desk Lamp) - Single warehouse
('LAMP-001', 'WAREHOUSE_US_EAST', 150, 140, 10, 40, 400, 60, 80, NOW(), NOW(), 0),

-- BIKE-001 (Mountain Bike) - Multi-warehouse
('BIKE-001', 'WAREHOUSE_US_EAST', 20, 18, 2, 5, 50, 8, 15, NOW(), NOW(), 0),
('BIKE-001', 'WAREHOUSE_US_WEST', 15, 14, 1, 5, 50, 8, 15, NOW(), NOW(), 0),

-- TENT-001 (Camping Tent) - Single warehouse
('TENT-001', 'WAREHOUSE_US_EAST', 50, 45, 5, 12, 120, 20, 30, NOW(), NOW(), 0),

-- BACKPACK-001 (Hiking Backpack) - Multi-warehouse
('BACKPACK-001', 'WAREHOUSE_US_EAST', 80, 75, 5, 20, 200, 30, 40, NOW(), NOW(), 0),
('BACKPACK-001', 'WAREHOUSE_US_WEST', 60, 58, 2, 20, 200, 30, 40, NOW(), NOW(), 0);

-- =====================================================
-- SUMMARY QUERIES
-- =====================================================

-- Show inventory summary by product
SELECT 
    product_sku,
    COUNT(*) as warehouse_count,
    SUM(total_quantity) as total_stock,
    SUM(available_quantity) as total_available,
    SUM(reserved_quantity) as total_reserved
FROM inventory_items 
GROUP BY product_sku 
ORDER BY product_sku;

-- Show low stock items (available <= minimum_stock_level)
SELECT 
    i.product_sku,
    p.name as product_name,
    i.warehouse_location,
    i.available_quantity,
    i.minimum_stock_level,
    i.reorder_point
FROM inventory_items i
JOIN products p ON i.product_sku = p.sku
WHERE i.available_quantity <= i.minimum_stock_level
ORDER BY i.product_sku, i.warehouse_location;

-- Show multi-warehouse products
SELECT 
    product_sku,
    COUNT(*) as warehouse_count,
    STRING_AGG(warehouse_location, ', ' ORDER BY warehouse_location) as warehouses
FROM inventory_items 
GROUP BY product_sku 
HAVING COUNT(*) > 1
ORDER BY product_sku;

-- Show total inventory value by warehouse
SELECT 
    i.warehouse_location,
    COUNT(DISTINCT i.product_sku) as unique_products,
    SUM(i.total_quantity) as total_items,
    SUM(i.available_quantity) as available_items,
    ROUND(SUM(i.total_quantity * p.price), 2) as total_value
FROM inventory_items i
JOIN products p ON i.product_sku = p.sku
GROUP BY i.warehouse_location
ORDER BY total_value DESC;

-- =====================================================
-- VERIFICATION
-- =====================================================

SELECT 'Inventory initialization completed successfully!' as status;
SELECT COUNT(*) as total_products FROM products;
SELECT COUNT(*) as total_inventory_items FROM inventory_items;
SELECT COUNT(DISTINCT warehouse_location) as total_warehouses FROM inventory_items;
