#!/usr/bin/env python3
"""
Test script to validate Prometheus metrics collection
"""
import requests
import json
import time
import sys

def test_prometheus_query(query, description):
    """Test a Prometheus query and return the result"""
    try:
        url = "http://localhost:9090/api/v1/query"
        params = {"query": query}
        response = requests.get(url, params=params, timeout=10)
        
        if response.status_code == 200:
            data = response.json()
            if data.get("status") == "success":
                results = data.get("data", {}).get("result", [])
                if results:
                    print(f"✅ {description}: Found {len(results)} metrics")
                    for result in results[:3]:  # Show first 3 results
                        labels = result.get("metric", {})
                        value = result.get("value", [None, "0"])[1]
                        print(f"   - {labels}: {value}")
                    return True
                else:
                    print(f"❌ {description}: No data found")
                    return False
            else:
                print(f"❌ {description}: Query failed - {data.get('error', 'Unknown error')}")
                return False
        else:
            print(f"❌ {description}: HTTP {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ {description}: Exception - {e}")
        return False

def main():
    print("🔍 Testing Prometheus Metrics Collection")
    print("=" * 50)
    
    # Test basic service metrics
    queries = [
        ("process_uptime_seconds", "Service Uptime"),
        ("process_cpu_usage", "CPU Usage"),
        ("jvm_memory_used_bytes", "JVM Memory Used"),
        ("jvm_memory_max_bytes", "JVM Memory Max"),
        ("http_server_requests_seconds_count", "HTTP Request Count"),
        ("http_server_requests_seconds_sum", "HTTP Request Sum"),
    ]
    
    success_count = 0
    total_count = len(queries)
    
    for query, description in queries:
        if test_prometheus_query(query, description):
            success_count += 1
        time.sleep(1)
    
    print("\n" + "=" * 50)
    print(f"📊 Results: {success_count}/{total_count} queries successful")
    
    if success_count == total_count:
        print("🎉 All metrics are available!")
        return 0
    else:
        print("⚠️  Some metrics are missing")
        return 1

if __name__ == "__main__":
    sys.exit(main())
