#!/usr/bin/env python3
"""
Quick Benchmark Load Test - Simplified version for fast algorithm comparison
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

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class QuickBenchmarkLoadTester:
    """Simplified load tester for quick algorithm comparison"""
    
    def __init__(self, config_dict: Dict[str, Any]):
        self.config = config_dict['rl_training_scenarios']
        self.base_url = self.config['base_config']['base_url']
        self.thread_local = threading.local()
        self.products = config_dict.get('enhanced_test_products', [])
        
    def get_session(self) -> requests.Session:
        """Get thread-local session"""
        if not hasattr(self.thread_local, 'session'):
            self.thread_local.session = requests.Session()
        return self.thread_local.session
        
    def register_user(self) -> Optional[str]:
        """Register a user and return JWT token"""
        try:
            timestamp = int(time.time() * 1000)
            user_email = f"quicktest_{uuid.uuid4().hex[:8]}_{timestamp}@example.com"
            
            user_data = {
                "firstName": random.choice(['John', 'Jane', 'Mike', 'Sarah']),
                "lastName": random.choice(['Smith', 'Johnson', 'Williams', 'Brown']),
                "email": user_email,
                "password": "LoadTest123!",
                "phoneNumber": f"+1{random.randint(2000000000, 9999999999)}"
            }
            
            response = self.get_session().post(
                f"{self.base_url}/user-service/api/users/register",
                json=user_data,
                timeout=10
            )
            
            if response.status_code == 201:
                return response.json().get('token')
            return None
            
        except Exception as e:
            logger.debug(f"Registration failed: {e}")
            return None
    
    def make_request(self, endpoint: str, headers: dict = None) -> tuple:
        """Make a single request and return success, latency"""
        start_time = time.time()
        try:
            response = self.get_session().get(
                f"{self.base_url}{endpoint}",
                headers=headers or {},
                timeout=5
            )
            latency = time.time() - start_time
            return response.status_code in [200, 201], latency
        except Exception:
            return False, time.time() - start_time
    
    def simulate_quick_session(self, user_id: str, duration_seconds: int) -> Dict[str, Any]:
        """Simulate a quick user session with time limit"""
        session_metrics = {
            'user_id': user_id,
            'profile': 'quick_test',
            'pattern': 'quick_test',
            'total_requests': 0,
            'successful_requests': 0,
            'failed_requests': 0,
            'total_latency': 0.0,
            'errors': []
        }
        
        # Register user
        token = self.register_user()
        if not token:
            session_metrics['errors'].append('Failed to register user')
            return session_metrics
        
        headers = {'Authorization': f'Bearer {token}'}
        session_end_time = datetime.now() + timedelta(seconds=duration_seconds)
        
        # Quick endpoints for testing
        endpoints = [
            "/user-service/api/users/profile",
            "/inventory-service/api/products",
            "/cart-service/api/cart",
            "/user-service/actuator/health",
            "/inventory-service/actuator/health"
        ]
        
        while datetime.now() < session_end_time:
            endpoint = random.choice(endpoints)
            success, latency = self.make_request(endpoint, headers)
            
            session_metrics['total_requests'] += 1
            session_metrics['total_latency'] += latency
            
            if success:
                session_metrics['successful_requests'] += 1
            else:
                session_metrics['failed_requests'] += 1
            
            # Quick think time
            time.sleep(random.uniform(0.1, 0.5))
        
        return session_metrics
    
    def run_diverse_load_patterns(self) -> Dict[str, Any]:
        """Run quick load test with time-bounded sessions"""
        scenario_config = self.config['training_scenarios']['diverse_load_patterns']
        pattern = scenario_config['traffic_patterns'][0]  # Use first pattern
        
        logger.info("Starting Quick Benchmark Load Test")
        logger.info(f"Duration: {pattern['duration_minutes']} minutes, Users: {pattern['concurrent_users']}")
        
        test_start_time = datetime.now()
        all_metrics = []
        
        # Calculate session duration in seconds (slightly less than pattern duration)
        session_duration_seconds = (pattern['duration_minutes'] * 60) - 10  # 10 second buffer
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=pattern['concurrent_users']) as executor:
            futures = []
            
            # Submit user sessions with time limits
            for i in range(pattern['concurrent_users']):
                user_id = f"quick_user_{i}"
                future = executor.submit(
                    self.simulate_quick_session, user_id, session_duration_seconds
                )
                futures.append(future)
                time.sleep(random.uniform(0.05, 0.2))  # Quick stagger
            
            # Collect results with timeout
            for future in concurrent.futures.as_completed(futures, timeout=pattern['duration_minutes'] * 60 + 30):
                try:
                    session_metrics = future.result()
                    all_metrics.append(session_metrics)
                except Exception as e:
                    logger.error(f"Session execution error: {e}")
        
        # Generate summary
        return self.generate_summary_report(all_metrics, test_start_time)
    
    def generate_summary_report(self, all_metrics: List[Dict[str, Any]], test_start_time: datetime) -> Dict[str, Any]:
        """Generate summary report"""
        total_sessions = len(all_metrics)
        total_requests = sum(m['total_requests'] for m in all_metrics)
        successful_requests = sum(m['successful_requests'] for m in all_metrics)
        failed_requests = sum(m['failed_requests'] for m in all_metrics)
        total_latency = sum(m['total_latency'] for m in all_metrics)
        
        success_rate = (successful_requests / total_requests * 100) if total_requests > 0 else 0
        avg_latency = (total_latency / successful_requests) if successful_requests > 0 else 0
        
        report = {
            'test_summary': {
                'total_sessions': total_sessions,
                'total_requests': total_requests,
                'successful_requests': successful_requests,
                'failed_requests': failed_requests,
                'success_rate_percent': round(success_rate, 2),
                'average_latency_seconds': round(avg_latency, 3),
                'test_duration_minutes': (datetime.now() - test_start_time).total_seconds() / 60
            },
            'detailed_metrics': all_metrics
        }
        
        return report
