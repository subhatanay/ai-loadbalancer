#!/usr/bin/env python3
"""
Inventory Data Setup Script for AI Load Balancer E-commerce Platform
Creates realistic product catalog and inventory data for load testing scenarios
"""

import requests
import json
import time
import random
from datetime import datetime
from typing import List, Dict, Any
import logging

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class InventoryDataSetup:
    """Setup inventory data for load testing"""
    
    def __init__(self, base_url: str = "http://localhost:8080/proxy"):
        self.base_url = base_url
        self.session = requests.Session()
        self.jwt_token = None
        
    def authenticate_admin(self):
        """Authenticate as admin user for inventory management"""
        # First try to register admin user
        admin_data = {
            "firstName": "Admin",
            "lastName": "User",
            "email": "admin@loadtest.com",
            "password": "AdminPass123!",
            "phoneNumber": "+1234567890"
        }
        
        try:
            response = self.session.post(
                f"{self.base_url}/user-service/api/users/register",
                json=admin_data,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            logger.info(f"Admin registration response: {response.status_code}")
        except Exception as e:
            logger.warning(f"Admin registration failed (may already exist): {e}")
        
        # Login as admin
        login_data = {
            "email": "admin@loadtest.com",
            "password": "AdminPass123!"
        }
        
        try:
            response = self.session.post(
                f"{self.base_url}/user-service/api/users/login",
                json=login_data,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            
            if response.status_code == 200:
                result = response.json()
                self.jwt_token = result.get('token')
                logger.info("Successfully authenticated as admin")
                return True
            else:
                logger.error(f"Admin login failed: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"Admin authentication error: {e}")
            return False
    
    def create_product(self, product_data: Dict) -> bool:
        """Create a product in the inventory service"""
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.jwt_token}'
        }
        
        try:
            response = self.session.post(
                f"{self.base_url}/inventory-service/api/inventory/products",
                json=product_data,
                headers=headers,
                timeout=30
            )
            
            if response.status_code in [200, 201]:
                logger.info(f"Created product: {product_data['sku']}")
                return True
            else:
                logger.warning(f"Failed to create product {product_data['sku']}: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"Error creating product {product_data['sku']}: {e}")
            return False
    
    def create_inventory_item(self, inventory_data: Dict) -> bool:
        """Create inventory item for a product"""
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {self.jwt_token}'
        }
        
        try:
            response = self.session.post(
                f"{self.base_url}/inventory-service/api/inventory/items",
                json=inventory_data,
                headers=headers,
                timeout=30
            )
            
            if response.status_code in [200, 201]:
                logger.info(f"Created inventory for: {inventory_data['productSku']} at {inventory_data['warehouseLocation']}")
                return True
            else:
                logger.warning(f"Failed to create inventory for {inventory_data['productSku']}: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"Error creating inventory for {inventory_data['productSku']}: {e}")
            return False
    
    def get_product_catalog(self) -> List[Dict]:
        """Generate realistic product catalog for e-commerce load testing"""
        products = [
            # Electronics - High demand items for load testing
            {
                "sku": "LAPTOP-001",
                "name": "Gaming Laptop Pro 15",
                "description": "High-performance gaming laptop with RTX graphics",
                "category": "Electronics",
                "brand": "TechPro",
                "price": 1299.99,
                "weight": 2.5,
                "status": "ACTIVE"
            },
            {
                "sku": "LAPTOP-002", 
                "name": "Business Laptop Ultra",
                "description": "Lightweight business laptop for professionals",
                "category": "Electronics",
                "brand": "BizTech",
                "price": 899.99,
                "weight": 1.8,
                "status": "ACTIVE"
            },
            {
                "sku": "PHONE-001",
                "name": "Smartphone Pro Max",
                "description": "Latest flagship smartphone with advanced camera",
                "category": "Electronics",
                "brand": "MobileTech",
                "price": 999.99,
                "weight": 0.2,
                "status": "ACTIVE"
            },
            {
                "sku": "PHONE-002",
                "name": "Budget Smartphone",
                "description": "Affordable smartphone with essential features",
                "category": "Electronics", 
                "brand": "ValueMobile",
                "price": 299.99,
                "weight": 0.18,
                "status": "ACTIVE"
            },
            {
                "sku": "TABLET-001",
                "name": "Tablet Pro 12",
                "description": "Professional tablet for creative work",
                "category": "Electronics",
                "brand": "CreativeTech",
                "price": 799.99,
                "weight": 0.6,
                "status": "ACTIVE"
            },
            {
                "sku": "WATCH-001",
                "name": "Smart Watch Fitness",
                "description": "Advanced fitness tracking smartwatch",
                "category": "Electronics",
                "brand": "FitTech",
                "price": 249.99,
                "weight": 0.05,
                "status": "ACTIVE"
            },
            {
                "sku": "HEADPHONES-001",
                "name": "Wireless Noise Cancelling Headphones",
                "description": "Premium wireless headphones with active noise cancellation",
                "category": "Electronics",
                "brand": "AudioPro",
                "price": 299.99,
                "weight": 0.3,
                "status": "ACTIVE"
            },
            {
                "sku": "CAMERA-001",
                "name": "DSLR Camera Professional",
                "description": "Professional DSLR camera for photography enthusiasts",
                "category": "Electronics",
                "brand": "PhotoPro",
                "price": 1599.99,
                "weight": 1.2,
                "status": "ACTIVE"
            },
            {
                "sku": "SPEAKER-001",
                "name": "Bluetooth Speaker Portable",
                "description": "Portable Bluetooth speaker with rich sound",
                "category": "Electronics",
                "brand": "SoundTech",
                "price": 79.99,
                "weight": 0.4,
                "status": "ACTIVE"
            },
            {
                "sku": "KEYBOARD-001",
                "name": "Mechanical Gaming Keyboard",
                "description": "RGB mechanical keyboard for gaming",
                "category": "Electronics",
                "brand": "GameTech",
                "price": 149.99,
                "weight": 1.0,
                "status": "ACTIVE"
            },
            {
                "sku": "MOUSE-001",
                "name": "Gaming Mouse Precision",
                "description": "High-precision gaming mouse with programmable buttons",
                "category": "Electronics",
                "brand": "GameTech",
                "price": 69.99,
                "weight": 0.12,
                "status": "ACTIVE"
            },
            {
                "sku": "MONITOR-001",
                "name": "4K Gaming Monitor 27",
                "description": "27-inch 4K gaming monitor with high refresh rate",
                "category": "Electronics",
                "brand": "DisplayPro",
                "price": 449.99,
                "weight": 5.5,
                "status": "ACTIVE"
            },
            
            # Home & Garden - Medium demand
            {
                "sku": "CHAIR-001",
                "name": "Ergonomic Office Chair",
                "description": "Comfortable ergonomic chair for office work",
                "category": "Furniture",
                "brand": "ComfortHome",
                "price": 299.99,
                "weight": 15.0,
                "status": "ACTIVE"
            },
            {
                "sku": "DESK-001",
                "name": "Standing Desk Adjustable",
                "description": "Height-adjustable standing desk",
                "category": "Furniture",
                "brand": "WorkSpace",
                "price": 599.99,
                "weight": 25.0,
                "status": "ACTIVE"
            },
            {
                "sku": "LAMP-001",
                "name": "LED Desk Lamp Smart",
                "description": "Smart LED desk lamp with app control",
                "category": "Home",
                "brand": "SmartHome",
                "price": 89.99,
                "weight": 1.5,
                "status": "ACTIVE"
            },
            
            # Books - Lower demand but consistent
            {
                "sku": "BOOK-001",
                "name": "Programming Guide Advanced",
                "description": "Comprehensive guide to advanced programming concepts",
                "category": "Books",
                "brand": "TechBooks",
                "price": 49.99,
                "weight": 0.8,
                "status": "ACTIVE"
            },
            {
                "sku": "BOOK-002",
                "name": "Data Science Handbook",
                "description": "Complete handbook for data science practitioners",
                "category": "Books",
                "brand": "DataPress",
                "price": 59.99,
                "weight": 1.0,
                "status": "ACTIVE"
            },
            
            # Clothing - Seasonal demand
            {
                "sku": "SHIRT-001",
                "name": "Cotton T-Shirt Premium",
                "description": "Premium cotton t-shirt in various colors",
                "category": "Clothing",
                "brand": "FashionPro",
                "price": 29.99,
                "weight": 0.2,
                "status": "ACTIVE"
            },
            {
                "sku": "JEANS-001",
                "name": "Denim Jeans Classic",
                "description": "Classic fit denim jeans",
                "category": "Clothing",
                "brand": "DenimCo",
                "price": 79.99,
                "weight": 0.6,
                "status": "ACTIVE"
            },
            
            # Sports & Fitness
            {
                "sku": "YOGA-001",
                "name": "Yoga Mat Premium",
                "description": "Non-slip premium yoga mat",
                "category": "Sports",
                "brand": "FitLife",
                "price": 39.99,
                "weight": 1.2,
                "status": "ACTIVE"
            },
            
            # Beauty & Personal Care - High conversion category
            {
                "sku": "SKINCARE-001",
                "name": "Anti-Aging Serum Premium",
                "description": "Advanced anti-aging serum with vitamin C",
                "category": "Beauty",
                "brand": "GlowTech",
                "price": 89.99,
                "weight": 0.1,
                "status": "ACTIVE"
            },
            {
                "sku": "MAKEUP-001",
                "name": "Foundation Liquid Matte",
                "description": "Long-lasting matte foundation",
                "category": "Beauty",
                "brand": "BeautyPro",
                "price": 45.99,
                "weight": 0.15,
                "status": "ACTIVE"
            },
            
            # Home & Kitchen - Popular category
            {
                "sku": "BLENDER-001",
                "name": "High-Speed Blender Pro",
                "description": "Professional-grade high-speed blender",
                "category": "Kitchen",
                "brand": "BlendMaster",
                "price": 199.99,
                "weight": 4.5,
                "status": "ACTIVE"
            },
            {
                "sku": "COOKWARE-001",
                "name": "Non-Stick Pan Set",
                "description": "5-piece non-stick cookware set",
                "category": "Kitchen",
                "brand": "ChefPro",
                "price": 129.99,
                "weight": 3.2,
                "status": "ACTIVE"
            },
            
            # Automotive - Seasonal demand
            {
                "sku": "TIRE-001",
                "name": "All-Season Tire 225/60R16",
                "description": "High-performance all-season tire",
                "category": "Automotive",
                "brand": "RoadGrip",
                "price": 149.99,
                "weight": 12.0,
                "status": "ACTIVE"
            },
            {
                "sku": "OIL-001",
                "name": "Synthetic Motor Oil 5W-30",
                "description": "Full synthetic motor oil 5 quart",
                "category": "Automotive",
                "brand": "EngineGuard",
                "price": 29.99,
                "weight": 2.3,
                "status": "ACTIVE"
            },
            
            # Baby & Kids - High frequency purchases
            {
                "sku": "DIAPER-001",
                "name": "Baby Diapers Size 3",
                "description": "Ultra-soft baby diapers pack of 84",
                "category": "Baby",
                "brand": "BabyCare",
                "price": 24.99,
                "weight": 1.8,
                "status": "ACTIVE"
            },
            {
                "sku": "TOY-001",
                "name": "Educational Building Blocks",
                "description": "STEM learning building blocks set",
                "category": "Toys",
                "brand": "LearnPlay",
                "price": 39.99,
                "weight": 1.5,
                "status": "ACTIVE"
            },
            
            # Health & Wellness - Growing category
            {
                "sku": "VITAMIN-001",
                "name": "Multivitamin Gummies",
                "description": "Daily multivitamin gummies for adults",
                "category": "Health",
                "brand": "WellnessPlus",
                "price": 19.99,
                "weight": 0.3,
                "status": "ACTIVE"
            },
            {
                "sku": "PROTEIN-001",
                "name": "Whey Protein Powder",
                "description": "Premium whey protein powder 2lb",
                "category": "Health",
                "brand": "FitNutrition",
                "price": 49.99,
                "weight": 1.0,
                "status": "ACTIVE"
            },
            
            # Pet Supplies - Loyal customer base
            {
                "sku": "DOGFOOD-001",
                "name": "Premium Dog Food 15lb",
                "description": "Natural premium dog food for all breeds",
                "category": "Pet",
                "brand": "PetNutrition",
                "price": 59.99,
                "weight": 6.8,
                "status": "ACTIVE"
            },
            {
                "sku": "CATTOY-001",
                "name": "Interactive Cat Toy",
                "description": "Motion-activated interactive cat toy",
                "category": "Pet",
                "brand": "PetPlay",
                "price": 24.99,
                "weight": 0.4,
                "status": "ACTIVE"
            }
        ]
        
        return products
    
    def get_inventory_levels(self, sku: str, traffic_scenario: str = "normal") -> List[Dict]:
        """Generate inventory levels based on traffic scenarios"""
        
        # Define stock levels based on product popularity and traffic scenarios
        high_demand_skus = ["LAPTOP-001", "PHONE-001", "TABLET-001", "WATCH-001", "HEADPHONES-001", "SKINCARE-001", "DIAPER-001", "VITAMIN-001"]
        medium_demand_skus = ["LAPTOP-002", "PHONE-002", "CAMERA-001", "MONITOR-001", "CHAIR-001", "BLENDER-001", "MAKEUP-001", "PROTEIN-001", "DOGFOOD-001"]
        low_demand_skus = ["BOOK-001", "BOOK-002", "SHIRT-001", "JEANS-001", "YOGA-001", "TIRE-001", "OIL-001", "TOY-001", "CATTOY-001", "COOKWARE-001"]
        
        # Warehouse locations
        warehouses = ["US-EAST", "US-WEST", "US-CENTRAL"]
        
        inventory_items = []
        
        for warehouse in warehouses:
            if sku in high_demand_skus:
                # High demand products - more stock
                if traffic_scenario == "stress_test":
                    base_stock = random.randint(800, 1200)
                elif traffic_scenario == "peak_traffic":
                    base_stock = random.randint(500, 800)
                else:
                    base_stock = random.randint(300, 500)
                    
            elif sku in medium_demand_skus:
                # Medium demand products
                if traffic_scenario == "stress_test":
                    base_stock = random.randint(400, 600)
                elif traffic_scenario == "peak_traffic":
                    base_stock = random.randint(200, 400)
                else:
                    base_stock = random.randint(100, 200)
                    
            else:
                # Low demand products
                if traffic_scenario == "stress_test":
                    base_stock = random.randint(200, 300)
                elif traffic_scenario == "peak_traffic":
                    base_stock = random.randint(100, 200)
                else:
                    base_stock = random.randint(50, 100)
            
            # Distribute stock across warehouses (US-EAST gets more)
            if warehouse == "US-EAST":
                stock_multiplier = 1.5
            elif warehouse == "US-WEST":
                stock_multiplier = 1.2
            else:  # US-CENTRAL
                stock_multiplier = 1.0
                
            total_quantity = int(base_stock * stock_multiplier)
            available_quantity = total_quantity - random.randint(0, int(total_quantity * 0.1))  # 0-10% reserved
            reserved_quantity = total_quantity - available_quantity
            
            inventory_item = {
                "productSku": sku,
                "warehouseLocation": warehouse,
                "totalQuantity": total_quantity,
                "availableQuantity": available_quantity,
                "reservedQuantity": reserved_quantity,
                "minimumStockLevel": int(total_quantity * 0.1),  # 10% of total as minimum
                "maximumStockLevel": int(total_quantity * 1.5),  # 150% of current as maximum
                "reorderPoint": int(total_quantity * 0.2)  # 20% as reorder point
            }
            
            inventory_items.append(inventory_item)
        
        return inventory_items
    
    def setup_inventory_for_scenario(self, scenario: str = "normal_traffic"):
        """Setup complete inventory for a specific load testing scenario"""
        logger.info(f"Setting up inventory data for scenario: {scenario}")
        
        if not self.authenticate_admin():
            logger.error("Failed to authenticate admin user")
            return False
        
        products = self.get_product_catalog()
        
        # Create products first
        logger.info("Creating products...")
        created_products = 0
        for product in products:
            if self.create_product(product):
                created_products += 1
            time.sleep(0.5)  # Rate limiting
        
        logger.info(f"Created {created_products}/{len(products)} products")
        
        # Create inventory items
        logger.info("Creating inventory items...")
        created_inventory = 0
        total_inventory = 0
        
        for product in products:
            inventory_items = self.get_inventory_levels(product['sku'], scenario)
            total_inventory += len(inventory_items)
            
            for inventory_item in inventory_items:
                if self.create_inventory_item(inventory_item):
                    created_inventory += 1
                time.sleep(0.3)  # Rate limiting
        
        logger.info(f"Created {created_inventory}/{total_inventory} inventory items")
        
        # Summary
        logger.info("="*60)
        logger.info("INVENTORY SETUP SUMMARY")
        logger.info("="*60)
        logger.info(f"Scenario: {scenario}")
        logger.info(f"Products Created: {created_products}")
        logger.info(f"Inventory Items Created: {created_inventory}")
        logger.info(f"Warehouses: US-EAST, US-WEST, US-CENTRAL")
        logger.info(f"Product Categories: Electronics, Furniture, Home, Books, Clothing, Sports")
        logger.info("="*60)
        
        return created_products > 0 and created_inventory > 0
    
    def verify_inventory_setup(self):
        """Verify inventory setup by checking some products"""
        logger.info("Verifying inventory setup...")
        
        test_skus = ["LAPTOP-001", "PHONE-001", "TABLET-001"]
        
        for sku in test_skus:
            try:
                response = self.session.get(
                    f"{self.base_url}/inventory-service/api/inventory/product/{sku}",
                    timeout=30
                )
                
                if response.status_code == 200:
                    logger.info(f"✅ Product {sku} inventory verified")
                else:
                    logger.warning(f"❌ Product {sku} verification failed: {response.status_code}")
                    
            except Exception as e:
                logger.error(f"❌ Error verifying {sku}: {e}")
        
        # Check total inventory count
        try:
            response = self.session.get(
                f"{self.base_url}/inventory-service/api/inventory",
                timeout=30
            )
            
            if response.status_code == 200:
                inventory_data = response.json()
                if isinstance(inventory_data, list):
                    logger.info(f"✅ Total inventory items accessible: {len(inventory_data)}")
                else:
                    logger.info("✅ Inventory endpoint accessible")
            else:
                logger.warning(f"❌ Inventory list check failed: {response.status_code}")
                
        except Exception as e:
            logger.error(f"❌ Error checking inventory list: {e}")

def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='Setup inventory data for load testing')
    parser.add_argument('--scenario', default='normal_traffic', 
                       choices=['normal_traffic', 'peak_traffic', 'light_traffic', 'stress_test'],
                       help='Load testing scenario to setup inventory for')
    parser.add_argument('--base-url', default='http://localhost:8080/proxy',
                       help='Base URL of the AI Load Balancer')
    parser.add_argument('--verify', action='store_true',
                       help='Verify inventory setup after creation')
    
    args = parser.parse_args()
    
    setup = InventoryDataSetup(args.base_url)
    
    logger.info("Starting inventory data setup...")
    success = setup.setup_inventory_for_scenario(args.scenario)
    
    if success:
        logger.info("✅ Inventory setup completed successfully!")
        
        if args.verify:
            setup.verify_inventory_setup()
    else:
        logger.error("❌ Inventory setup failed!")
        return 1
    
    return 0

if __name__ == "__main__":
    exit(main())
