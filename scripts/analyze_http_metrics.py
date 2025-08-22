#!/usr/bin/env python3
"""
Deep analysis of HTTP metrics in Prometheus for optimal query design
"""
import requests
import json
import sys

def query_prometheus(query):
    """Execute a Prometheus query and return results"""
    try:
        url = "http://localhost:9090/api/v1/query"
        params = {"query": query}
        response = requests.get(url, params=params, timeout=10)
        
        if response.status_code == 200:
            data = response.json()
            if data.get("status") == "success":
                return data.get("data", {}).get("result", [])
    except Exception as e:
        print(f"Query failed: {e}")
    return []

def analyze_http_metrics():
    """Analyze HTTP metrics structure and calculate optimal queries"""
    print("🔍 DEEP DIVE: HTTP METRICS ANALYSIS")
    print("=" * 60)
    
    # 1. Analyze request count metrics
    print("\n1. REQUEST COUNT METRICS:")
    count_results = query_prometheus("http_server_requests_seconds_count")
    print(f"   Total count metrics: {len(count_results)}")
    
    # Group by service
    services = {}
    for result in count_results:
        metric = result['metric']
        value = float(result['value'][1])
        
        # Extract service name (try multiple label patterns)
        service = metric.get('application', metric.get('job', 'unknown'))
        status = metric.get('status', '200')
        
        if service not in services:
            services[service] = {
                'total_requests': 0,
                'error_requests': 0,
                'success_requests': 0,
                'labels_seen': set()
            }
        
        services[service]['total_requests'] += value
        services[service]['labels_seen'].add(tuple(sorted(metric.items())))
        
        if status.startswith(('4', '5')):
            services[service]['error_requests'] += value
        else:
            services[service]['success_requests'] += value
    
    for service, data in services.items():
        error_rate = (data['error_requests'] / data['total_requests'] * 100) if data['total_requests'] > 0 else 0
        print(f"   {service}:")
        print(f"     Total: {data['total_requests']}, Errors: {data['error_requests']}, Error Rate: {error_rate:.2f}%")
        print(f"     Unique label combinations: {len(data['labels_seen'])}")
    
    # 2. Analyze response time metrics
    print("\n2. RESPONSE TIME METRICS:")
    sum_results = query_prometheus("http_server_requests_seconds_sum")
    print(f"   Total sum metrics: {len(sum_results)}")
    
    response_times = {}
    for result in sum_results:
        metric = result['metric']
        value = float(result['value'][1])
        
        service = metric.get('application', metric.get('job', 'unknown'))
        if service not in response_times:
            response_times[service] = {'total_time': 0}
        
        response_times[service]['total_time'] += value
    
    for service in services.keys():
        if service in response_times:
            total_time = response_times[service]['total_time']
            total_requests = services[service]['total_requests']
            avg_time = (total_time / total_requests * 1000) if total_requests > 0 else 0
            print(f"   {service}: Avg Response Time: {avg_time:.2f}ms")
    
    # 3. Test optimal query patterns
    print("\n3. OPTIMAL QUERY DESIGN:")
    
    test_queries = [
        ("Total Requests", "sum(http_server_requests_seconds_count{application=\"user-service\"})"),
        ("Error Requests", "sum(http_server_requests_seconds_count{application=\"user-service\", status=~\"4..|5..\"})"),
        ("Total Response Time", "sum(http_server_requests_seconds_sum{application=\"user-service\"})"),
        ("Request Rate (5m)", "sum(rate(http_server_requests_seconds_count{application=\"user-service\"}[5m]))"),
        ("Avg Response Time", "sum(http_server_requests_seconds_sum{application=\"user-service\"}) / sum(http_server_requests_seconds_count{application=\"user-service\"})"),
        ("Error Rate %", "sum(http_server_requests_seconds_count{application=\"user-service\", status=~\"4..|5..\"}) / sum(http_server_requests_seconds_count{application=\"user-service\"}) * 100"),
    ]
    
    for name, query in test_queries:
        results = query_prometheus(query)
        if results:
            value = float(results[0]['value'][1])
            print(f"   ✅ {name}: {value}")
        else:
            print(f"   ❌ {name}: No data")
    
    # 4. Generate final recommendations
    print("\n4. RECOMMENDED QUERIES FOR LOAD BALANCER:")
    print("   # Request Rate (requests/second)")
    print("   sum(rate(http_server_requests_seconds_count{application=\"SERVICE_NAME\"}[5m]))")
    print()
    print("   # Average Response Time (seconds)")
    print("   sum(http_server_requests_seconds_sum{application=\"SERVICE_NAME\"}) / sum(http_server_requests_seconds_count{application=\"SERVICE_NAME\"})")
    print()
    print("   # Error Rate (percentage)")
    print("   sum(http_server_requests_seconds_count{application=\"SERVICE_NAME\", status=~\"4..|5..\"}) / sum(http_server_requests_seconds_count{application=\"SERVICE_NAME\"}) * 100")

if __name__ == "__main__":
    analyze_http_metrics()
