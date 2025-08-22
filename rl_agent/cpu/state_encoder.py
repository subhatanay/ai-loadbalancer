import numpy as np
from typing import Dict, List, Tuple, Any, Optional
from sklearn.preprocessing import KBinsDiscretizer
from sklearn.exceptions import NotFittedError
from sklearn.utils.validation import check_is_fitted
from collections import defaultdict
import pickle
from pathlib import Path

from models.metrics_model import ServiceMetrics
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

class StateEncoder:
    """
    Robust state encoder for microservices metrics.
    Handles safe, fallback binning for metrics with insufficient variation.
    """

    def __init__(self):
        self.config = rl_settings.state_encoding
        self.discretizers = {}
        self.is_fitted = False
        self.metrics_history = defaultdict(list)
        self.state_cache = {}

        # Define metric types and their bin configurations
        self.metric_bins = {
            'cpu_usage_percent': self.config.cpu_bins,
            'jvm_memory_usage_percent': self.config.memory_bins,
            'avg_response_time_ms': self.config.latency_bins,
            'error_rate_percent': self.config.error_rate_bins,
            'request_rate_per_second': self.config.throughput_bins
        }

        # Initialize discretizers
        self._initialize_discretizers()

    def _initialize_discretizers(self):
        """Initialize discretizers for each metric type"""
        for metric_name, n_bins in self.metric_bins.items():
            self.discretizers[metric_name] = KBinsDiscretizer(
                n_bins=n_bins,
                encode='ordinal',
                strategy=self.config.bin_strategy,
                dtype=np.float64
            )

    def fit(self, historical_metrics: List[ServiceMetrics]):
        """
        Fit discretizers on historical metrics data for optimal binning.
        Skips metrics with insufficient variance; logs appropriately.
        """
        rl_logger.logger.info(f"Fitting state encoder on {len(historical_metrics)} historical samples")

        metrics_data = defaultdict(list)

        for metric in historical_metrics:
            metrics_dict = self._extract_metrics_dict(metric)
            for metric_name, value in metrics_dict.items():
                if value is not None and metric_name in self.metric_bins:
                    metrics_data[metric_name].append(value)

        # For each metric, fit only if at least 2 unique values exist
        for metric_name, values in metrics_data.items():
            unique_values = set(values)
            num_unique = len(unique_values)
            if num_unique >= 2:
                n_bins = min(self.metric_bins[metric_name], num_unique)
                arr = np.array(values).reshape(-1, 1)
                self.discretizers[metric_name].n_bins = n_bins
                try:
                    self.discretizers[metric_name].fit(arr)
                    rl_logger.logger.info(
                        f"Fitted discretizer for {metric_name}: samples={len(values)}, unique={num_unique}, bins={n_bins}"
                    )
                except Exception as e:
                    rl_logger.log_error(f"Could not fit discretizer for {metric_name}: values={values}", e)
            else:
                rl_logger.logger.warning(f"Not enough unique values to fit discretizer for {metric_name}. Got {num_unique}. Will use fallback.")

        self.is_fitted = True
        rl_logger.logger.info("State encoder fitting completed")

    def encode_state(self, service_metrics: List[ServiceMetrics]) -> Tuple[np.float64, ...]:
        """
        Encode current service metrics into discrete state representation.
        Uses fallback middle bin if metric's discretizer is not fitted.
        """
        if not self.is_fitted:
            rl_logger.logger.warning("State encoder not fitted. Using default binning.")

        state_key = self._create_cache_key(service_metrics)
        if state_key in self.state_cache:
            return self.state_cache[state_key]

        aggregated_metrics = self._aggregate_service_metrics(service_metrics)
        state_components = []

        for metric_name in sorted(self.metric_bins.keys()):
            value = aggregated_metrics.get(metric_name)

            if value is not None and metric_name in self.discretizers:
                try:
                    # Check if discretizer is fitted
                    check_is_fitted(self.discretizers[metric_name])
                    discretized = self.discretizers[metric_name].transform([[value]])[0, 0]
                    state_components.append(np.float64(discretized))
                except NotFittedError:
                    fallback = self.metric_bins[metric_name] // 2
                    rl_logger.logger.warning(f"Discretizer for {metric_name} not fitted. Using fallback bin {fallback}. Value: {value}")
                    state_components.append(fallback)
                except Exception as e:
                    fallback = self.metric_bins[metric_name] // 2
                    rl_logger.log_error(f"Error discretizing {metric_name}: {value}, falling back to bin {fallback}", e)
                    state_components.append(fallback)
            else:
                # Use middle bin for missing values
                fallback = self.metric_bins[metric_name] // 2
                rl_logger.logger.debug(f"No value for {metric_name}. Using fallback bin {fallback}")
                state_components.append(fallback)

        # Add service count and load distribution metrics
        state_components.extend(self._encode_system_state(service_metrics))

        encoded_state = tuple(state_components)
        self.state_cache[state_key] = encoded_state
        rl_logger.log_state_encoding(aggregated_metrics, encoded_state)

        return encoded_state

    # Remainder of class is unchanged (utility methods, aggregation)

    def _extract_metrics_dict(self, metric: ServiceMetrics) -> Dict[str, Optional[float]]:
        return {
            'cpu_usage_percent': metric.cpu_usage_percent,
            'jvm_memory_usage_percent': metric.jvm_memory_usage_percent,
            'avg_response_time_ms': metric.avg_response_time_ms,
            'error_rate_percent': metric.error_rate_percent,
            'request_rate_per_second': metric.request_rate_per_second
        }

    def _aggregate_service_metrics(self, service_metrics: List[ServiceMetrics]) -> Dict[str, float]:
        if not service_metrics:
            return {metric: 0.0 for metric in self.metric_bins.keys()}
        aggregated = {}
        for metric_name in self.metric_bins.keys():
            values = [getattr(m, metric_name, None) for m in service_metrics if getattr(m, metric_name, None) is not None]
            if values:
                if 'error_rate' in metric_name:
                    aggregated[metric_name] = np.mean(values)
                elif 'response_time' in metric_name or 'latency' in metric_name:
                    aggregated[metric_name] = np.percentile(values, 95)
                elif 'usage' in metric_name:
                    aggregated[metric_name] = np.max(values)
                else:
                    aggregated[metric_name] = np.mean(values)
            else:
                aggregated[metric_name] = 0.0
        return aggregated

    def _encode_system_state(self, service_metrics: List[ServiceMetrics]) -> List[np.float64]:
        if not service_metrics:
            return [0, 0]
        service_count = len(service_metrics)
        service_count_bin = min(service_count // 2, 3)
        cpu_values = [m.cpu_usage_percent for m in service_metrics if m.cpu_usage_percent is not None]
        if len(cpu_values) > 1:
            variance = np.var(cpu_values)
            variance_bin = 0 if variance < 100 else (1 if variance < 400 else 2)
        else:
            variance_bin = 0
        return [service_count_bin, variance_bin]

    def _create_cache_key(self, service_metrics: List[ServiceMetrics]) -> str:
        if not service_metrics:
            return "empty_state"
        key_parts = []
        for metric in sorted(service_metrics, key=lambda x: x.instance_id):
            key_parts.append(
                f"{metric.instance_id}:"
                f"{metric.cpu_usage_percent or 0:.1f}:"
                f"{metric.avg_response_time_ms or 0:.1f}:"
                f"{metric.error_rate_percent or 0:.2f}"
            )
        return "|".join(key_parts)

    def get_state_space_size(self) -> int:
        size = 1
        for n_bins in self.metric_bins.values():
            size *= n_bins
        size *= 4 * 3
        return size

    def save_discretizers(self, path: str):
        save_path = Path(path)
        save_path.parent.mkdir(parents=True, exist_ok=True)
        with open(save_path, 'wb') as f:
            pickle.dump({'discretizers': self.discretizers,
                         'is_fitted': self.is_fitted,
                         'metric_bins': self.metric_bins}, f)
        rl_logger.logger.info(f"State encoder saved to {save_path}")

    def load_discretizers(self, path: str):
        try:
            with open(path, 'rb') as f:
                data = pickle.load(f)
                self.discretizers = data['discretizers']
                self.is_fitted = data['is_fitted']
                self.metric_bins = data['metric_bins']
            rl_logger.logger.info(f"State encoder loaded from {path}")
        except Exception as e:
            rl_logger.log_error(f"Failed to load state encoder from {path}", e)
