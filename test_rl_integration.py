#!/usr/bin/env python3
"""
Integration test script for RL Load Balancer Architecture
Tests the new API-based RL routing system
"""

import requests
import json
import time
import sys
from typing import Dict, Any, Optional

class RLIntegrationTester:
    def __init__(self, lb_url: str = "http://localhost:8080", rl_url: str = "http://localhost:8088"):
        self.lb_url = lb_url
        self.rl_url = rl_url
        self.test_results = []
        
    def log_test(self, test_name: str, success: bool, message: str, details: Optional[Dict] = None):
        """Log test result"""
        result = {
            "test": test_name,
            "success": success,
            "message": message,
            "details": details or {},
            "timestamp": time.time()
        }
        self.test_results.append(result)
        
        status = "✅ PASS" if success else "❌ FAIL"
        print(f"{status} {test_name}: {message}")
        if details:
            print(f"    Details: {json.dumps(details, indent=2)}")
        print()

    def test_rl_api_health(self) -> bool:
        """Test RL API health endpoint"""
        try:
            response = requests.get(f"{self.rl_url}/health", timeout=5)
            success = response.status_code == 200
            
            if success:
                health_data = response.json()
                self.log_test("RL API Health", True, "RL API is healthy", health_data)
            else:
                self.log_test("RL API Health", False, f"Health check failed: {response.status_code}")
            
            return success
        except Exception as e:
            self.log_test("RL API Health", False, f"Failed to connect to RL API: {str(e)}")
            return False

    def test_rl_api_stats(self) -> bool:
        """Test RL API stats endpoint"""
        try:
            response = requests.get(f"{self.rl_url}/stats", timeout=5)
            success = response.status_code == 200
            
            if success:
                stats_data = response.json()
                self.log_test("RL API Stats", True, "RL API stats retrieved", stats_data)
            else:
                self.log_test("RL API Stats", False, f"Stats endpoint failed: {response.status_code}")
            
            return success
        except Exception as e:
            self.log_test("RL API Stats", False, f"Failed to get RL stats: {str(e)}")
            return False

    def test_rl_decision_endpoint(self) -> bool:
        """Test RL decision endpoint"""
        try:
            decision_request = {
                "serviceName": "user-service",
                "currentState": {
                    "cpu_usage": 0.5,
                    "memory_usage": 0.6,
                    "response_time": 100,
                    "error_rate": 0.01,
                    "throughput": 50,
                    "healthy_instances": 3
                }
            }
            
            response = requests.post(
                f"{self.rl_url}/decide", 
                json=decision_request, 
                timeout=5
            )
            success = response.status_code == 200
            
            if success:
                decision_data = response.json()
                expected_fields = ["selectedPod", "confidence", "decisionType"]
                has_required_fields = all(field in decision_data for field in expected_fields)
                
                if has_required_fields:
                    self.log_test("RL Decision Endpoint", True, "RL decision successful", decision_data)
                else:
                    self.log_test("RL Decision Endpoint", False, "Missing required fields in response", decision_data)
                    success = False
            else:
                self.log_test("RL Decision Endpoint", False, f"Decision endpoint failed: {response.status_code}")
            
            return success
        except Exception as e:
            self.log_test("RL Decision Endpoint", False, f"Failed to get RL decision: {str(e)}")
            return False

    def test_lb_health(self) -> bool:
        """Test Load Balancer health"""
        try:
            response = requests.get(f"{self.lb_url}/actuator/health", timeout=5)
            success = response.status_code == 200
            
            if success:
                health_data = response.json()
                self.log_test("Load Balancer Health", True, "Load Balancer is healthy", health_data)
            else:
                self.log_test("Load Balancer Health", False, f"LB health check failed: {response.status_code}")
            
            return success
        except Exception as e:
            self.log_test("Load Balancer Health", False, f"Failed to connect to Load Balancer: {str(e)}")
            return False

    def test_rl_monitoring_endpoints(self) -> bool:
        """Test RL monitoring endpoints"""
        try:
            # Test RL status endpoint
            response = requests.get(f"{self.lb_url}/rl/status", timeout=5)
            success = response.status_code == 200
            
            if success:
                status_data = response.json()
                self.log_test("RL Monitoring Status", True, "RL monitoring status retrieved", status_data)
                
                # Check if it's using API-based routing
                routing_type = status_data.get("routingType")
                if routing_type == "api-based":
                    self.log_test("RL Routing Type", True, "Using API-based RL routing")
                else:
                    self.log_test("RL Routing Type", False, f"Expected api-based, got: {routing_type}")
                    success = False
            else:
                self.log_test("RL Monitoring Status", False, f"RL status endpoint failed: {response.status_code}")
            
            # Test strategies endpoint
            response = requests.get(f"{self.lb_url}/rl/strategies", timeout=5)
            if response.status_code == 200:
                strategies_data = response.json()
                self.log_test("RL Strategies", True, "RL strategies retrieved", strategies_data)
            else:
                self.log_test("RL Strategies", False, f"RL strategies endpoint failed: {response.status_code}")
                success = False
            
            return success
        except Exception as e:
            self.log_test("RL Monitoring Endpoints", False, f"Failed to test RL monitoring: {str(e)}")
            return False

    def test_routing_with_rl(self) -> bool:
        """Test actual routing with RL"""
        try:
            # Make a routing request through the load balancer
            response = requests.get(f"{self.lb_url}/api/users/health", timeout=10)
            success = response.status_code in [200, 404]  # 404 is ok if service not running
            
            if success:
                self.log_test("RL Routing Test", True, "Routing request completed successfully")
                
                # Check RL monitoring stats after routing
                time.sleep(1)  # Allow stats to update
                stats_response = requests.get(f"{self.lb_url}/rl/status", timeout=5)
                if stats_response.status_code == 200:
                    stats_data = stats_response.json()
                    routing_stats = stats_data.get("routingStats", {})
                    total_decisions = routing_stats.get("totalDecisions", 0)
                    
                    if total_decisions > 0:
                        self.log_test("RL Stats Update", True, f"RL routing stats updated: {total_decisions} decisions")
                    else:
                        self.log_test("RL Stats Update", False, "RL routing stats not updated")
            else:
                self.log_test("RL Routing Test", False, f"Routing request failed: {response.status_code}")
            
            return success
        except Exception as e:
            self.log_test("RL Routing Test", False, f"Failed to test routing: {str(e)}")
            return False

    def run_all_tests(self) -> bool:
        """Run all integration tests"""
        print("🧪 Starting RL Load Balancer Integration Tests")
        print("=" * 50)
        print()
        
        tests = [
            ("RL API Health", self.test_rl_api_health),
            ("RL API Stats", self.test_rl_api_stats),
            ("RL Decision Endpoint", self.test_rl_decision_endpoint),
            ("Load Balancer Health", self.test_lb_health),
            ("RL Monitoring Endpoints", self.test_rl_monitoring_endpoints),
            ("RL Routing Test", self.test_routing_with_rl)
        ]
        
        passed = 0
        total = len(tests)
        
        for test_name, test_func in tests:
            if test_func():
                passed += 1
        
        print("=" * 50)
        print(f"🎯 Test Results: {passed}/{total} tests passed")
        
        if passed == total:
            print("✅ All integration tests passed!")
            return True
        else:
            print("❌ Some tests failed. Check the logs above.")
            return False

    def generate_report(self) -> Dict[str, Any]:
        """Generate test report"""
        passed = sum(1 for result in self.test_results if result["success"])
        total = len(self.test_results)
        
        return {
            "summary": {
                "total_tests": total,
                "passed": passed,
                "failed": total - passed,
                "success_rate": passed / total if total > 0 else 0
            },
            "tests": self.test_results,
            "timestamp": time.time()
        }

def main():
    """Main test execution"""
    import argparse
    
    parser = argparse.ArgumentParser(description="RL Load Balancer Integration Tests")
    parser.add_argument("--lb-url", default="http://localhost:8080", help="Load Balancer URL")
    parser.add_argument("--rl-url", default="http://localhost:8088", help="RL API URL")
    parser.add_argument("--report", help="Save test report to file")
    
    args = parser.parse_args()
    
    tester = RLIntegrationTester(args.lb_url, args.rl_url)
    success = tester.run_all_tests()
    
    if args.report:
        report = tester.generate_report()
        with open(args.report, 'w') as f:
            json.dump(report, f, indent=2)
        print(f"📄 Test report saved to: {args.report}")
    
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
