#!/usr/bin/env python3
"""
RL Training Load Test Script
Generates diverse, realistic traffic patterns specifically designed for RL Agent training
"""

import requests
import concurrent.futures
import time
import random
import json
import threading
import logging
from datetime import datetime, timedelta
from dataclasses import dataclass
from typing import List, Dict, Any, Optional
import uuid
import argparse
import sys
import os
import asyncio
from collections import defaultdict

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - [%(threadName)s] - %(message)s',
    handlers=[
        logging.FileHandler('rl_training_load_test.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

@dataclass
class TrafficPattern:
    """Represents a specific traffic pattern for RL training"""
    name: str
    start_minute: int
    duration_minutes: int
    intensity: str
    concurrent_users: int
    scenario_weights: Dict[str, int]

@dataclass
class UserBehaviorProfile:
    """Defines different user behavior patterns"""
    profile_type: str
    session_duration_range: tuple
    think_time_range: tuple
    action_probabilities: Dict[str, float]
    error_tolerance: float

class RLTrainingLoadTester:
    """Enhanced load tester for RL training data generation"""
    
    def __init__(self, config_file: str):
        self.config = self.load_config(config_file)
        self.base_url = self.config['base_config']['base_url']
        self.thread_local = threading.local()
        self.metrics = defaultdict(list)
        self.active_sessions = {}
        self.test_start_time = None
        
        # Error simulation configuration
        self.error_config = self.config.get('error_simulation_config', {})
        self.error_injection_rate = self.error_config.get('error_injection_probability', 0.15)
        
        # User behavior profiles for diverse training data
        self.user_profiles = {
            'quick_browser': UserBehaviorProfile(
                'quick_browser', (2, 5), (0.5, 1.5), 
                {'browse': 0.7, 'search': 0.2, 'cart': 0.08, 'order': 0.02}, 0.8
            ),
            'careful_shopper': UserBehaviorProfile(
                'careful_shopper', (8, 15), (2.0, 5.0),
                {'browse': 0.4, 'search': 0.3, 'cart': 0.2, 'order': 0.1}, 0.3
            ),
            'bulk_buyer': UserBehaviorProfile(
                'bulk_buyer', (10, 20), (1.0, 3.0),
                {'browse': 0.2, 'search': 0.1, 'cart': 0.4, 'order': 0.3}, 0.2
            ),
            'impulse_buyer': UserBehaviorProfile(
                'impulse_buyer', (3, 8), (0.5, 2.0),
                {'browse': 0.5, 'cart': 0.3, 'order': 0.2}, 0.6
            )
        }
        
    def get_session(self) -> requests.Session:
        """Get thread-local session to avoid concurrency issues"""
        if not hasattr(self.thread_local, 'session'):
            self.thread_local.session = requests.Session()
        return self.thread_local.session
        
    def load_config(self, config_file: str) -> Dict[str, Any]:
        """Load RL training configuration"""
        try:
            with open(config_file, 'r') as f:
                config = json.load(f)
            return config['rl_training_scenarios']
        except Exception as e:
            logger.error(f"Failed to load config: {e}")
            sys.exit(1)
    
    def get_user_profile(self) -> UserBehaviorProfile:
        """Randomly select a user behavior profile"""
        return random.choice(list(self.user_profiles.values()))
    
    def register_user(self) -> Optional[str]:
        """Register a new user and return JWT token with retry logic and error simulation"""
        max_retries = 3
        
        for attempt in range(max_retries):
            try:
                # Generate unique email for each attempt
                timestamp = int(time.time() * 1000)
                user_email = f"rltest_{uuid.uuid4().hex[:8]}_{timestamp}_{attempt}@example.com"
                
                # Simulate authentication errors
                if self.should_inject_error('authentication_errors'):
                    user_data = self.generate_malformed_user_data(user_email)
                else:
                    user_data = {
                        "firstName": random.choice(['John', 'Jane', 'Mike', 'Sarah', 'David', 'Lisa']),
                        "lastName": random.choice(['Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia']),
                        "email": user_email,
                        "password": "LoadTest123!",
                        "phoneNumber": f"+1{random.randint(2000000000, 9999999999)}"
                    }
                
                # Try registration (may fail if user exists, that's OK)
                try:
                    reg_response = self.get_session().post(
                        f"{self.base_url}/user-service/api/users/register",
                        json=user_data,
                        timeout=self.config['base_config']['request_timeout']
                    )
                except Exception:
                    pass  # Registration failure is OK, we'll try login
                
                # Always try login regardless of registration result
                # Simulate login errors
                if self.should_inject_error('authentication_errors'):
                    login_data = self.generate_malformed_login_data(user_email)
                else:
                    login_data = {
                        "email": user_email,
                        "password": "LoadTest123!"
                    }
                
                login_response = self.get_session().post(
                    f"{self.base_url}/user-service/api/users/login",
                    json=login_data,
                    timeout=self.config['base_config']['request_timeout']
                )
                
                if login_response.status_code == 200:
                    token = login_response.json().get('token')
                    if token:
                        # Simulate expired/invalid token scenarios
                        if self.should_inject_error('authentication_errors', 'expired_token_rate'):
                            return "EXPIRED_TOKEN_SIMULATION"
                        elif self.should_inject_error('authentication_errors', 'invalid_token_rate'):
                            return "INVALID_TOKEN_SIMULATION"
                        return token
                
                # If login failed, wait and retry
                if attempt < max_retries - 1:
                    time.sleep(random.uniform(0.5, 2.0))  # Random backoff
                    
            except Exception as e:
                logger.error(f"Auth attempt {attempt + 1} failed: {str(e)}")
                if attempt < max_retries - 1:
                    time.sleep(random.uniform(1.0, 3.0))
        
        logger.error(f"Failed to authenticate after {max_retries} attempts")
        return None
    
    def simulate_user_session(self, user_id: str, profile: UserBehaviorProfile, 
                            pattern: TrafficPattern) -> Dict[str, Any]:
        """Simulate a complete user session with realistic behavior"""
        session_metrics = {
            'user_id': user_id,
            'profile': profile.profile_type,
            'pattern': pattern.name,
            'start_time': datetime.now(),
            'actions': [],
            'total_requests': 0,
            'successful_requests': 0,
            'failed_requests': 0,
            'total_latency': 0.0,
            'errors': []
        }
        
        # Register user and get token
        token = self.register_user()
        if not token:
            session_metrics['errors'].append('Failed to register user')
            return session_metrics
        
        headers = {'Authorization': f'Bearer {token}'}
        session_duration = random.uniform(*profile.session_duration_range)
        session_end_time = datetime.now() + timedelta(minutes=session_duration)
        
        products = self.config['enhanced_test_products']
        cart_items = []
        
        while datetime.now() < session_end_time:
            # Select action based on profile probabilities
            action = self.select_action(profile.action_probabilities)
            action_start = time.time()
            
            try:
                if action == 'browse':
                    success, latency = self.browse_products(headers)
                elif action == 'search':
                    success, latency = self.search_products(headers, random.choice(products)['name'])
                elif action == 'cart':
                    product = random.choice(products)
                    success, latency = self.add_to_cart(headers, product, cart_items)
                elif action == 'order':
                    # Ensure server-side cart has items even if local list is empty
                    ensured = self.ensure_server_cart_has_items(headers)
                    if ensured:
                        success, latency = self.create_order(headers, cart_items)
                        if success:
                            cart_items.clear()
                    else:
                        # Could not ensure cart; skip order gracefully
                        success, latency = True, 0.0
                else:
                    success, latency = self.get_user_profile_info(headers)
                
                # Record action metrics
                session_metrics['actions'].append({
                    'action': action,
                    'timestamp': datetime.now(),
                    'success': success,
                    'latency': latency
                })
                
                session_metrics['total_requests'] += 1
                session_metrics['total_latency'] += latency
                
                if success:
                    session_metrics['successful_requests'] += 1
                else:
                    session_metrics['failed_requests'] += 1
                
            except Exception as e:
                session_metrics['errors'].append(f"{action}: {str(e)}")
                session_metrics['failed_requests'] += 1
            
            # Think time based on profile
            think_time = random.uniform(*profile.think_time_range)
            time.sleep(think_time)
        
        session_metrics['end_time'] = datetime.now()
        session_metrics['duration_minutes'] = (
            session_metrics['end_time'] - session_metrics['start_time']
        ).total_seconds() / 60
        
        return session_metrics
    
    def should_inject_error(self, error_category: str, specific_error: str = None) -> bool:
        """Determine if an error should be injected based on configuration"""
        if not self.error_config or random.random() > self.error_injection_rate:
            return False
        
        error_scenarios = self.error_config.get('error_scenarios', {})
        category_config = error_scenarios.get(error_category, {})
        
        if specific_error and specific_error in category_config:
            return random.random() < category_config[specific_error]
        
        # General category error injection
        return random.random() < 0.3  # 30% chance within error injection
    
    def get_error_headers(self, headers: Dict[str, str]) -> Dict[str, str]:
        """Get headers with potential authentication errors"""
        if self.should_inject_error('authentication_errors', 'missing_token_rate'):
            return {}  # Missing authorization header
        elif self.should_inject_error('authentication_errors', 'invalid_token_rate'):
            return {'Authorization': 'Bearer INVALID_TOKEN_SIMULATION'}
        elif self.should_inject_error('authentication_errors', 'malformed_credentials_rate'):
            return {'Authorization': 'InvalidFormat'}
        return headers
    
    def generate_malformed_user_data(self, email: str) -> Dict[str, Any]:
        """Generate malformed user registration data"""
        malformed_payloads = self.error_config.get('malformed_payloads', {}).get('user_registration', [])
        if malformed_payloads:
            base_payload = random.choice(malformed_payloads).copy()
            if 'email' not in base_payload or not base_payload['email']:
                base_payload['email'] = email
            return base_payload
        
        # Fallback malformed data
        return {
            "email": "invalid-email-format",
            "password": "",
            "firstName": "",
            "lastName": ""
        }
    
    def generate_malformed_login_data(self, email: str) -> Dict[str, Any]:
        """Generate malformed login data"""
        error_type = random.choice(['invalid_email', 'empty_password', 'wrong_credentials'])
        
        if error_type == 'invalid_email':
            return {"email": "not-an-email", "password": "LoadTest123!"}
        elif error_type == 'empty_password':
            return {"email": email, "password": ""}
        else:
            return {"email": email, "password": "WrongPassword123!"}
    
    def generate_malformed_cart_data(self, product_sku: str, product_name: str, 
                                   quantity: int, unit_price: float) -> Dict[str, Any]:
        """Generate malformed cart data"""
        malformed_payloads = self.error_config.get('malformed_payloads', {}).get('cart_items', [])
        if malformed_payloads:
            base_payload = random.choice(malformed_payloads).copy()
            # Ensure we have some valid data
            if not base_payload.get('productId'):
                base_payload['productId'] = product_sku
            return base_payload
        
        # Fallback malformed data
        return {
            "productId": "",
            "quantity": -1,
            "price": -100,
            "unitPrice": "invalid_price"
        }
    
    def generate_malformed_order_data(self, order_items: List[Dict], addresses: List[Dict]) -> Dict[str, Any]:
        """Generate malformed order data"""
        malformed_payloads = self.error_config.get('malformed_payloads', {}).get('orders', [])
        if malformed_payloads:
            base_payload = random.choice(malformed_payloads).copy()
            # Ensure we have some items if payload is completely empty
            if not base_payload.get('items') and order_items:
                base_payload['items'] = order_items[:1]  # At least one item
            return base_payload
        
        # Fallback malformed data
        error_type = random.choice(['empty_items', 'invalid_address', 'missing_fields'])
        
        if error_type == 'empty_items':
            return {
                "items": [],
                "shippingAddress": random.choice(addresses),
                "notes": "Empty cart order"
            }
        elif error_type == 'invalid_address':
            return {
                "items": order_items,
                "shippingAddress": {"street": "", "zipCode": "INVALID"},
                "notes": "Invalid address order"
            }
        else:
            return {
                "items": [{"productId": "INVALID"}],  # Missing required fields
                "notes": "Missing fields order"
            }
    
    def select_action(self, probabilities: Dict[str, float]) -> str:
        """Select action based on probability distribution"""
        actions = list(probabilities.keys())
        weights = list(probabilities.values())
        return random.choices(actions, weights=weights)[0]
    
    def browse_products(self, headers: Dict[str, str]) -> tuple:
        """Browse products endpoint"""
        start_time = time.time()
        try:
            response = self.get_session().get(
                f"{self.base_url}/inventory-service/api/inventory/all",
                headers=headers,
                timeout=self.config['base_config']['request_timeout']
            )
            latency = time.time() - start_time
            return response.status_code == 200, latency
        except Exception:
            return False, time.time() - start_time
    
    def search_products(self, headers: Dict[str, str], query: str) -> tuple:
        """Search products endpoint with error simulation"""
        start_time = time.time()
        try:
            # Simulate product search errors
            if self.should_inject_error('product_errors', 'invalid_product_sku_rate'):
                product_sku = random.choice(self.error_config.get('invalid_product_skus', ['INVALID-001']))
            elif self.should_inject_error('product_errors', 'invalid_category_search_rate'):
                # Search by invalid category
                invalid_category = random.choice(self.error_config.get('invalid_categories', ['InvalidCategory']))
                response = self.get_session().get(
                    f"{self.base_url}/inventory-service/api/inventory/category/{invalid_category}",
                    headers=self.get_error_headers(headers),
                    timeout=self.config['base_config']['request_timeout']
                )
                latency = time.time() - start_time
                return response.status_code == 200, latency
            else:
                product_sku = query.replace(' ', '-').upper() + '-001'
            
            response = self.get_session().get(
                f"{self.base_url}/inventory-service/api/inventory/products/{product_sku}",
                headers=self.get_error_headers(headers),
                timeout=self.config['base_config']['request_timeout']
            )
            latency = time.time() - start_time
            return response.status_code == 200, latency
        except Exception:
            return False, time.time() - start_time
    
    def add_to_cart(self, headers: Dict[str, str], product: Dict, cart_items: List) -> tuple:
        """Add item to cart with error simulation"""
        start_time = time.time()
        try:
            # Simulate cart errors
            if self.should_inject_error('cart_errors', 'invalid_product_id_rate'):
                product_sku = random.choice(self.error_config.get('invalid_product_skus', ['INVALID-001']))
            else:
                product_sku = product.get('sku', 'LAPTOP-001')
            
            # First check if product exists in inventory
            inventory_check = self.get_session().get(
                f"{self.base_url}/inventory-service/api/inventory/products/{product_sku}",
                headers=self.get_error_headers(headers),
                timeout=self.config['base_config']['request_timeout']
            )
            
            if inventory_check.status_code == 200:
                # Generate realistic product data
                product_names = {
                    "LAPTOP-001": "Dell XPS 13 Laptop",
                    "PHONE-001": "iPhone 15 Pro",
                    "TABLET-001": "iPad Air 5th Gen",
                    "WATCH-001": "Apple Watch Series 9"
                }
                
                product_name = product_names.get(product_sku, f"Product {product_sku}")
                unit_price = random.uniform(99.99, 1999.99)
                
                # Simulate cart quantity errors
                if self.should_inject_error('cart_errors', 'negative_quantity_rate'):
                    quantity = random.randint(-10, -1)
                elif self.should_inject_error('cart_errors', 'invalid_quantity_rate'):
                    quantity = random.choice([0, 999999, -1])
                else:
                    quantity = random.choices([1, 2, 3], [70, 25, 5])[0]
                
                # Generate cart data with potential errors
                if self.should_inject_error('cart_errors'):
                    cart_data = self.generate_malformed_cart_data(product_sku, product_name, quantity, unit_price)
                else:
                    cart_data = {
                        "productId": product_sku,
                        "productSku": product_sku,
                        "productName": product_name,
                        "quantity": quantity,
                        "price": unit_price,
                        "unitPrice": unit_price
                    }
                
                response = self.get_session().post(
                    f"{self.base_url}/cart-service/api/cart/items",
                    json=cart_data,
                    headers=self.get_error_headers(headers),
                    timeout=self.config['base_config']['request_timeout']
                )
                
                latency = time.time() - start_time
                success = response.status_code in [200, 201]
                if success and product_sku not in cart_items:
                    cart_items.append(product_sku)
                return success, latency
            
            return False, time.time() - start_time
        except Exception:
            return False, time.time() - start_time
    
    def get_server_cart(self, headers: Dict[str, str]) -> tuple:
        """Fetch the server-side cart to validate actual cart contents"""
        start_time = time.time()
        try:
            response = self.get_session().get(
                f"{self.base_url}/cart-service/api/cart",
                headers=headers,
                timeout=self.config['base_config']['request_timeout']
            )
            latency = time.time() - start_time
            if response.status_code == 200:
                return True, latency, response.json()
            return False, latency, None
        except Exception:
            return False, time.time() - start_time, None

    def ensure_server_cart_has_items(self, headers: Dict[str, str], fallback_sku: str = "LAPTOP-001") -> bool:
        """Ensure the server cart has at least one valid item. If empty, add a default item without error injection."""
        ok, _, cart = self.get_server_cart(headers)
        if ok and cart and cart.get('totalItems', 0) > 0:
            return True

        # Cart empty or fetch failed: try to add a safe, valid item deterministically (no error injection)
        try:
            # Verify product exists
            inv_resp = self.get_session().get(
                f"{self.base_url}/inventory-service/api/inventory/products/{fallback_sku}",
                headers=headers,
                timeout=self.config['base_config']['request_timeout']
            )
            if inv_resp.status_code != 200:
                return False

            product_names = {
                "LAPTOP-001": "Dell XPS 13 Laptop",
                "PHONE-001": "iPhone 15 Pro",
                "TABLET-001": "iPad Air 5th Gen",
                "WATCH-001": "Apple Watch Series 9"
            }
            unit_price = random.uniform(99.99, 1999.99)
            safe_cart_data = {
                "productId": fallback_sku,
                "productSku": fallback_sku,
                "productName": product_names.get(fallback_sku, f"Product {fallback_sku}"),
                "quantity": 1,
                "price": unit_price,
                "unitPrice": unit_price
            }
            add_resp = self.get_session().post(
                f"{self.base_url}/cart-service/api/cart/items",
                json=safe_cart_data,
                headers=headers,
                timeout=self.config['base_config']['request_timeout']
            )
            if add_resp.status_code not in [200, 201]:
                return False

            # Re-check cart
            ok2, _, cart2 = self.get_server_cart(headers)
            return bool(ok2 and cart2 and cart2.get('totalItems', 0) > 0)
        except Exception:
            return False

    def create_order(self, headers: Dict[str, str], cart_items: List) -> tuple:
        """Create order relying on server-side cart; send required shippingAddress and notes only"""
        start_time = time.time()
        try:
            # Realistic shipping addresses
            addresses = [
                {"street": "123 Main St", "city": "New York", "state": "NY", "zipCode": "10001", "country": "US"},
                {"street": "456 Oak Ave", "city": "Los Angeles", "state": "CA", "zipCode": "90210", "country": "US"},
                {"street": "789 Pine Rd", "city": "Chicago", "state": "IL", "zipCode": "60601", "country": "US"}
            ]
            
            # Simulate order errors; if not injecting, omit items to rely on server cart
            if self.should_inject_error('order_errors'):
                # Keep building minimal items list to satisfy malformed generator expectations
                minimal_items = [{"productId": cart_items[0]}] if cart_items else []
                order_data = self.generate_malformed_order_data(minimal_items, addresses)
            else:
                order_data = {
                    "shippingAddress": random.choice(addresses),
                    "notes": "RL Training Load Test Order",
                    "items": []
                }
            
            response = self.get_session().post(
                f"{self.base_url}/order-service/api/orders",
                json=order_data,
                headers=self.get_error_headers(headers),
                timeout=self.config['base_config']['request_timeout']
            )
            
            latency = time.time() - start_time
            return response.status_code in [200, 201], latency
        except Exception:
            return False, time.time() - start_time
    
    def get_user_profile_info(self, headers: Dict[str, str]) -> tuple:
        """Get user profile information"""
        start_time = time.time()
        try:
            response = self.get_session().get(
                f"{self.base_url}/user-service/api/users/profile",
                headers=headers,
                timeout=self.config['base_config']['request_timeout']
            )
            latency = time.time() - start_time
            return response.status_code == 200, latency
        except Exception:
            return False, time.time() - start_time
    
    def run_traffic_pattern(self, pattern: TrafficPattern) -> List[Dict[str, Any]]:
        """Execute a specific traffic pattern"""
        logger.info(f"Starting traffic pattern: {pattern.name}")
        logger.info(f"Duration: {pattern.duration_minutes} minutes, "
                   f"Concurrent users: {pattern.concurrent_users}")
        
        pattern_metrics = []
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=pattern.concurrent_users) as executor:
            futures = []
            
            # Submit user sessions
            for i in range(pattern.concurrent_users):
                profile = self.get_user_profile()
                user_id = f"{pattern.name}_user_{i}"
                
                future = executor.submit(
                    self.simulate_user_session, user_id, profile, pattern
                )
                futures.append(future)
                
                # Stagger user starts
                time.sleep(random.uniform(0.1, 1.0))
            
            # Collect results
            for future in concurrent.futures.as_completed(futures):
                try:
                    session_metrics = future.result()
                    pattern_metrics.append(session_metrics)
                except Exception as e:
                    logger.error(f"Session execution error: {e}")
        
        logger.info(f"Completed traffic pattern: {pattern.name}")
        return pattern_metrics
    
    def run_diverse_load_patterns(self) -> Dict[str, Any]:
        """Run the diverse load patterns scenario"""
        scenario_config = self.config['training_scenarios']['diverse_load_patterns']
        patterns = [TrafficPattern(**p) for p in scenario_config['traffic_patterns']]
        
        logger.info("Starting RL Training Load Test - Diverse Load Patterns")
        logger.info(f"Total duration: {scenario_config['test_duration_minutes']} minutes")
        
        self.test_start_time = datetime.now()
        all_metrics = []
        
        for pattern in patterns:
            # Wait for pattern start time
            elapsed_minutes = (datetime.now() - self.test_start_time).total_seconds() / 60
            if elapsed_minutes < pattern.start_minute:
                wait_time = (pattern.start_minute - elapsed_minutes) * 60
                logger.info(f"Waiting {wait_time:.1f} seconds for pattern {pattern.name}")
                time.sleep(wait_time)
            
            # Run the pattern
            pattern_metrics = self.run_traffic_pattern(pattern)
            all_metrics.extend(pattern_metrics)
        
        # Generate summary report
        return self.generate_summary_report(all_metrics)
    
    def generate_summary_report(self, all_metrics: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Generate comprehensive summary report"""
        total_sessions = len(all_metrics)
        total_requests = sum(m['total_requests'] for m in all_metrics)
        successful_requests = sum(m['successful_requests'] for m in all_metrics)
        failed_requests = sum(m['failed_requests'] for m in all_metrics)
        total_latency = sum(m['total_latency'] for m in all_metrics)
        
        success_rate = (successful_requests / total_requests * 100) if total_requests > 0 else 0
        avg_latency = (total_latency / successful_requests) if successful_requests > 0 else 0
        
        # Profile distribution
        profile_counts = defaultdict(int)
        for m in all_metrics:
            profile_counts[m['profile']] += 1
        
        # Pattern distribution  
        pattern_counts = defaultdict(int)
        for m in all_metrics:
            pattern_counts[m['pattern']] += 1
        
        report = {
            'test_summary': {
                'total_sessions': total_sessions,
                'total_requests': total_requests,
                'successful_requests': successful_requests,
                'failed_requests': failed_requests,
                'success_rate_percent': round(success_rate, 2),
                'average_latency_seconds': round(avg_latency, 3),
                'test_duration_minutes': (datetime.now() - self.test_start_time).total_seconds() / 60
            },
            'profile_distribution': dict(profile_counts),
            'pattern_distribution': dict(pattern_counts),
            'detailed_metrics': all_metrics
        }
        
        return report

def main():
    parser = argparse.ArgumentParser(description='RL Training Load Test')
    parser.add_argument('--config', default='rl_training_config.json',
                       help='Configuration file path')
    parser.add_argument('--scenario', default='diverse_load_patterns',
                       choices=['diverse_load_patterns', 'stress_testing', 'failure_simulation'],
                       help='Scenario to run')
    parser.add_argument('--output', default='rl_training_results.json',
                       help='Output file for results')
    
    args = parser.parse_args()
    
    # Initialize load tester
    load_tester = RLTrainingLoadTester(args.config)
    
    # Run selected scenario
    if args.scenario == 'diverse_load_patterns':
        results = load_tester.run_diverse_load_patterns()
    else:
        logger.error(f"Scenario {args.scenario} not implemented yet")
        sys.exit(1)
    
    # Save results
    with open(args.output, 'w') as f:
        json.dump(results, f, indent=2, default=str)
    
    # Print summary
    summary = results['test_summary']
    logger.info("="*80)
    logger.info("RL TRAINING LOAD TEST COMPLETED")
    logger.info("="*80)
    logger.info(f"Total Sessions: {summary['total_sessions']}")
    logger.info(f"Total Requests: {summary['total_requests']}")
    logger.info(f"Success Rate: {summary['success_rate_percent']}%")
    logger.info(f"Average Latency: {summary['average_latency_seconds']}s")
    logger.info(f"Test Duration: {summary['test_duration_minutes']:.1f} minutes")
    logger.info("="*80)

if __name__ == "__main__":
    main()
