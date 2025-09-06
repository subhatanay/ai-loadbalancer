import requests
from typing import Dict, List, Any, Optional
from config.settings import PROMETHEUS_HOST, PROMETHEUS_PORT, settings
from utils.simple_cache import METRICS_CACHE
import time
from models.metrics_model import MetricValue, ServiceMetrics
from datetime import datetime
import logging

logger = logging.getLogger(__name__)

class PrometheusClient:
    def __init__(self):
        self.base_url = f"http://{PROMETHEUS_HOST}:{PROMETHEUS_PORT}"
        self.timeout = 15  # seconds
        logger.info(f"PrometheusClient initialized with base_url: {self.base_url}")
    
    def query(self, query: str) -> Optional[Dict[str, Any]]:
        """Execute a PromQL query with caching and return the result"""
        cache_key = f"query_{hash(query)}"
        
        # Try cache first
        cached_result = METRICS_CACHE.get(cache_key)
        if cached_result is not None:
            logger.debug(f"Using cached Prometheus query result")
            return cached_result
        
        # Cache miss - execute query
        try:
            url = f"{self.base_url}/api/v1/query"
            params = {'query': query}
            
            response = requests.get(url, params=params, timeout=self.timeout)
            response.raise_for_status()
            
            result = response.json()
            if result['status'] == 'success':
                data = result['data']
                # Cache the result
                METRICS_CACHE.set(cache_key, data)
                logger.debug(f"Cached Prometheus query result")
                return data
            else:
                logger.error(f"Prometheus query failed: {result}")
                return None
                
        except requests.exceptions.Timeout:
            logger.error(f"Timeout while querying Prometheus: {query}")
            return None
        except requests.exceptions.RequestException as e:
            logger.error(f"Error querying Prometheus: {e}")
            return None
        except Exception as e:
            logger.error(f"Unexpected error in Prometheus query: {e}")
            return None

    def get_service_metrics(self, service_instances: List) -> List[ServiceMetrics]:
        """Collect comprehensive metrics for all service instances"""
        import time
        start_time = time.time()
        
        logger.info(f"Starting metrics collection for {len(service_instances)} instances")

        all_metrics = []

        for instance in service_instances:
            try:
                instance_start = time.time()
                service_metrics = self._collect_instance_metrics(instance)
                instance_time = (time.time() - instance_start) * 1000
                
                if service_metrics:
                    all_metrics.append(service_metrics)
                    instance_id = instance.get('instanceName', 'unknown') if isinstance(instance, dict) else getattr(instance, 'instance_id', 'unknown')
                    logger.debug(f"Instance metrics collected in {instance_time:.2f}ms for {instance_id}")

            except Exception as e:
                instance_id = instance.get('instanceName', 'unknown') if isinstance(instance, dict) else getattr(instance, 'instance_id', 'unknown')
                logger.error(f"Failed to collect metrics for instance {instance_id}: {str(e)}")

        total_time = (time.time() - start_time) * 1000
        logger.info(f"PROMETHEUS_TIMING: {total_time:.2f}ms total for {len(service_instances)} instances")
        return all_metrics

    def _collect_instance_metrics(self, instance) -> Optional[ServiceMetrics]:
        """Collect metrics for a single service instance"""
        # Handle both dict and object instances
        if isinstance(instance, dict):
            instance_id = instance.get('instanceName') or instance.get('url', 'unknown')
            service_name = instance.get('serviceName', 'unknown')
        else:
            instance_id = getattr(instance, 'instance_id', 'unknown')
            service_name = getattr(instance, 'service_name', 'unknown')
            
        pod_name = self._extract_pod_name(instance_id)

        # Build metric queries for this specific pod using correct label names
        # Use 'instance' label which matches the pod name in our Prometheus setup
        queries = {
            "cpu_usage": f'system_cpu_usage{{instance="{pod_name}"}}',
            "jvm_memory_used": f'jvm_memory_used_bytes{{instance="{pod_name}", area="heap"}}',
            "jvm_memory_max": f'jvm_memory_max_bytes{{instance="{pod_name}", area="heap"}}',
            "uptime": f'process_uptime_seconds{{instance="{pod_name}"}}',
            "request_count": f'sum(rate(http_server_requests_seconds_count{{instance="{pod_name}"}}[{settings.metrics_time_range}]))',
            "request_sum": f'sum(rate(http_server_requests_seconds_sum{{instance="{pod_name}"}}[{settings.metrics_time_range}]))',
            "error_rate": f'sum(rate(http_server_requests_seconds_count{{instance="{pod_name}", status=~"4..|5.."}}[{settings.metrics_time_range}]))'
        }

        # Collect metrics using pod-specific queries
        results = {}
        for metric_name, query in queries.items():
            metrics = self.query_metric(query)
            if metrics:
                if metric_name == "jvm_memory_max":
                    # For JVM memory max, find the first non-negative value
                    valid_values = [m.value for m in metrics if m.value > 0]
                    if valid_values:
                        results[metric_name] = valid_values[0]
                        logger.debug(f"Found valid {metric_name} for {pod_name}: {valid_values[0]} (filtered from {len(metrics)} results)")
                    else:
                        results[metric_name] = None
                        logger.warning(f"No valid {metric_name} found for {pod_name} - all values were -1 or 0")
                else:
                    # For other metrics, take the first result (aggregated queries return single values)
                    results[metric_name] = metrics[0].value
                    logger.debug(f"Found {metric_name} for {pod_name}: {metrics[0].value}")
            else:
                results[metric_name] = None
                logger.warning(f"No {metric_name} found for {pod_name} with query: {query}")

        # Calculate derived metrics
        cpu_usage_percent = results["cpu_usage"] * 100 if results["cpu_usage"] else None
        logger.debug(f"CPU calculation for {pod_name}: raw={results['cpu_usage']} -> percent={cpu_usage_percent}")

        jvm_memory_usage_percent = None
        if results["jvm_memory_used"] and results["jvm_memory_max"] and results["jvm_memory_max"] > 0:
            jvm_memory_usage_percent = (results["jvm_memory_used"] / results["jvm_memory_max"]) * 100

        avg_response_time_ms = None
        if results["request_sum"] and results["request_count"] and results["request_count"] > 0:
            avg_response_time_ms = (results["request_sum"] / results["request_count"]) * 1000

        error_rate_percent = None
        if results["error_rate"] and results["request_count"] and results["request_count"] > 0:
            error_rate_percent = (results["error_rate"] / results["request_count"]) * 100

        return ServiceMetrics(
            service_name=service_name,
            instance_id=instance_id,
            pod_name=pod_name,
            timestamp=datetime.now(),
            cpu_usage_percent=cpu_usage_percent,
            jvm_memory_usage_percent=jvm_memory_usage_percent,
            uptime_seconds=results["uptime"],
            request_rate_per_second=results["request_count"],
            avg_response_time_ms=avg_response_time_ms,
            error_rate_percent=error_rate_percent or 0.0,
            total_requests=int(results["request_count"] * 60) if results["request_count"] else 0
        )

    def _extract_pod_name(self, instance_id: str) -> str:
        """Extract pod name from instance ID or URL"""
        # Assuming instance_id contains pod name or can be derived
        # Adjust this logic based on your actual instance ID format
        if "://" in instance_id:
            # If it's a URL, extract from the host part
            return instance_id.split("://")[1].split(":")[0]
        return instance_id

    def query_metric(self, query: str) -> List[MetricValue]:
        """Execute a query and return list of MetricValue objects with caching"""
        cache_key = f"metric_{hash(query)}"
        
        # Try cache first
        cached_metrics = METRICS_CACHE.get(cache_key)
        if cached_metrics is not None:
            logger.debug(f"Using cached metric values")
            return cached_metrics
        
        # Cache miss - execute query
        data = self.query(query)
        if not data or 'result' not in data:
            return []
        
        metrics = []
        for result in data['result']:
            try:
                value = float(result['value'][1])
                metric = MetricValue(
                    metric_name=result['metric'].get('__name__', 'unknown'),
                    labels=result['metric'],
                    value=value,
                    timestamp=result['value'][0]
                )
                metrics.append(metric)
            except (ValueError, KeyError, IndexError) as e:
                logger.warning(f"Failed to parse metric result: {e}")
                continue
        
        # Cache the result
        METRICS_CACHE.set(cache_key, metrics)
        logger.debug(f"Cached {len(metrics)} metric values")
        
        return metrics
    
    def invalidate_cache(self, query: Optional[str] = None) -> None:
        """
        Invalidate cache entries for debugging/testing
        If query is provided, only invalidate that query's cache
        """
        if query:
            cache_key = f"query_{hash(query)}"
            metric_key = f"metric_{hash(query)}"
            METRICS_CACHE.delete(cache_key)
            METRICS_CACHE.delete(metric_key)
            logger.debug(f"Invalidated cache for query: {query}")
        else:
            METRICS_CACHE.clear()
            logger.debug("Invalidated all metrics cache")
    
    def get_cache_stats(self) -> Dict[str, Any]:
        """Get metrics cache statistics"""
        return METRICS_CACHE.get_stats()

    def health_check(self) -> bool:
        """Check if Prometheus is accessible"""
        try:
            url = f"{self.base_url}/-/healthy"
            response = requests.get(url, timeout=5)
            return response.status_code == 200
        except:
            return False