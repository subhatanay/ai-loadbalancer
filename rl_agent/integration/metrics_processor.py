from typing import List, Dict, Any, Optional
import numpy as np
from datetime import datetime, timedelta

from models.metrics_model import ServiceMetrics
from utils.rl_logger import rl_logger

class MetricsProcessor:
    """
    Advanced metrics processing for RL agent.
    Handles data validation, normalization, and feature engineering.
    """

    def __init__(self):
        self.metrics_cache = {}
        self.baseline_metrics = {}
        self.anomaly_threshold = 3.0  # Standard deviations

    def process_metrics(self, raw_metrics: List[ServiceMetrics]) -> List[ServiceMetrics]:
        """
        Process and validate raw metrics.

        Args:
            raw_metrics: Raw metrics from Prometheus

        Returns:
            Processed and validated metrics
        """
        if not raw_metrics:
            return []

        processed_metrics = []

        for metric in raw_metrics:
            try:
                # Validate and clean metric
                cleaned_metric = self._clean_metric(metric)

                # Detect anomalies
                if not self._is_anomalous(cleaned_metric):
                    processed_metrics.append(cleaned_metric)
                else:
                    rl_logger.logger.warning(
                        f"Anomalous metric detected for {metric.instance_id}, skipping"
                    )

            except Exception as e:
                rl_logger.log_error(f"Failed to process metric for {metric.instance_id}", e)

        # Update baselines
        self._update_baselines(processed_metrics)

        rl_logger.log_metrics_collected(len(processed_metrics),
                                        [m.instance_id for m in processed_metrics])

        return processed_metrics

    def _clean_metric(self, metric: ServiceMetrics) -> ServiceMetrics:
        """Clean and validate individual metric"""
        # Handle None values and outliers
        cpu_usage = self._clean_percentage(metric.cpu_usage_percent)
        memory_usage = self._clean_percentage(metric.jvm_memory_usage_percent)

        # Clean response time
        response_time = metric.avg_response_time_ms
        if response_time is not None:
            response_time = max(0, min(response_time, 30000))  # Cap at 30 seconds

        # Clean error rate
        error_rate = self._clean_percentage(metric.error_rate_percent)

        # Clean request rate
        request_rate = metric.request_rate_per_second
        if request_rate is not None:
            request_rate = max(0, request_rate)

        # Create cleaned metric
        return ServiceMetrics(
            service_name=metric.service_name,
            instance_id=metric.instance_id,
            pod_name=metric.pod_name,
            timestamp=metric.timestamp,
            cpu_usage_percent=cpu_usage,
            jvm_memory_usage_percent=memory_usage,
            uptime_seconds=metric.uptime_seconds,
            request_rate_per_second=request_rate,
            avg_response_time_ms=response_time,
            error_rate_percent=error_rate,
            total_requests=metric.total_requests
        )

    def _clean_percentage(self, value: Optional[float]) -> Optional[float]:
        """Clean percentage values (0-100)"""
        if value is None:
            return None
        return max(0.0, min(100.0, value))

    def _is_anomalous(self, metric: ServiceMetrics) -> bool:
        """Detect anomalous metrics using statistical methods"""
        instance_id = metric.instance_id

        # Get baseline for this instance
        baseline = self.baseline_metrics.get(instance_id, {})

        if not baseline:
            return False  # No baseline yet

        # Check each metric for anomalies
        checks = [
            ('cpu_usage_percent', metric.cpu_usage_percent),
            ('jvm_memory_usage_percent', metric.jvm_memory_usage_percent),
            ('avg_response_time_ms', metric.avg_response_time_ms),
            ('request_rate_per_second', metric.request_rate_per_second)
        ]

        anomaly_count = 0

        for metric_name, value in checks:
            if value is None:
                continue

            baseline_stats = baseline.get(metric_name, {})
            if not baseline_stats:
                continue

            mean = baseline_stats.get('mean', 0)
            std = baseline_stats.get('std', 0)

            if std > 0:
                z_score = abs(value - mean) / std
                if z_score > self.anomaly_threshold:
                    anomaly_count += 1

        # Consider anomalous if multiple metrics are outliers
        return anomaly_count >= 2

    def _update_baselines(self, metrics: List[ServiceMetrics]):
        """Update baseline statistics for anomaly detection"""
        for metric in metrics:
            instance_id = metric.instance_id

            if instance_id not in self.baseline_metrics:
                self.baseline_metrics[instance_id] = {}

            baseline = self.baseline_metrics[instance_id]

            # Update statistics for each metric
            metric_values = {
                'cpu_usage_percent': metric.cpu_usage_percent,
                'jvm_memory_usage_percent': metric.jvm_memory_usage_percent,
                'avg_response_time_ms': metric.avg_response_time_ms,
                'request_rate_per_second': metric.request_rate_per_second
            }

            for metric_name, value in metric_values.items():
                if value is None:
                    continue

                if metric_name not in baseline:
                    baseline[metric_name] = {'values': [], 'mean': 0, 'std': 0}

                # Add value and maintain sliding window
                baseline[metric_name]['values'].append(value)
                if len(baseline[metric_name]['values']) > 100:
                    baseline[metric_name]['values'].pop(0)

                # Update statistics
                values = baseline[metric_name]['values']
                baseline[metric_name]['mean'] = np.mean(values)
                baseline[metric_name]['std'] = np.std(values)

    def get_metrics_summary(self, metrics: List[ServiceMetrics]) -> Dict[str, Any]:
        """Get summary statistics for metrics"""
        if not metrics:
            return {}

        # Aggregate by metric type
        cpu_values = [m.cpu_usage_percent for m in metrics if m.cpu_usage_percent is not None]
        memory_values = [m.jvm_memory_usage_percent for m in metrics if m.jvm_memory_usage_percent is not None]
        latency_values = [m.avg_response_time_ms for m in metrics if m.avg_response_time_ms is not None]
        error_values = [m.error_rate_percent for m in metrics if m.error_rate_percent is not None]
        throughput_values = [m.request_rate_per_second for m in metrics if m.request_rate_per_second is not None]

        summary = {
            'total_services': len(metrics),
            'timestamp': datetime.now().isoformat()
        }

        # CPU statistics
        if cpu_values:
            summary['cpu'] = {
                'mean': np.mean(cpu_values),
                'max': np.max(cpu_values),
                'min': np.min(cpu_values),
                'std': np.std(cpu_values)
            }

        # Memory statistics
        if memory_values:
            summary['memory'] = {
                'mean': np.mean(memory_values),
                'max': np.max(memory_values),
                'min': np.min(memory_values)
            }

        # Latency statistics
        if latency_values:
            summary['latency'] = {
                'mean': np.mean(latency_values),
                'p95': np.percentile(latency_values, 95),
                'p99': np.percentile(latency_values, 99),
                'max': np.max(latency_values)
            }

        # Error rate statistics
        if error_values:
            summary['errors'] = {
                'mean': np.mean(error_values),
                'max': np.max(error_values),
                'services_with_errors': sum(1 for e in error_values if e > 0)
            }

        # Throughput statistics
        if throughput_values:
            summary['throughput'] = {
                'total': np.sum(throughput_values),
                'mean': np.mean(throughput_values),
                'max': np.max(throughput_values)
            }

        return summary
