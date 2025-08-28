#!/usr/bin/env python3
"""
Automated Benchmarking Script for AI Load Balancer
Integrates with existing BenchmarkController to run comprehensive algorithm comparison tests
"""

import requests
import time
import json
import logging
import argparse
import sys
from datetime import datetime, timedelta
from typing import Dict, List, Any
import concurrent.futures
import threading
from dataclasses import dataclass

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(f'benchmark_test_{datetime.now().strftime("%Y%m%d_%H%M%S")}.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

@dataclass
class BenchmarkConfig:
    """Configuration for benchmark test"""
    load_balancer_url: str
    test_duration_minutes: int
    concurrent_users: int
    ramp_up_seconds: int
    test_scenarios: List[str]
    metrics_collection_interval: int = 30

class AutomatedBenchmark:
    """Orchestrates comprehensive benchmarking of load balancer algorithms"""
    
    def __init__(self, config: BenchmarkConfig):
        self.config = config
        self.benchmark_api = f"{config.load_balancer_url}/api/benchmark"
        self.metrics_history = []
        self.test_results = {}
        self.stop_traffic = False
        
    def run_complete_benchmark(self) -> bool:
        """Run the complete benchmarking process"""
        logger.info("🚀 Starting Automated Load Balancer Benchmark")
        
        try:
            # Step 1: Validate system readiness
            if not self._validate_system():
                return False
            
            # Step 2: Run fair load traffic generation (no benchmark controller needed)
            self._generate_load_traffic()
            
            # Step 3: Generate final report
            self._generate_final_report()
            
            return True
            
        except Exception as e:
            logger.error(f"Benchmark failed: {e}")
            return False
        finally:
            self._cleanup()
    
    def _validate_system(self) -> bool:
        """Validate that all systems are ready for benchmarking"""
        logger.info("🔍 Validating system readiness...")
        
        try:
            # Check load balancer health using actuator endpoint
            logger.info(f"Testing health endpoint: {self.config.load_balancer_url}/actuator/health")
            response = requests.get(f"{self.config.load_balancer_url}/actuator/health", timeout=10)
            logger.info(f"Health check response: {response.status_code}")
            if response.status_code != 200:
                logger.error(f"Load balancer health check failed with status: {response.status_code}")
                logger.error(f"Response text: {response.text}")
                return False
                
            # Check benchmark controller availability
            logger.info(f"Testing benchmark API: {self.benchmark_api}/status")
            response = requests.get(f"{self.benchmark_api}/status", timeout=10)
            logger.info(f"Benchmark API response: {response.status_code}")
            if response.status_code != 200:
                logger.error(f"Benchmark controller not available. Status: {response.status_code}")
                logger.error(f"Response text: {response.text}")
                return False
                
            # Check if benchmark is already running
            status = response.json()
            logger.info(f"Benchmark status: {status}")
            if status.get('status') == 'active':
                logger.error("Benchmark already running. Stop it first.")
                return False
                
            logger.info("✅ System validation passed")
            return True
            
        except requests.exceptions.ConnectionError as e:
            logger.error(f"Connection failed: {e}")
            logger.error("Make sure port-forward is running: kubectl port-forward -n ai-loadbalancer ai-loadbalancer-57548df54-hkl8g 8080:8080")
            return False
        except requests.exceptions.Timeout as e:
            logger.error(f"Request timeout: {e}")
            return False
        except Exception as e:
            logger.error(f"System validation failed: {e}")
            return False
    
    def _start_benchmark_controller(self) -> bool:
        """Start the benchmark controller"""
        logger.info("🎯 Starting benchmark controller...")
        
        try:
            response = requests.post(
                f"{self.benchmark_api}/start",
                params={'durationMinutes': self.config.test_duration_minutes},
                timeout=10
            )
            
            if response.status_code != 200:
                logger.error(f"Failed to start benchmark: {response.text}")
                return False
                
            result = response.json()
            logger.info(f"✅ Benchmark started: {result}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to start benchmark controller: {e}")
            return False
    
    def _generate_load_traffic(self):
        """Generate fair load traffic for each algorithm using diversified scenarios"""
        logger.info(f"🚛 Starting fair load traffic generation for algorithm comparison")
        
        # Import the existing load test functionality
        sys.path.append('/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/load-testing')
        
        try:
            from rl_training_load_test import RLTrainingLoadTester
            
            # Get available algorithms from benchmark controller
            algorithms = self._get_test_algorithms()
            logger.info(f"Running diversified scenarios for algorithms: {algorithms}")
            
            all_results = {}
            
            for algorithm in algorithms:
                logger.info(f"🔄 Starting diversified load test for algorithm: {algorithm}")
                
                # Switch to specific algorithm and verify
                if not self._switch_to_algorithm(algorithm):
                    logger.error(f"❌ Failed to switch to {algorithm}, skipping this algorithm")
                    all_results[algorithm] = self._get_empty_algorithm_metrics(algorithm)
                    continue
                
                # Additional wait after verified switch
                time.sleep(5)  # Allow algorithm to fully initialize
                
                try:
                    # Enable benchmark mode in controller for this algorithm
                    self._enable_benchmark_mode()
                    
                    # Reset metrics for this algorithm test
                    self._reset_algorithm_metrics(algorithm)
                    
                    # Create identical config for each algorithm with shorter duration
                    temp_config = self._create_diversified_config()
                    
                    # Save temporary config
                    import tempfile
                    with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
                        json.dump(temp_config, f, indent=2)
                        temp_config_path = f.name
                    
                    logger.info(f"Starting load test for {algorithm} with {temp_config['rl_training_scenarios']['training_scenarios']['diverse_load_patterns']['test_duration_minutes']} minutes duration")
                    
                    # Run diversified load test for this algorithm
                    logger.info(f"🚀 Starting load test for {algorithm}")
                    
                    # Run RL training load test with diverse scenarios
                    load_tester = RLTrainingLoadTester(config_file=temp_config_path)
                    load_test_results = load_tester.run_diverse_load_patterns()
                    
                    # Get algorithm performance metrics from BenchmarkController
                    algorithm_metrics = self._get_algorithm_metrics(algorithm)
                    
                    # Combine both metrics
                    algorithm_results = self._combine_metrics(load_test_results, algorithm_metrics, algorithm)
                    
                    # Store combined results
                    all_results[algorithm] = algorithm_results
                    
                    logger.info(f"✅ Completed load test for {algorithm}: {algorithm_results['load_test_metrics']['total_requests']} requests, {algorithm_results['algorithm_metrics']['requestCount']} algorithm requests")
                    
                except Exception as algo_error:
                    logger.error(f"Failed to run load test for {algorithm}: {algo_error}")
                    all_results[algorithm] = {
                        'algorithm': algorithm,
                        'total_requests': 0,
                        'success_rate': 0,
                        'avg_latency': 0,
                        'total_sessions': 0,
                        'test_summary': {
                            'total_requests': 0,
                            'success_rate_percent': 0,
                            'average_latency_seconds': 0,
                            'total_sessions': 0
                        },
                        'error': str(algo_error)
                    }
                
            # Clean up temp config file after all tests complete
            try:
                import os
                os.unlink(temp_config_path)
            except:
                pass
            
            # Generate comparative report
            self._generate_comparative_report(all_results)
            
        except Exception as e:
            logger.error(f"Fair load traffic generation failed: {e}")
            import traceback
            logger.error(f"Error details: {traceback.format_exc()}")
            # Fallback to simple HTTP requests
            self._simple_load_generation()
    
    def _get_test_algorithms(self):
        """Get available algorithms from benchmark controller"""
        try:
            response = requests.get(f"{self.config.load_balancer_url}/api/benchmark/status")
            if response.status_code == 200:
                data = response.json()
                return data.get('testAlgorithms', ['rl-agent', 'round-robin'])
        except:
            pass
        return ['rl-agent', 'round-robin']
    
    def _switch_to_algorithm(self, algorithm):
        """Switch load balancer to specific algorithm and verify switch"""
        try:
            # First, switch the algorithm
            response = requests.post(
                f"{self.config.load_balancer_url}/api/benchmark/switch",
                params={'algorithm': algorithm}
            )
            if response.status_code == 200:
                logger.info(f"✅ Algorithm switch request sent: {algorithm}")
                
                # Verify the switch took effect by checking status
                max_retries = 10
                for attempt in range(max_retries):
                    time.sleep(2)  # Wait for switch to take effect
                    try:
                        status_response = requests.get(f"{self.config.load_balancer_url}/api/benchmark/status")
                        if status_response.status_code == 200:
                            status_data = status_response.json()
                            current_algo = status_data.get('currentAlgorithm', '')
                            if current_algo == algorithm:
                                logger.info(f"✅ Verified algorithm switch to: {algorithm}")
                                return True
                            else:
                                logger.warning(f"Algorithm not switched yet. Current: {current_algo}, Expected: {algorithm}")
                    except Exception as verify_error:
                        logger.warning(f"Failed to verify algorithm switch (attempt {attempt + 1}): {verify_error}")
                
                logger.error(f"❌ Failed to verify algorithm switch to {algorithm} after {max_retries} attempts")
                return False
            else:
                logger.warning(f"Failed to switch to {algorithm}: {response.status_code}")
                return False
        except Exception as e:
            logger.error(f"Error switching to {algorithm}: {e}")
            return False
    
    def _create_diversified_config(self):
        """Create identical diversified scenario config for fair comparison"""
        return {
            "rl_training_scenarios": {
                "base_config": {
                    "base_url": self.config.load_balancer_url + "/proxy",
                    "request_timeout": 10,
                    "max_retries": 2
                },
                "training_scenarios": {
                    "diverse_load_patterns": {
                        "test_duration_minutes": max(30, self.config.test_duration_minutes),  # Minimum 30 minutes per algorithm
                        "total_users": self.config.concurrent_users * 2,
                        "max_concurrent_users": self.config.concurrent_users,
                        "traffic_patterns": [
                            {
                                "name": "quick_test",
                                "start_minute": 0,
                                "duration_minutes": max(30, self.config.test_duration_minutes),  # Configurable with 30-minute minimum
                                "intensity": "moderate",
                                "concurrent_users": self.config.concurrent_users,
                                "scenario_weights": {
                                    "browse_weight": 40,
                                    "search_weight": 20,
                                    "cart_weight": 30,
                                    "order_weight": 5,
                                    "profile_weight": 5
                                }
                            }
                        ]
                    }
                },
                "enhanced_test_products": [
                    {
                        "sku": "LAPTOP-001",
                        "name": "Dell XPS 13 Laptop",
                        "category": "Electronics",
                        "price": 1299.99,
                        "image": "https://example.com/laptop.jpg",
                        "popularity_score": 0.9
                    },
                    {
                        "sku": "PHONE-001", 
                        "name": "iPhone 15 Pro",
                        "category": "Electronics",
                        "price": 999.99,
                        "image": "https://example.com/phone.jpg",
                        "popularity_score": 0.95
                    },
                    {
                        "sku": "TABLET-001",
                        "name": "iPad Air 5th Gen", 
                        "category": "Electronics",
                        "price": 599.99,
                        "image": "https://example.com/tablet.jpg",
                        "popularity_score": 0.8
                    }
                ]
            }
        }
    
    def _generate_comparative_report(self, all_results):
        """Generate comparative report across all algorithms"""
        logger.info("📊 Generating comparative benchmark report")
        
        print("\n" + "="*80)
        print("🏆 FAIR DIVERSIFIED BENCHMARK RESULTS")
        print("="*80)
        
        for algorithm, results in all_results.items():
            print(f"\n📈 {algorithm.upper()} ALGORITHM:")
            
            if 'load_test_metrics' in results and 'algorithm_metrics' in results:
                # Combined metrics display
                load_metrics = results['load_test_metrics']
                algo_metrics = results['algorithm_metrics']
                
                print(f"   🔹 LOAD TEST METRICS:")
                print(f"      Generated Requests: {load_metrics.get('total_requests', 0):,}")
                print(f"      Success Rate: {load_metrics.get('success_rate_percent', 0):.2f}%")
                print(f"      Average Latency: {load_metrics.get('average_latency_seconds', 0):.3f}s")
                print(f"      Total Sessions: {load_metrics.get('total_sessions', 0)}")
                print(f"      Test Duration: {load_metrics.get('test_duration_minutes', 0):.1f} min")
                
                print(f"   🔹 ALGORITHM PERFORMANCE:")
                print(f"      Algorithm Requests: {algo_metrics.get('requestCount', 0):,}")
                print(f"      Avg Response Time: {algo_metrics.get('averageResponseTime', 0):.2f}ms")
                print(f"      Error Rate: {algo_metrics.get('errorRate', 0):.2f}%")
                print(f"      Error Count: {algo_metrics.get('errorCount', 0)}")
                
            elif 'test_summary' in results:
                # Fallback to test summary
                summary = results['test_summary']
                print(f"   Total Requests: {summary.get('total_requests', 0)}")
                print(f"   Success Rate: {summary.get('success_rate_percent', 0):.2f}%")
                print(f"   Average Latency: {summary.get('average_latency_seconds', 0):.3f}s")
                print(f"   Total Sessions: {summary.get('total_sessions', 0)}")
            else:
                # Error case
                print(f"   Total Requests: {results.get('total_requests', 0)}")
                print(f"   Success Rate: {results.get('success_rate', 0):.2f}%")
                print(f"   Average Latency: {results.get('avg_latency', 0):.3f}s")
                print(f"   Total Sessions: {results.get('total_sessions', 0)}")
                if 'error' in results:
                    print(f"   Error: {results['error']}")
        
        # Add algorithm comparison section
        self._generate_algorithm_comparison(all_results)
        
        print("\n" + "="*80)
    
    def _generate_algorithm_comparison(self, all_results):
        """Generate detailed comparison between algorithms"""
        if len(all_results) < 2:
            print("\n🔍 ALGORITHM COMPARISON:")
            print("   Need at least 2 algorithms to compare")
            return
        
        print("\n🔍 ALGORITHM COMPARISON:")
        print("="*50)
        
        # Extract metrics for comparison
        algorithms = list(all_results.keys())
        if len(algorithms) >= 2:
            algo1, algo2 = algorithms[0], algorithms[1]
            results1, results2 = all_results[algo1], all_results[algo2]
            
            # Compare algorithm performance metrics
            if ('algorithm_metrics' in results1 and 'algorithm_metrics' in results2):
                metrics1 = results1['algorithm_metrics']
                metrics2 = results2['algorithm_metrics']
                
                print(f"\n📊 PERFORMANCE COMPARISON ({algo1.upper()} vs {algo2.upper()}):")
                
                # Response Time Comparison
                rt1 = metrics1.get('averageResponseTime', 0)
                rt2 = metrics2.get('averageResponseTime', 0)
                if rt1 > 0 and rt2 > 0:
                    improvement = ((rt2 - rt1) / rt2) * 100
                    winner = algo1 if rt1 < rt2 else algo2
                    print(f"   🚀 Response Time: {winner.upper()} wins")
                    print(f"      {algo1}: {rt1:.2f}ms vs {algo2}: {rt2:.2f}ms")
                    print(f"      Improvement: {abs(improvement):.1f}% {'better' if improvement > 0 else 'worse'}")
                
                # Throughput Comparison
                req1 = metrics1.get('requestCount', 0)
                req2 = metrics2.get('requestCount', 0)
                if req1 > 0 and req2 > 0:
                    throughput_diff = ((req1 - req2) / req2) * 100
                    winner = algo1 if req1 > req2 else algo2
                    print(f"   📈 Throughput: {winner.upper()} wins")
                    print(f"      {algo1}: {req1:,} requests vs {algo2}: {req2:,} requests")
                    print(f"      Difference: {abs(throughput_diff):.1f}% {'higher' if throughput_diff > 0 else 'lower'}")
                
                # Error Rate Comparison
                err1 = metrics1.get('errorRate', 0)
                err2 = metrics2.get('errorRate', 0)
                winner = algo1 if err1 < err2 else algo2
                print(f"   🛡️  Reliability: {winner.upper()} wins")
                print(f"      {algo1}: {err1:.2f}% errors vs {algo2}: {err2:.2f}% errors")
                
                # Overall Winner
                print(f"\n🏆 OVERALL ASSESSMENT:")
                response_winner = algo1 if rt1 < rt2 and rt1 > 0 else algo2
                throughput_winner = algo1 if req1 > req2 else algo2
                reliability_winner = algo1 if err1 < err2 else algo2
                
                print(f"   Best Response Time: {response_winner.upper()}")
                print(f"   Best Throughput: {throughput_winner.upper()}")
                print(f"   Best Reliability: {reliability_winner.upper()}")
                
                # Recommendation
                if response_winner == throughput_winner == reliability_winner:
                    print(f"   🎯 RECOMMENDATION: {response_winner.upper()} is clearly superior")
                else:
                    print(f"   🎯 RECOMMENDATION: Mixed results - choose based on priority:")
                    print(f"      For speed: {response_winner.upper()}")
                    print(f"      For throughput: {throughput_winner.upper()}")
                    print(f"      For reliability: {reliability_winner.upper()}")
            
            # Compare load test metrics
            if ('load_test_metrics' in results1 and 'load_test_metrics' in results2):
                load1 = results1['load_test_metrics']
                load2 = results2['load_test_metrics']
                
                print(f"\n📋 LOAD TEST COMPARISON:")
                print(f"   Generated Requests: {algo1}: {load1.get('total_requests', 0):,} vs {algo2}: {load2.get('total_requests', 0):,}")
                print(f"   Success Rate: {algo1}: {load1.get('success_rate_percent', 0):.2f}% vs {algo2}: {load2.get('success_rate_percent', 0):.2f}%")
                print(f"   Test Latency: {algo1}: {load1.get('average_latency_seconds', 0):.3f}s vs {algo2}: {load2.get('average_latency_seconds', 0):.3f}s")
    
    def _enable_benchmark_mode(self):
        """Enable benchmark mode in BenchmarkController"""
        try:
            response = requests.post(f"{self.benchmark_api}/start", 
                                   json={"durationMinutes": self.config.test_duration_minutes}, 
                                   timeout=10)
            if response.status_code == 200:
                logger.info("✅ Enabled benchmark mode in controller")
            else:
                logger.warning(f"Failed to enable benchmark mode: {response.status_code}")
        except Exception as e:
            logger.error(f"Error enabling benchmark mode: {e}")
    
    def _get_algorithm_metrics(self, algorithm: str) -> Dict[str, Any]:
        """Get algorithm performance metrics from BenchmarkController"""
        try:
            response = requests.get(f"{self.benchmark_api}/status", timeout=10)
            if response.status_code == 200:
                status = response.json()
                current_stats = status.get('currentAlgorithmStats', {})
                return {
                    'algorithm': algorithm,
                    'requestCount': current_stats.get('requestCount', 0),
                    'averageResponseTime': current_stats.get('averageResponseTime', 0.0),
                    'errorRate': current_stats.get('errorRate', 0.0),
                    'errorCount': current_stats.get('errorCount', 0)
                }
            else:
                logger.warning(f"Failed to get algorithm metrics: {response.status_code}")
                return self._get_empty_algorithm_metrics(algorithm)
        except Exception as e:
            logger.error(f"Error getting algorithm metrics: {e}")
            return self._get_empty_algorithm_metrics(algorithm)
    
    def _get_empty_algorithm_metrics(self, algorithm: str) -> Dict[str, Any]:
        """Return empty algorithm metrics structure for failed tests"""
        return {
            'algorithm': algorithm,
            'load_test_metrics': {
                'total_requests': 0,
                'successful_requests': 0,
                'failed_requests': 0,
                'success_rate_percent': 0.0,
                'average_latency_seconds': 0.0,
                'total_sessions': 0,
                'test_duration_minutes': 0
            },
            'algorithm_metrics': {
                'algorithm': algorithm,
                'requestCount': 0,
                'averageResponseTime': 0.0,
                'errorRate': 0.0,
                'errorCount': 0
            },
            'test_summary': {
                'total_requests': 0,
                'success_rate_percent': 0.0,
                'average_latency_seconds': 0.0,
                'total_sessions': 0
            },
            'error': 'Failed to switch to algorithm'
        }
    
    def _reset_algorithm_metrics(self, algorithm: str):
        """Reset algorithm metrics in BenchmarkController before starting test"""
        try:
            response = requests.post(
                f"{self.config.load_balancer_url}/api/benchmark/reset",
                params={'algorithm': algorithm}
            )
            if response.status_code == 200:
                logger.info(f"✅ Reset metrics for algorithm: {algorithm}")
            else:
                logger.warning(f"Failed to reset metrics for {algorithm}: {response.status_code}")
        except Exception as e:
            logger.warning(f"Error resetting metrics for {algorithm}: {e}")
    
    def _combine_metrics(self, load_test_results: Dict[str, Any], 
                        algorithm_metrics: Dict[str, Any], 
                        algorithm: str) -> Dict[str, Any]:
        """Combine load test results with algorithm performance metrics"""
        return {
            'algorithm': algorithm,
            'load_test_metrics': {
                'total_requests': load_test_results['test_summary']['total_requests'],
                'successful_requests': load_test_results['test_summary']['successful_requests'],
                'failed_requests': load_test_results['test_summary']['failed_requests'],
                'success_rate_percent': load_test_results['test_summary']['success_rate_percent'],
                'average_latency_seconds': load_test_results['test_summary']['average_latency_seconds'],
                'total_sessions': load_test_results['test_summary']['total_sessions'],
                'test_duration_minutes': load_test_results['test_summary']['test_duration_minutes']
            },
            'algorithm_metrics': algorithm_metrics,
            'test_summary': load_test_results['test_summary']  # For backward compatibility
        }
    
    def _run_simple_load_test(self, algorithm: str, duration_minutes: int) -> Dict[str, Any]:
        """Run simple HTTP load test for specified duration"""
        logger.info(f"Running simple load test for {algorithm} - {duration_minutes} minutes")
        
        # Microservice endpoints through load balancer proxy
        endpoints = [
            "/user-service/actuator/health",
            "/cart-service/actuator/health", 
            "/order-service/actuator/health",
            "/inventory-service/actuator/health",
            "/payment-service/actuator/health",
            "/notification-service/actuator/health"
        ]
        
        # Test metrics
        total_requests = 0
        successful_requests = 0
        failed_requests = 0
        total_latency = 0.0
        
        def make_request():
            nonlocal total_requests, successful_requests, failed_requests, total_latency
            try:
                import random
                endpoint = random.choice(endpoints)
                start_time = time.time()
                response = requests.get(f"{self.config.load_balancer_url}{endpoint}", timeout=5)
                latency = time.time() - start_time
                
                total_requests += 1
                total_latency += latency
                
                if response.status_code in [200, 404]:  # 404 acceptable for health checks
                    successful_requests += 1
                    return True
                else:
                    failed_requests += 1
                    return False
            except Exception as e:
                total_requests += 1
                failed_requests += 1
                return False
        
        # Run load test for specified duration
        end_time = time.time() + (duration_minutes * 60)
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=self.config.concurrent_users) as executor:
            while time.time() < end_time:
                # Submit batch of requests
                futures = [executor.submit(make_request) for _ in range(10)]
                concurrent.futures.wait(futures, timeout=1)
                time.sleep(0.1)  # Small delay between batches
        
        # Calculate metrics
        success_rate = (successful_requests / total_requests * 100) if total_requests > 0 else 0
        avg_latency = (total_latency / successful_requests) if successful_requests > 0 else 0
        
        return {
            'total_requests': total_requests,
            'successful_requests': successful_requests,
            'failed_requests': failed_requests,
            'success_rate': round(success_rate, 2),
            'avg_latency': round(avg_latency, 3),
            'total_sessions': self.config.concurrent_users
        }
    
    def _simple_load_generation(self):
        """Fallback simple load generation"""
        logger.info("Using simple load generation fallback")
        
        # Correct microservice endpoints through load balancer proxy using actuator
        endpoints = [
            "/user-service/actuator/health",
            "/cart-service/actuator/health", 
            "/order-service/actuator/health",
            "/inventory-service/actuator/health",
            "/payment-service/actuator/health",
            "/notification-service/actuator/health"
        ]
        
        def make_request():
            try:
                # Randomly select an endpoint to simulate realistic traffic
                import random
                endpoint = random.choice(endpoints)
                response = requests.get(f"{self.config.load_balancer_url}{endpoint}", timeout=5)
                return response.status_code in [200, 404]  # 404 acceptable for health checks
            except:
                return False
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=self.config.concurrent_users) as executor:
            while not self.stop_traffic:
                futures = [executor.submit(make_request) for _ in range(10)]
                concurrent.futures.wait(futures, timeout=1)
                time.sleep(0.1)
    
    def _monitor_benchmark_progress(self):
        """Monitor benchmark progress and collect metrics"""
        logger.info("📊 Monitoring benchmark progress...")
        
        start_time = time.time()
        end_time = start_time + (self.config.test_duration_minutes * 60)
        
        while time.time() < end_time:
            try:
                # Get current status
                response = requests.get(f"{self.benchmark_api}/status", timeout=10)
                if response.status_code == 200:
                    status = response.json()
                    
                    if status.get('status') != 'active':
                        logger.info("Benchmark completed by controller")
                        break
                    
                    # Log progress
                    current_algo = status.get('currentAlgorithm', 'unknown')
                    phase = status.get('currentPhase', 0)
                    total_phases = status.get('totalPhases', 3)
                    progress = status.get('phaseProgress', 0)
                    
                    logger.info(f"Phase {phase}/{total_phases}: {current_algo} - {progress:.1f}% complete")
                    
                    # Store metrics
                    self.metrics_history.append({
                        'timestamp': datetime.now().isoformat(),
                        'status': status
                    })
                
                time.sleep(self.config.metrics_collection_interval)
                
            except Exception as e:
                logger.error(f"Error monitoring progress: {e}")
                time.sleep(5)
    
    def _generate_final_report(self):
        """Generate comprehensive final report"""
        logger.info("📋 Generating final benchmark report...")
        
        try:
            # Get final results from benchmark controller
            response = requests.get(f"{self.benchmark_api}/results", timeout=10)
            if response.status_code == 200:
                self.test_results = response.json()
                
                # Save detailed results
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                results_file = f"benchmark_results_{timestamp}.json"
                
                with open(results_file, 'w') as f:
                    json.dump({
                        'config': self.config.__dict__,
                        'results': self.test_results,
                        'metrics_history': self.metrics_history
                    }, f, indent=2)
                
                logger.info(f"📄 Results saved to: {results_file}")
                
                # Print summary
                self._print_summary_report()
                
        except Exception as e:
            logger.error(f"Failed to generate final report: {e}")
    
    def _print_summary_report(self):
        """Print summary of benchmark results"""
        if not self.test_results.get('results'):
            logger.warning("No results available for summary")
            return
            
        results = self.test_results['results']
        algorithm_results = results.get('algorithmResults', {})
        
        print("\n" + "="*80)
        print("🏆 BENCHMARK RESULTS SUMMARY")
        print("="*80)
        
        for algorithm, metrics in algorithm_results.items():
            print(f"\n📊 {algorithm.upper()}:")
            print(f"   Requests: {metrics.get('requestCount', 0):,}")
            print(f"   Avg Response Time: {metrics.get('averageResponseTime', 0):.2f}ms")
            print(f"   Error Rate: {metrics.get('errorRate', 0):.2f}%")
            print(f"   P95 Response Time: {metrics.get('p95ResponseTime', 0)}ms")
            print(f"   P99 Response Time: {metrics.get('p99ResponseTime', 0)}ms")
        
        # Performance comparison
        comparison = results.get('performanceComparison', {})
        if comparison:
            print(f"\n🎯 PERFORMANCE COMPARISON:")
            print(f"   Best Response Time: {comparison.get('bestResponseTime', 'N/A')}")
            print(f"   Best Throughput: {comparison.get('bestThroughput', 'N/A')}")
            print(f"   Lowest Error Rate: {comparison.get('lowestErrorRate', 'N/A')}")
            
            improvement = comparison.get('rlVsRoundRobinImprovement')
            if improvement:
                print(f"   RL vs Round-Robin: {improvement} improvement")
        
        print("="*80)
    
    def _cleanup(self):
        """Cleanup benchmark resources"""
        try:
            # Stop benchmark if still running
            requests.post(f"{self.benchmark_api}/stop", timeout=10)
        except:
            pass

def main():
    parser = argparse.ArgumentParser(description='Automated Load Balancer Benchmarking')
    parser.add_argument('--url', default='http://localhost:8080', help='Load balancer URL')
    parser.add_argument('--duration', type=int, default=30, help='Test duration in minutes (minimum 30)')
    parser.add_argument('--users', type=int, default=50, help='Concurrent users')
    parser.add_argument('--ramp-up', type=int, default=30, help='Ramp up time in seconds')
    
    args = parser.parse_args()
    
    # Enforce minimum 30-minute duration
    duration = max(30, args.duration)
    if args.duration < 30:
        logger.warning(f"Duration {args.duration} minutes is below minimum. Using 30 minutes.")
    
    config = BenchmarkConfig(
        load_balancer_url=args.url,
        test_duration_minutes=duration,
        concurrent_users=args.users,
        ramp_up_seconds=args.ramp_up,
        test_scenarios=["user_browsing", "cart_operations", "order_processing"]
    )
    
    benchmark = AutomatedBenchmark(config)
    success = benchmark.run_complete_benchmark()
    
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
