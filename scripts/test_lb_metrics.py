#!/usr/bin/env python3
"""
Test script to validate Load Balancer metrics collection
"""
import requests
import json
import time
import sys

def test_lb_metrics():
    """Test load balancer metrics collection endpoint"""
    try:
        # Make a request to trigger metrics collection
        url = "http://localhost:8080/proxy/user-service/api/users/register"
        headers = {"Content-Type": "application/json"}
        data = {"username": "test_user_metrics", "email": "test@metrics.com", "password": "password123"}
        
        print("🔄 Making test request to trigger metrics collection...")
        response = requests.post(url, json=data, headers=headers, timeout=10)
        print(f"   Response: {response.status_code}")
        
        # Wait a moment for metrics to be collected
        time.sleep(2)
        
        # Check RL experience collector for metrics
        rl_url = "http://localhost:8081/experiences/recent"
        print("🔍 Checking RL experience collector...")
        
        rl_response = requests.get(rl_url, timeout=10)
        if rl_response.status_code == 200:
            experiences = rl_response.json()
            if experiences:
                latest = experiences[0]
                state_metrics = latest.get("state", {}).get("metrics", {})
                
                print("📊 Latest RL Experience Metrics:")
                for pod_name, metrics in state_metrics.items():
                    cpu = metrics.get("cpuUsagePercent", 0)
                    memory = metrics.get("jvmMemoryUsagePercent", 0)
                    uptime = metrics.get("uptimeSeconds", 0)
                    requests_rate = metrics.get("requestRatePerSecond", 0)
                    
                    print(f"   {pod_name}:")
                    print(f"     CPU: {cpu}%, Memory: {memory}%, Uptime: {uptime}s, RPS: {requests_rate}")
                    
                    if cpu > 0 or memory > 0 or uptime > 0:
                        print("✅ Non-zero metrics found!")
                        return True
                
                print("❌ All metrics are still zero")
                return False
            else:
                print("❌ No RL experiences found")
                return False
        else:
            print(f"❌ RL collector not accessible: {rl_response.status_code}")
            return False
            
    except Exception as e:
        print(f"❌ Test failed: {e}")
        return False

def main():
    print("🧪 Testing Load Balancer Metrics Collection")
    print("=" * 50)
    
    success = test_lb_metrics()
    
    print("\n" + "=" * 50)
    if success:
        print("🎉 Load Balancer metrics collection is working!")
        return 0
    else:
        print("⚠️  Load Balancer metrics collection needs fixing")
        return 1

if __name__ == "__main__":
    sys.exit(main())
