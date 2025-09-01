import requests
from typing import Dict, List, Optional
from datetime import datetime, timedelta
from models.metrics_model import MetricValue, ServiceMetrics
from utils.logger import logger
from config.settings import settings

class PrometheusClient:
    def __init__(self):
        self.base_url = f"http://{settings.prometheus_host}:{settings.prometheus_port}"
        self.timeout = 15

    def query_metric(self, query: str) -> List[MetricValue]:
        """Query Prometheus for a specific metric"""
        try:
            url = f"{self.base_url}/api/v1/query"
            params = {"query": query}

            response = requests.get(url, params=params, timeout=self.timeout)
            response.raise_for_status()

            data = response.json()
            metrics = []

            if data["status"] == "success" and "result" in data["data"]:
                for result in data["data"]["result"]:
                    metric = MetricValue(
                        metric_name=result["metric"].get("__name__", "unknown"),
                        value=float(result["value"][1]),
                        timestamp=datetime.fromtimestamp(float(result["value"][0])),
                        labels=result["metric"]
                    )
                    metrics.append(metric)

            return metrics

        except Exception as e:
            logger.error("Failed to query Prometheus", query=query, error=str(e))
            return []

    def get_service_metrics(self, service_instances: List) -> List[ServiceMetrics]:
        """Collect comprehensive metrics for all service instances"""
        import time
        start_time = time.time()
        
        logger.info("Starting metrics collection", total_instances=len(service_instances))

        all_metrics = []

        for instance in service_instances:
            try:
                instance_start = time.time()
                service_metrics = self._collect_instance_metrics(instance)
                instance_time = (time.time() - instance_start) * 1000
                
                if service_metrics:
                    all_metrics.append(service_metrics)
                    logger.debug(f"Instance metrics collected in {instance_time:.2f}ms for {instance.instance_id}")

            except Exception as e:
                logger.error("Failed to collect metrics for instance",
                             instance=instance.instance_id, error=str(e))

        total_time = (time.time() - start_time) * 1000
        logger.info(f"PROMETHEUS_TIMING: {total_time:.2f}ms total for {len(service_instances)} instances")
        return all_metrics

    def _collect_instance_metrics(self, instance) -> Optional[ServiceMetrics]:
        """Collect metrics for a single service instance"""
        pod_name = self._extract_pod_name(instance.instance_id)

        # Build metric queries for this specific pod
        queries = {
            "cpu_usage": f'system_cpu_usage{{job="kubernetes-pods", namespace="ai-loadbalancer", pod="{pod_name}"}}',
            "jvm_memory_used": f'jvm_memory_used_bytes{{job="kubernetes-pods", namespace="ai-loadbalancer", pod="{pod_name}", area="heap"}}',
            "jvm_memory_max": f'jvm_memory_max_bytes{{job="kubernetes-pods", namespace="ai-loadbalancer", pod="{pod_name}", area="heap"}}',
            "uptime": f'process_uptime_seconds{{job="kubernetes-pods", namespace="ai-loadbalancer", pod="{pod_name}"}}',
            "request_count": f'rate(http_server_requests_seconds_count{{job="kubernetes-pods", namespace="ai-loadbalancer", pod="{pod_name}"}}[{settings.metrics_time_range}])',
            "request_sum": f'rate(http_server_requests_seconds_sum{{job="kubernetes-pods", namespace="ai-loadbalancer", pod="{pod_name}"}}[{settings.metrics_time_range}])',
            "error_rate": f'rate(http_server_requests_seconds_count{{job="kubernetes-pods", namespace="ai-loadbalancer", pod="{pod_name}", status=~"4..|5.."}}[{settings.metrics_time_range}])'
        }

        # Collect all metrics
        results = {}
        for metric_name, query in queries.items():
            metrics = self.query_metric(query)
            if metrics:
                results[metric_name] = metrics[0].value
            else:
                results[metric_name] = None

        # Calculate derived metrics
        cpu_usage_percent = results["cpu_usage"] * 100 if results["cpu_usage"] else None

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
            service_name=instance.service_name,
            instance_id=instance.instance_id,
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

    def health_check(self) -> bool:
        """Check if Prometheus is accessible"""
        try:
            url = f"{self.base_url}/-/healthy"
            response = requests.get(url, timeout=5)
            return response.status_code == 200
        except:
            return False