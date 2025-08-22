#!/usr/bin/env python3
"""
Enhanced RL Training Load Test Script
Generates comprehensive, realistic traffic patterns for AI Load Balancer RL training
Designed to create diverse, high-quality offline training data
All API endpoints and DTOs validated against service controllers
"""

import json
import logging
import random
import threading
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
import requests
import uuid
import argparse
from queue import Queue

# Configure logging with detailed formatting
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/enhanced_rl_training_load_test.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

@dataclass
class TrafficPattern:
    name: str
    start_minute: int
    duration_minutes: int
    intensity: str
    concurrent_users: int
    user_behavior_distribution: Dict[str, float]
    scenario_weights: Dict[str, int]
    service_load_characteristics: Dict[str, str]

@dataclass
class UserBehaviorProfile:
    name: str
    session_duration_range: List[int]
    think_time_range: List[float]
    action_probabilities: Dict[str, float]
    conversion_rate: float
    pages_per_session: List[int]
    cart_abandonment_rate: float
    average_cart_size: Optional[List[int]] = None

@dataclass
class RLTrainingMetrics:
    timestamp: str
    user_id: str
    session_id: str
    action_type: str
    service_called: str
    response_time: float
    status_code: int
    success: bool
    user_behavior_type: str
    traffic_pattern: str
    concurrent_users: int
    service_load_level: str

