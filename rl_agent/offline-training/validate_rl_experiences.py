#!/usr/bin/env python3
"""
Validate RL experience data collection after LoadBalancer fixes.
This script tests that complete RL experience tuples are being collected.
"""

import json
import requests
import time
import sys
from pathlib import Path
from typing import Dict, List, Any
from datetime import datetime

class RLExperienceValidator:
    """Validate that LoadBalancer is collecting complete RL experiences"""
    
    def __init__(self):
        self.load_balancer_url = "http://localhost:8080"
        self.collector_url = "http://localhost:8087"
        self.test_results = {
            "timestamp": datetime.now().isoformat(),
            "tests_run": 0,
            "tests_passed": 0,
            "issues_found": [],
            "sample_experiences": []
        }
    
    def check_services_health(self) -> bool:
        """Check if required services are running"""
        print("🔍 Checking service health...")
        
        services = {
            "Load Balancer": f"{self.load_balancer_url}/actuator/health",
            "RL Collector": f"{self.collector_url}/health"
        }
        
        all_healthy = True
        for service_name, health_url in services.items():
            try:
                response = requests.get(health_url, timeout=5)
                if response.status_code == 200:
                    print(f"✅ {service_name}: Healthy")
                else:
                    print(f"❌ {service_name}: Unhealthy (status: {response.status_code})")
                    all_healthy = False
            except Exception as e:
                print(f"❌ {service_name}: Not accessible ({e})")
                all_healthy = False
        
        return all_healthy
    
    def send_test_requests(self, num_requests: int = 10) -> List[Dict]:
        """Send test requests through the load balancer"""
        print(f"🚀 Sending {num_requests} test requests...")
        
        test_endpoints = [
            "/proxy/user-service/api/users/profile",
            "/proxy/cart-service/api/cart",
            "/proxy/order-service/api/orders/user/123",
            "/proxy/inventory-service/api/inventory/product/test-sku",
            "/proxy/payment-service/api/payments/test-payment"
        ]
        
        requests_sent = []
        
        for i in range(num_requests):
            endpoint = test_endpoints[i % len(test_endpoints)]
            url = f"{self.load_balancer_url}{endpoint}"
            
            try:
                # Add some delay between requests
                if i > 0:
                    time.sleep(0.5)
                
                start_time = time.time()
                response = requests.get(url, timeout=10, headers={
                    "Authorization": "Bearer test-token",
                    "X-Test-Request": f"validation-{i+1}"
                })
                end_time = time.time()
                
                request_info = {
                    "request_id": i + 1,
                    "endpoint": endpoint,
                    "status_code": response.status_code,
                    "response_time_ms": round((end_time - start_time) * 1000, 2),
                    "timestamp": datetime.now().isoformat()
                }
                
                requests_sent.append(request_info)
                print(f"  Request {i+1}: {endpoint} -> {response.status_code} ({request_info['response_time_ms']}ms)")
                
            except Exception as e:
                print(f"  Request {i+1}: {endpoint} -> ERROR: {e}")
                requests_sent.append({
                    "request_id": i + 1,
                    "endpoint": endpoint,
                    "error": str(e),
                    "timestamp": datetime.now().isoformat()
                })
        
        return requests_sent
    
    def wait_for_experience_collection(self, wait_seconds: int = 10):
        """Wait for RL experiences to be collected and processed"""
        print(f"⏳ Waiting {wait_seconds} seconds for experience collection...")
        time.sleep(wait_seconds)
    
    def fetch_collected_experiences(self) -> List[Dict]:
        """Fetch collected RL experiences from the collector"""
        print("📊 Fetching collected RL experiences...")
        
        try:
            # Try to get experiences from collector API if available
            response = requests.get(f"{self.collector_url}/experiences", timeout=5)
            if response.status_code == 200:
                return response.json()
        except Exception as e:
            print(f"⚠️  Could not fetch via API: {e}")
        
        # Fallback: try to read from collector container
        print("📁 Attempting to read experiences from collector container...")
        try:
            import subprocess
            result = subprocess.run([
                "kubectl", "exec", "-n", "default", 
                "deployment/rl-experience-collector", "--",
                "tail", "-n", "50", "/app/data/rl_experiences.jsonl"
            ], capture_output=True, text=True, timeout=30)
            
            if result.returncode == 0:
                experiences = []
                for line in result.stdout.strip().split('\n'):
                    if line.strip():
                        try:
                            experiences.append(json.loads(line))
                        except json.JSONDecodeError:
                            continue
                return experiences[-20:]  # Return last 20 experiences
            else:
                print(f"❌ Failed to read from container: {result.stderr}")
                
        except Exception as e:
            print(f"❌ Container read failed: {e}")
        
        return []
    
    def validate_experience_structure(self, experience: Dict) -> Dict[str, Any]:
        """Validate that an RL experience has the correct structure"""
        validation_result = {
            "valid": True,
            "issues": [],
            "completeness_score": 0
        }
        
        required_fields = ["state", "action", "reward", "next_state", "metadata"]
        
        # Check required fields
        for field in required_fields:
            if field not in experience:
                validation_result["issues"].append(f"Missing field: {field}")
                validation_result["valid"] = False
            else:
                validation_result["completeness_score"] += 20
        
        # Validate state structure
        if "state" in experience:
            state = experience["state"]
            if not isinstance(state, dict) or "metrics" not in state:
                validation_result["issues"].append("Invalid state structure")
                validation_result["valid"] = False
            elif not state["metrics"]:
                validation_result["issues"].append("Empty state metrics")
                validation_result["valid"] = False
        
        # Validate action
        if "action" in experience:
            action = experience["action"]
            if not action or not isinstance(action, str):
                validation_result["issues"].append("Invalid or empty action")
                validation_result["valid"] = False
        
        # Validate reward
        if "reward" in experience:
            reward = experience["reward"]
            if not isinstance(reward, (int, float)):
                validation_result["issues"].append("Invalid reward type")
                validation_result["valid"] = False
        
        # Validate next_state
        if "next_state" in experience:
            next_state = experience["next_state"]
            if not isinstance(next_state, dict) or "metrics" not in next_state:
                validation_result["issues"].append("Invalid next_state structure")
                validation_result["valid"] = False
        
        return validation_result
    
    def analyze_experiences(self, experiences: List[Dict]) -> Dict[str, Any]:
        """Analyze collected RL experiences for quality and completeness"""
        print(f"🔬 Analyzing {len(experiences)} collected experiences...")
        
        analysis = {
            "total_experiences": len(experiences),
            "valid_experiences": 0,
            "invalid_experiences": 0,
            "completeness_scores": [],
            "common_issues": {},
            "action_distribution": {},
            "reward_stats": {
                "min": None,
                "max": None,
                "avg": None,
                "positive_rewards": 0,
                "negative_rewards": 0,
                "zero_rewards": 0
            }
        }
        
        if not experiences:
            print("❌ No experiences found to analyze!")
            return analysis
        
        rewards = []
        
        for i, exp in enumerate(experiences):
            validation = self.validate_experience_structure(exp)
            
            if validation["valid"]:
                analysis["valid_experiences"] += 1
            else:
                analysis["invalid_experiences"] += 1
                
                # Track common issues
                for issue in validation["issues"]:
                    analysis["common_issues"][issue] = analysis["common_issues"].get(issue, 0) + 1
            
            analysis["completeness_scores"].append(validation["completeness_score"])
            
            # Track action distribution
            if "action" in exp and exp["action"]:
                action = exp["action"]
                analysis["action_distribution"][action] = analysis["action_distribution"].get(action, 0) + 1
            
            # Track reward statistics
            if "reward" in exp and isinstance(exp["reward"], (int, float)):
                reward = exp["reward"]
                rewards.append(reward)
                
                if reward > 0:
                    analysis["reward_stats"]["positive_rewards"] += 1
                elif reward < 0:
                    analysis["reward_stats"]["negative_rewards"] += 1
                else:
                    analysis["reward_stats"]["zero_rewards"] += 1
        
        # Calculate reward statistics
        if rewards:
            analysis["reward_stats"]["min"] = min(rewards)
            analysis["reward_stats"]["max"] = max(rewards)
            analysis["reward_stats"]["avg"] = sum(rewards) / len(rewards)
        
        return analysis
    
    def generate_report(self, requests_sent: List[Dict], experiences: List[Dict], analysis: Dict[str, Any]):
        """Generate comprehensive validation report"""
        print("\n" + "="*80)
        print("🎯 RL EXPERIENCE VALIDATION REPORT")
        print("="*80)
        
        print(f"\n📊 TEST SUMMARY:")
        print(f"   • Test requests sent: {len(requests_sent)}")
        print(f"   • RL experiences collected: {analysis['total_experiences']}")
        print(f"   • Valid experiences: {analysis['valid_experiences']}")
        print(f"   • Invalid experiences: {analysis['invalid_experiences']}")
        
        if analysis['total_experiences'] > 0:
            success_rate = (analysis['valid_experiences'] / analysis['total_experiences']) * 100
            print(f"   • Success rate: {success_rate:.1f}%")
            
            if success_rate >= 90:
                print("   ✅ EXCELLENT: RL experience collection is working properly!")
            elif success_rate >= 70:
                print("   ⚠️  GOOD: Most experiences are valid, minor issues detected")
            else:
                print("   ❌ POOR: Significant issues with RL experience collection")
        else:
            print("   ❌ CRITICAL: No RL experiences collected!")
        
        print(f"\n🎯 ACTION ANALYSIS:")
        if analysis['action_distribution']:
            print("   Actions taken:")
            for action, count in analysis['action_distribution'].items():
                print(f"     • {action}: {count} times")
        else:
            print("   ❌ No valid actions recorded")
        
        print(f"\n💰 REWARD ANALYSIS:")
        reward_stats = analysis['reward_stats']
        if reward_stats['avg'] is not None:
            print(f"   • Average reward: {reward_stats['avg']:.3f}")
            print(f"   • Reward range: {reward_stats['min']:.3f} to {reward_stats['max']:.3f}")
            print(f"   • Positive rewards: {reward_stats['positive_rewards']}")
            print(f"   • Negative rewards: {reward_stats['negative_rewards']}")
            print(f"   • Zero rewards: {reward_stats['zero_rewards']}")
        else:
            print("   ❌ No valid rewards recorded")
        
        print(f"\n🐛 ISSUES FOUND:")
        if analysis['common_issues']:
            for issue, count in analysis['common_issues'].items():
                print(f"   • {issue}: {count} occurrences")
        else:
            print("   ✅ No structural issues found")
        
        # Sample experiences
        if experiences:
            print(f"\n📋 SAMPLE EXPERIENCES:")
            for i, exp in enumerate(experiences[:3]):  # Show first 3
                print(f"   Experience {i+1}:")
                print(f"     • Action: {exp.get('action', 'MISSING')}")
                print(f"     • Reward: {exp.get('reward', 'MISSING')}")
                print(f"     • State metrics count: {len(exp.get('state', {}).get('metrics', {}))}")
                print(f"     • Next state metrics count: {len(exp.get('next_state', {}).get('metrics', {}))}")
        
        print("\n" + "="*80)
        
        # Save detailed report
        report_file = Path("rl_experience_validation_report.json")
        detailed_report = {
            "timestamp": datetime.now().isoformat(),
            "requests_sent": requests_sent,
            "experiences_analyzed": len(experiences),
            "analysis": analysis,
            "sample_experiences": experiences[:5]  # Save first 5 for reference
        }
        
        with open(report_file, 'w') as f:
            json.dump(detailed_report, f, indent=2)
        
        print(f"📄 Detailed report saved to: {report_file}")
    
    def run_validation(self):
        """Run complete RL experience validation"""
        print("🎯 Starting RL Experience Validation...")
        print("="*60)
        
        # Check service health
        if not self.check_services_health():
            print("❌ Services not healthy. Please start the required services first.")
            return False
        
        # Send test requests
        requests_sent = self.send_test_requests(15)
        
        # Wait for collection
        self.wait_for_experience_collection(15)
        
        # Fetch and analyze experiences
        experiences = self.fetch_collected_experiences()
        analysis = self.analyze_experiences(experiences)
        
        # Generate report
        self.generate_report(requests_sent, experiences, analysis)
        
        # Return success status
        return analysis['valid_experiences'] > 0

if __name__ == "__main__":
    validator = RLExperienceValidator()
    success = validator.run_validation()
    
    if success:
        print("\n🎉 Validation completed successfully!")
        sys.exit(0)
    else:
        print("\n❌ Validation failed - check the issues above")
        sys.exit(1)