class EnhancedRLTrainingTester:
    def __init__(self, config_file: str):
        """Initialize the enhanced RL training tester with comprehensive configuration"""
        self.thread_local = threading.local()
        self.config = self._load_config(config_file)
        
        # Handle both comprehensive config structure and quick validation structure
        if 'rl_training_scenarios' in self.config:
            self.base_config = self.config['rl_training_scenarios'].get('base_config', {})
            self.products = self.config['rl_training_scenarios'].get('enhanced_test_products', 
                                           self.config.get('enhanced_test_products', []))
            self.addresses = self.config['rl_training_scenarios'].get('realistic_shipping_addresses',
                                            self.config.get('realistic_shipping_addresses', []))
        else:
            self.base_config = self.config.get('base_config', {})
            self.products = self.config.get('enhanced_test_products', [])
            self.addresses = self.config.get('realistic_shipping_addresses', [])
        
        self.user_profiles = self._parse_user_profiles()
        self.metrics_collector = []
        self.metrics_lock = threading.Lock()
        
        # RL training specific tracking
        self.current_traffic_pattern = None
        self.pattern_start_time = None
        
        # Background traffic control
        self.background_traffic_active = False
        self.background_traffic_thread = None
        self.background_stop_event = threading.Event()
        self.background_request_queue = Queue()
        
        logger.info("🚀 Enhanced RL Training Tester initialized with comprehensive configuration")
        
    def get_session(self) -> requests.Session:
        """Get thread-local session for safe concurrent requests"""
        if not hasattr(self.thread_local, 'session'):
            self.thread_local.session = requests.Session()
            # Configure session for optimal performance
            adapter = requests.adapters.HTTPAdapter(
                pool_connections=10,
                pool_maxsize=20,
                max_retries=2
            )
            self.thread_local.session.mount('http://', adapter)
            self.thread_local.session.mount('https://', adapter)
        return self.thread_local.session

    def _load_config(self, config_file: str) -> Dict:
        """Load and validate configuration file"""
        try:
            with open(config_file, 'r') as f:
                config = json.load(f)
            logger.info(f"✅ Configuration loaded from {config_file}")
            return config
        except Exception as e:
            logger.error(f"❌ Failed to load configuration: {e}")
            raise

    def _parse_user_profiles(self) -> Dict[str, UserBehaviorProfile]:
        """Parse user behavior profiles from configuration"""
        profiles = {}
        
        # Handle both comprehensive config structure and quick validation structure
        if 'user_behavior_profiles' in self.config:
            profile_data = self.config['user_behavior_profiles']
        elif 'rl_training_scenarios' in self.config and 'user_behavior_profiles' in self.config['rl_training_scenarios']:
            profile_data = self.config['rl_training_scenarios']['user_behavior_profiles']
        else:
            logger.error("❌ No user_behavior_profiles found in configuration")
            raise KeyError("user_behavior_profiles not found in configuration structure")
        
        for name, data in profile_data.items():
            profiles[name] = UserBehaviorProfile(
                name=name,
                session_duration_range=data.get('session_duration_minutes', data.get('session_duration_range', [5, 15])),
                think_time_range=data.get('think_time_range', [1.0, 3.0]),
                action_probabilities=data.get('action_probabilities', {
                    'browse': data.get('category_browse_probability', 0.5),
                    'search': data.get('search_probability', 0.3),
                    'cart': data.get('add_to_cart_probability', 0.2),
                    'order': data.get('checkout_probability', 0.1)
                }),
                conversion_rate=data['conversion_rate'],
                pages_per_session=data['pages_per_session'],
                cart_abandonment_rate=data['cart_abandonment_rate'],
                average_cart_size=data.get('average_cart_size', [1, 3])
            )
        
        # Add background user profile for cooldown periods
        profiles['background_user'] = UserBehaviorProfile(
            name='background_user',
            session_duration_range=[1, 3],  # Very short sessions
            think_time_range=[2.0, 5.0],    # Slower think time
            action_probabilities={
                'browse': 0.7,   # Mostly browsing
                'cart': 0.25,    # Some cart activity  
                'order': 0.05    # Rare orders
            },
            conversion_rate=0.05,           # Very low conversion
            pages_per_session=[1, 3],       # Few pages
            cart_abandonment_rate=0.9,      # High abandonment
            average_cart_size=[1, 2]        # Small cart size
        )
        
        logger.info(f"📊 Loaded {len(profiles)} user behavior profiles")
        return profiles

    def collect_rl_metrics(self, user_id: str, session_id: str, action_type: str, 
                          service_called: str, response_time: float, status_code: int, 
                          success: bool, user_behavior_type: str):
        """Collect detailed metrics for RL training"""
        metrics = RLTrainingMetrics(
            timestamp=datetime.now().isoformat(),
            user_id=user_id,
            session_id=session_id,
            action_type=action_type,
            service_called=service_called,
            response_time=response_time,
            status_code=status_code,
            success=success,
            user_behavior_type=user_behavior_type,
            traffic_pattern=self.current_traffic_pattern.name if self.current_traffic_pattern else "unknown",
            concurrent_users=self.current_traffic_pattern.concurrent_users if self.current_traffic_pattern else 0,
            service_load_level=self.current_traffic_pattern.service_load_characteristics.get(service_called, "unknown") if self.current_traffic_pattern else "unknown"
        )
        
        with self.metrics_lock:
            self.metrics_collector.append(metrics)

    def register_and_login_user(self, user_behavior_type: str) -> Tuple[Optional[str], str, str]:
        """Register and login user with enhanced error handling and metrics collection"""
        session = self.get_session()
        user_id = f"rl_user_{uuid.uuid4().hex[:8]}"
        session_id = f"session_{uuid.uuid4().hex[:8]}"
        email = f"{user_id}@rltraining.com"
        
        # Registration - Validated against UserRegistrationRequest DTO
        start_time = time.time()
        try:
            registration_data = {
                "firstName": f"RLUser",
                "lastName": f"{user_id[-4:]}",
                "email": email,
                "password": "RLTraining123!",
                "phoneNumber": f"+1555{random.randint(1000000, 9999999)}"
            }
            
            headers = {'Content-Type': 'application/json'}
            response = session.post(
                f"{self.base_config['base_url']}/user-service/api/users/register",
                json=registration_data,
                headers=headers,
                timeout=self.base_config.get('request_timeout', 8)
            )
            
            response_time = time.time() - start_time
            success = response.status_code in [200, 201]
            
            self.collect_rl_metrics(user_id, session_id, "registration", "user-service", 
                                  response_time, response.status_code, success, user_behavior_type)
            
            if not success:
                logger.warning(f"⚠️ Registration failed for {user_id}: {response.status_code}")
                if response.status_code != 409:  # Not a conflict (user exists)
                    return None, user_id, session_id
            
        except Exception as e:
            response_time = time.time() - start_time
            self.collect_rl_metrics(user_id, session_id, "registration", "user-service", 
                                  response_time, 0, False, user_behavior_type)
            logger.error(f"❌ Registration exception for {user_id}: {e}")
            return None, user_id, session_id

        # Login - Validated against LoginRequest DTO
        start_time = time.time()
        try:
            login_data = {"email": email, "password": "RLTraining123!"}
            response = session.post(
                f"{self.base_config['base_url']}/user-service/api/users/login",
                json=login_data,
                headers=headers,
                timeout=self.base_config.get('request_timeout', 8)
            )
            
            response_time = time.time() - start_time
            success = response.status_code == 200
            
            self.collect_rl_metrics(user_id, session_id, "login", "user-service", 
                                  response_time, response.status_code, success, user_behavior_type)
            
            if success:
                token = response.json().get('token')
                logger.info(f"✅ User {user_id} authenticated successfully")
                return token, user_id, session_id
            else:
                logger.warning(f"⚠️ Login failed for {user_id}: {response.status_code}")
                
        except Exception as e:
            response_time = time.time() - start_time
            self.collect_rl_metrics(user_id, session_id, "login", "user-service", 
                                  response_time, 0, False, user_behavior_type)
            logger.error(f"❌ Login exception for {user_id}: {e}")
            
        return None, user_id, session_id

    def browse_inventory(self, token: str, user_id: str, session_id: str, user_behavior_type: str) -> bool:
        """Browse inventory with detailed RL metrics collection - Validated against InventoryController"""
        session = self.get_session()
        product = random.choice(self.products)
        start_time = time.time()
        
        try:
            headers = {'Authorization': f'Bearer {token}'}
            response = session.get(
                f"{self.base_config['base_url']}/inventory-service/api/inventory/product/{product.get('sku', product.get('id'))}",
                headers=headers,
                timeout=self.base_config.get('request_timeout', 8)
            )
            
            response_time = time.time() - start_time
            success = response.status_code == 200
            
            self.collect_rl_metrics(user_id, session_id, "browse", "inventory-service", 
                                  response_time, response.status_code, success, user_behavior_type)
            
            if success:
                logger.info(f"🔍 User {user_id} browsed inventory for {product.get('sku', product.get('id'))} successfully")
                return True
            else:
                logger.warning(f"⚠️ Browse failed for {user_id}: {response.status_code}")
                
        except Exception as e:
            response_time = time.time() - start_time
            self.collect_rl_metrics(user_id, session_id, "browse", "inventory-service", 
                                  response_time, 0, False, user_behavior_type)
            logger.error(f"❌ Browse exception for {user_id}: {e}", exc_info=True)
            
        return False

    def add_to_cart(self, token: str, user_id: str, session_id: str, user_behavior_type: str) -> bool:
        """Add items to cart - Validated against AddToCartRequest DTO"""
        session = self.get_session()
        product = random.choice(self.products)
        quantity = random.randint(1, 3)
        
        start_time = time.time()
        try:
            # Payload structure validated against AddToCartRequest DTO
            cart_data = {
                "productId": product.get('sku', product.get('id')),
                "productName": product['name'],
                "productImage": product['image'],
                "price": product['price'],
                "quantity": quantity
            }
            
            headers = {
                'Authorization': f'Bearer {token}',
                'Content-Type': 'application/json'
            }
            
            response = session.post(
                f"{self.base_config['base_url']}/cart-service/api/cart/items",
                json=cart_data,
                headers=headers,
                timeout=self.base_config.get('request_timeout', 8)
            )
            
            response_time = time.time() - start_time
            success = response.status_code in [200, 201]
            
            self.collect_rl_metrics(user_id, session_id, "add_to_cart", "cart-service", 
                                  response_time, response.status_code, success, user_behavior_type)
            
            if success:
                logger.info(f"🛒 User {user_id} added {product['name']} to cart")
                return True
            else:
                logger.warning(f"⚠️ Add to cart failed for {user_id}: {response.status_code}")
                
        except Exception as e:
            response_time = time.time() - start_time
            self.collect_rl_metrics(user_id, session_id, "add_to_cart", "cart-service", 
                                  response_time, 0, False, user_behavior_type)
            logger.error(f"❌ Add to cart exception for {user_id}: {e}", exc_info=True)
            
        return False

    def create_order(self, token: str, user_id: str, session_id: str, user_behavior_type: str) -> bool:
        """Create order from cart - Following proper e-commerce workflow"""
        session = self.get_session()
        address = random.choice(self.addresses)
        
        # Step 1: Ensure cart has items by adding some products first
        products_to_add = random.sample(self.products, random.randint(1, 3))
        cart_items_added = 0
        
        for product in products_to_add:
            if self.add_to_cart(token, user_id, session_id, user_behavior_type):
                cart_items_added += 1
        
        if cart_items_added == 0:
            logger.warning(f"⚠️ Could not add any items to cart for user {user_id}, skipping order creation")
            return False
        
        # Step 2: Create order from cart (order service will fetch cart contents)
        start_time = time.time()
        try:
            # Order service expects minimal data and fetches cart contents internally
            order_data = {
                "items": [],
                "shippingAddress": address,  # Includes all required fields: street, city, state, zipCode, country, phone
                "notes": f"RL Training Order - {user_behavior_type}"
            }
            
            headers = {
                'Authorization': f'Bearer {token}',
                'Content-Type': 'application/json'
            }
            
            response = session.post(
                f"{self.base_config['base_url']}/order-service/api/orders",
                json=order_data,
                headers=headers,
                timeout=self.base_config.get('request_timeout', 8)
            )
            
            response_time = time.time() - start_time
            success = response.status_code in [200, 201]
            
            self.collect_rl_metrics(user_id, session_id, "create_order", "order-service", 
                                  response_time, response.status_code, success, user_behavior_type)
            
            if success:
                logger.info(f"📦 User {user_id} created order successfully from cart with {cart_items_added} items")
                return True
            else:
                logger.warning(f"⚠️ Order creation failed for {user_id}: {response.status_code}")
                if response.status_code == 400:
                    logger.debug(f"Order creation error details: {response.text}")
                
        except Exception as e:
            response_time = time.time() - start_time
            self.collect_rl_metrics(user_id, session_id, "create_order", "order-service", 
                                  response_time, 0, False, user_behavior_type)
            logger.error(f"❌ Order creation exception for {user_id}: {e}")
            
        return False

    def background_traffic_worker(self):
        """Background worker that generates minimal traffic during cooldown periods"""
        logger.info("🔄 Background traffic worker started")
        
        # Background user profile for minimal activity
        background_profile = {
            'session_duration_range': [30, 120],  # 30s to 2min sessions
            'think_time_range': [2.0, 5.0],       # Slower think time
            'action_probabilities': {
                'browse': 0.7,   # Mostly browsing
                'cart': 0.25,    # Some cart activity
                'order': 0.05    # Rare orders
            },
            'cart_abandonment_rate': 0.9  # High abandonment for background
        }
        
        session_counter = 0
        
        while not self.background_stop_event.is_set():
            try:
                # Generate background session every 60 seconds (1 req/sec average)
                session_interval = 60
                
                if self.background_stop_event.wait(session_interval):
                    break  # Stop event was set
                
                if not self.background_traffic_active:
                    continue
                
                session_counter += 1
                user_id = f"bg_user_{session_counter:04d}"
                
                # Quick background session
                self._execute_background_session(user_id, background_profile)
                
            except Exception as e:
                logger.error(f"❌ Background traffic error: {e}")
                time.sleep(5)  # Brief pause on error
        
        logger.info("🔄 Background traffic worker stopped")
    
    def _execute_background_session(self, user_id: str, profile: Dict):
        """Execute a minimal background session"""
        try:
            # Simplified authentication for background traffic
            session = self.get_session()
            session_id = f"bg_session_{uuid.uuid4().hex[:6]}"
            
            # Quick registration/login (reuse existing method but simplified)
            token, actual_user_id, session_id = self.register_and_login_user("background_user")
            if not token:
                return
            
            # Perform 1-3 quick actions
            num_actions = random.randint(1, 3)
            
            for _ in range(num_actions):
                if self.background_stop_event.is_set():
                    break
                
                # Choose action based on background profile
                action = random.choices(
                    list(profile['action_probabilities'].keys()),
                    weights=list(profile['action_probabilities'].values())
                )[0]
                
                # Execute action with minimal logging
                if action == "browse":
                    self.browse_inventory(token, actual_user_id, session_id, "background_user")
                elif action == "cart":
                    self.add_to_cart(token, actual_user_id, session_id, "background_user")
                elif action == "order":
                    if random.random() > profile['cart_abandonment_rate']:
                        self.create_order(token, actual_user_id, session_id, "background_user")
                
                # Quick think time for background
                think_time = random.uniform(*profile['think_time_range'])
                if self.background_stop_event.wait(think_time):
                    break
                
        except Exception as e:
            # Silently handle background errors to avoid log spam
            pass
    
    def start_background_traffic(self):
        """Start background traffic generation"""
        if self.background_traffic_thread and self.background_traffic_thread.is_alive():
            return
        
        self.background_stop_event.clear()
        self.background_traffic_active = True
        self.background_traffic_thread = threading.Thread(
            target=self.background_traffic_worker,
            daemon=True,
            name="BackgroundTraffic"
        )
        self.background_traffic_thread.start()
        logger.info("🌊 Background traffic started (1 req/sec)")
    
    def stop_background_traffic(self):
        """Stop background traffic generation"""
        self.background_traffic_active = False
        self.background_stop_event.set()
        
        if self.background_traffic_thread and self.background_traffic_thread.is_alive():
            self.background_traffic_thread.join(timeout=10)
        
        logger.info("🌊 Background traffic stopped")
    
    def pause_background_traffic(self):
        """Temporarily pause background traffic during active patterns"""
        self.background_traffic_active = False
        logger.debug("⏸️ Background traffic paused for active pattern")
    
    def resume_background_traffic(self):
        """Resume background traffic during cooldown periods"""
        if self.background_traffic_thread and self.background_traffic_thread.is_alive():
            self.background_traffic_active = True
            logger.debug("▶️ Background traffic resumed for cooldown period")

    def simulate_user_session(self, user_behavior_type: str, traffic_pattern: TrafficPattern) -> Dict:
        """Simulate a complete user session based on behavior profile"""
        self.current_traffic_pattern = traffic_pattern
        profile = self.user_profiles[user_behavior_type]
        
        # Authenticate user
        token, user_id, session_id = self.register_and_login_user(user_behavior_type)
        if not token:
            return {"user_id": user_id, "success": False, "reason": "authentication_failed"}
        
        # Determine session characteristics
        session_duration = random.uniform(*profile.session_duration_range)
        pages_to_visit = random.randint(*profile.pages_per_session)
        
        session_start = time.time()
        actions_performed = []
        
        try:
            for page in range(pages_to_visit):
                if time.time() - session_start > session_duration * 60:
                    break
                
                # Determine action based on profile probabilities
                action = random.choices(
                    list(profile.action_probabilities.keys()),
                    weights=list(profile.action_probabilities.values())
                )[0]
                
                # Perform action
                if action == "browse":
                    success = self.browse_inventory(token, user_id, session_id, user_behavior_type)
                elif action == "cart":
                    success = self.add_to_cart(token, user_id, session_id, user_behavior_type)
                elif action == "order":
                    # Check for cart abandonment
                    if random.random() > profile.cart_abandonment_rate:
                        success = self.create_order(token, user_id, session_id, user_behavior_type)
                    else:
                        success = False
                        logger.info(f"🛒 User {user_id} abandoned cart (simulated)")
                else:
                    success = self.browse_inventory(token, user_id, session_id, user_behavior_type)
                
                actions_performed.append({"action": action, "success": success})
                
                # Think time between actions
                think_time = random.uniform(*profile.think_time_range)
                time.sleep(think_time)
                
        except Exception as e:
            logger.error(f"❌ Session exception for {user_id}: {e}")
            
        session_duration_actual = time.time() - session_start
        success_rate = sum(1 for a in actions_performed if a["success"]) / len(actions_performed) if actions_performed else 0
        
        return {
            "user_id": user_id,
            "session_id": session_id,
            "user_behavior_type": user_behavior_type,
            "traffic_pattern": traffic_pattern.name,
            "success": True,
            "session_duration": session_duration_actual,
            "actions_performed": len(actions_performed),
            "success_rate": success_rate,
            "actions": actions_performed
        }

    def execute_traffic_pattern(self, pattern: TrafficPattern) -> List[Dict]:
        """Execute a specific traffic pattern with concurrent users"""
        logger.info(f"🚀 Executing traffic pattern: {pattern.name}")
        logger.info(f"   Duration: {pattern.duration_minutes} minutes")
        logger.info(f"   Concurrent Users: {pattern.concurrent_users}")
        logger.info(f"   Intensity: {pattern.intensity}")
        
        # Pause background traffic during active patterns
        self.pause_background_traffic()
        
        self.current_traffic_pattern = pattern
        self.pattern_start_time = time.time()
        
        # Reduce concurrent users for stability - prevent system overload
        max_concurrent = min(pattern.concurrent_users, 20)  # Limit to 20 concurrent users
        logger.info(f"🔧 Adjusted concurrent users to {max_concurrent} for system stability")
        
        # Generate user behaviors based on distribution
        user_behaviors = []
        for behavior_type, probability in pattern.user_behavior_distribution.items():
            count = int(max_concurrent * probability)
            user_behaviors.extend([behavior_type] * count)
        
        # Ensure we have exactly the right number of users
        while len(user_behaviors) < max_concurrent:
            user_behaviors.append(random.choice(list(pattern.user_behavior_distribution.keys())))
        user_behaviors = user_behaviors[:max_concurrent]
        
        random.shuffle(user_behaviors)
        
        logger.info(f"📊 User behavior distribution: {dict(Counter(user_behaviors))}")
        
        # Execute concurrent user sessions with reduced load
        results = []
        with ThreadPoolExecutor(max_workers=min(max_concurrent, 10)) as executor:  # Limit thread pool
            future_to_behavior = {
                executor.submit(self.simulate_user_session, behavior, pattern): behavior 
                for behavior in user_behaviors
            }
            
            for future in as_completed(future_to_behavior):
                try:
                    result = future.result(timeout=120)  # Reduced timeout to 2 minutes
                    results.append(result)
                    
                    # Progress logging
                    if len(results) % 5 == 0:  # More frequent progress updates
                        logger.info(f"📈 Completed {len(results)}/{len(user_behaviors)} user sessions")
                        
                except Exception as e:
                    behavior = future_to_behavior[future]
                    logger.error(f"❌ User session failed for {behavior}: {e}")
                    results.append({"user_behavior_type": behavior, "success": False, "error": str(e)})
        
        pattern_duration = time.time() - self.pattern_start_time
        success_count = sum(1 for r in results if r.get("success", False))
        
        logger.info(f"✅ Traffic pattern {pattern.name} completed:")
        logger.info(f"   Duration: {pattern_duration:.1f}s")
        logger.info(f"   Success Rate: {success_count}/{len(results)} ({success_count/len(results)*100:.1f}%)")
        
        # Resume background traffic after pattern completion
        self.resume_background_traffic()
        
        return results

    def run_comprehensive_rl_training(self, scenario_name: str, skip_waits: bool = False):
        """Run comprehensive RL training with multiple traffic patterns"""
        logger.info("🚀 Starting Comprehensive RL Training Load Test")
        if skip_waits:
            logger.info("⚡ Fast mode enabled - skipping waits between patterns")
        else:
            logger.info("🌊 Background traffic enabled during cooldown periods")
        
        # Start background traffic system
        if not skip_waits:
            self.start_background_traffic()
        
        # Get scenario config - handle both comprehensive and stress test scenarios
        scenario_config = None
        
        # Try rl_training_scenarios first (comprehensive config)
        if 'rl_training_scenarios' in self.config:
            if scenario_name in self.config['rl_training_scenarios']:
                scenario_config = self.config['rl_training_scenarios'][scenario_name]
            elif 'training_scenarios' in self.config['rl_training_scenarios'] and scenario_name in self.config['rl_training_scenarios']['training_scenarios']:
                scenario_config = self.config['rl_training_scenarios']['training_scenarios'][scenario_name]
        
        # Try stress_test_scenarios (stress test config)
        if scenario_config is None and 'stress_test_scenarios' in self.config:
            if 'extreme_load_scenarios' in self.config['stress_test_scenarios'] and scenario_name in self.config['stress_test_scenarios']['extreme_load_scenarios']:
                scenario_config = self.config['stress_test_scenarios']['extreme_load_scenarios'][scenario_name]
        
        if scenario_config is None:
            raise KeyError(f"Scenario '{scenario_name}' not found in any configuration section")
        
        traffic_patterns = []
        
        # Parse traffic patterns
        for pattern_data in scenario_config['traffic_patterns']:
            pattern = TrafficPattern(
                name=pattern_data['name'],
                start_minute=pattern_data['start_minute'],
                duration_minutes=pattern_data['duration_minutes'],
                intensity=pattern_data['intensity'],
                concurrent_users=pattern_data['concurrent_users'],
                user_behavior_distribution=pattern_data['user_behavior_distribution'],
                scenario_weights=pattern_data['scenario_weights'],
                service_load_characteristics=pattern_data['service_load_characteristics']
            )
            traffic_patterns.append(pattern)
        
        # Execute patterns sequentially with timing
        all_results = []
        start_time = time.time()
        
        for i, pattern in enumerate(traffic_patterns):
            # Wait for pattern start time (unless skip_waits is enabled)
            if not skip_waits:
                elapsed_minutes = (time.time() - start_time) / 60
                if elapsed_minutes < pattern.start_minute:
                    wait_time = (pattern.start_minute - elapsed_minutes) * 60
                    logger.info(f"⏱️ Waiting {wait_time:.1f}s for pattern {pattern.name}")
                    logger.info(f"🌊 Background traffic active during cooldown (1 req/sec)")
                    time.sleep(wait_time)
            else:
                logger.info(f"⚡ Starting pattern {pattern.name} immediately (fast mode)")
            
            # Execute pattern
            pattern_results = self.execute_traffic_pattern(pattern)
            all_results.extend(pattern_results)
            
            # Add brief pause between patterns for background traffic
            if not skip_waits and i < len(traffic_patterns) - 1:
                inter_pattern_pause = 10  # 10 second pause between patterns
                logger.info(f"🌊 Brief cooldown with background traffic: {inter_pattern_pause}s")
                time.sleep(inter_pattern_pause)
        
        # Stop background traffic
        if not skip_waits:
            self.stop_background_traffic()
        
        # Save comprehensive results
        self.save_rl_training_results(scenario_name, all_results)
        
        total_duration = time.time() - start_time
        total_success = sum(1 for r in all_results if r.get("success", False))
        
        logger.info(f"🏆 Comprehensive RL training completed:")
        logger.info(f"   Total Duration: {total_duration/60:.1f} minutes")
        logger.info(f"   Total Sessions: {len(all_results)}")
        logger.info(f"   Overall Success Rate: {total_success}/{len(all_results)} ({total_success/len(all_results)*100:.1f}%)")
        logger.info(f"   RL Metrics Collected: {len(self.metrics_collector)}")
        if not skip_waits:
            logger.info(f"   Background Traffic: Maintained 1 req/sec during cooldowns")

    def save_rl_training_results(self, scenario_name: str, results: List[Dict]):
        """Save comprehensive RL training results and metrics"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        
        # Save session results
        results_file = f"logs/rl_training_results_{scenario_name}_{timestamp}.json"
        with open(results_file, 'w') as f:
            json.dump({
                "scenario": scenario_name,
                "timestamp": timestamp,
                "total_sessions": len(results),
                "successful_sessions": sum(1 for r in results if r.get("success", False)),
                "results": results
            }, f, indent=2)
        
        # Save RL metrics
        metrics_file = f"logs/rl_training_metrics_{scenario_name}_{timestamp}.json"
        with open(metrics_file, 'w') as f:
            json.dump({
                "scenario": scenario_name,
                "timestamp": timestamp,
                "total_metrics": len(self.metrics_collector),
                "metrics": [
                    {
                        "timestamp": m.timestamp,
                        "user_id": m.user_id,
                        "session_id": m.session_id,
                        "action_type": m.action_type,
                        "service_called": m.service_called,
                        "response_time": m.response_time,
                        "status_code": m.status_code,
                        "success": m.success,
                        "user_behavior_type": m.user_behavior_type,
                        "traffic_pattern": m.traffic_pattern,
                        "concurrent_users": m.concurrent_users,
                        "service_load_level": m.service_load_level
                    } for m in self.metrics_collector
                ]
            }, f, indent=2)
        
        logger.info(f"💾 Results saved to {results_file}")
        logger.info(f"📊 RL metrics saved to {metrics_file}")

def main():
    """Main execution function"""
    parser = argparse.ArgumentParser(description='Enhanced RL Training Load Test')
    parser.add_argument('--config', required=True, help='Configuration file path')
    parser.add_argument('--scenario', required=True, help='Scenario name to run')
    
    args = parser.parse_args()
    
    logger.info("🚀 Enhanced RL Training Load Test Starting")
    logger.info(f"📋 Config: {args.config}, Scenario: {args.scenario}")
    
    try:
        # Initialize tester with specified configuration
        tester = EnhancedRLTrainingTester(args.config)
        
        # Run specified RL training scenario
        tester.run_comprehensive_rl_training(args.scenario)
        
        logger.info("🎉 Enhanced RL Training Load Test Completed Successfully!")
        
    except Exception as e:
        logger.error(f"💥 Enhanced RL Training Load Test Failed: {e}")
        raise

if __name__ == "__main__":
    main()
