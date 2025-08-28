from typing import List, Dict, Any, Tuple
import numpy as np
from collections import defaultdict

from experience_loader import Experience
import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from models.metrics_model import ServiceMetrics
from utils.rl_logger import rl_logger

class DataPreprocessor:
    """Preprocesses experience data for training"""

    def __init__(self):
        self.service_names = set()
        self.action_to_idx = {}
        self.idx_to_action = {}

    def process_experiences(self, experiences: List[Experience]) -> Tuple[List[Tuple], Dict[str, Any]]:
        """
        Convert raw experiences into format suitable for Q-learning

        Returns:
            - List of (state, action_idx, reward, next_state) tuples
            - Metadata about the processing
        """
        processed_experiences = []
        invalid_count = 0

        # Build action mappings only if not already built
        if not self.action_to_idx:
            self._build_action_mappings(experiences)

        for exp in experiences:
            try:
                # Convert metrics dict to ServiceMetrics objects
                state_metrics = self._convert_to_service_metrics(exp.state, exp.timestamp)
                next_state_metrics = self._convert_to_service_metrics(exp.next_state, exp.timestamp)

                # Get action index
                action_idx = self.action_to_idx.get(exp.action)
                if action_idx is None:
                    invalid_count += 1
                    continue

                processed_experiences.append((
                    state_metrics,
                    action_idx,
                    exp.reward,
                    next_state_metrics
                ))

            except Exception as e:
                rl_logger.logger.warning(f"Failed to process experience: {e}")
                invalid_count += 1

        metadata = {
            'total_processed': len(processed_experiences),
            'invalid_experiences': invalid_count,
            'unique_actions': len(self.action_to_idx),
            'action_mappings': self.action_to_idx
        }

        rl_logger.logger.info(f"Processed {len(processed_experiences)} experiences, {invalid_count} invalid")
        return processed_experiences, metadata

    def _build_action_mappings(self, experiences: List[Experience]):
        """Build mappings between action names and indices"""
        unique_actions = set(exp.action for exp in experiences)
        self.action_to_idx = {action: idx for idx, action in enumerate(sorted(unique_actions))}
        self.idx_to_action = {idx: action for action, idx in self.action_to_idx.items()}

    def _convert_to_service_metrics(self, metrics_dict: Dict[str, Any], timestamp) -> List[ServiceMetrics]:
        """Convert metrics dictionary to ServiceMetrics objects"""
        service_metrics = []

        for service_name, metrics in metrics_dict.items():
            self.service_names.add(service_name)

            service_metric = ServiceMetrics(
                service_name=service_name.split('-')[0],  # Extract service type
                instance_id=service_name,
                pod_name=service_name,
                timestamp=timestamp,
                cpu_usage_percent=metrics.get('cpuUsagePercent', 0.0),
                jvm_memory_usage_percent=metrics.get('jvmMemoryUsagePercent', 0.0),
                uptime_seconds=metrics.get('uptimeSeconds', 0.0),
                request_rate_per_second=metrics.get('requestRatePerSecond', 0.0),
                avg_response_time_ms=metrics.get('avgResponseTimeMs', 0.0),
                error_rate_percent=metrics.get('errorRatePercent', 0.0),
                total_requests=0  # Not available in current data
            )
            service_metrics.append(service_metric)

        return service_metrics

    def process_experiences_generator(self, experiences):
        """
        Process experiences from a generator one by one, yielding processed tuples
        
        Args:
            experiences: Generator yielding Experience objects
            
        Yields:
            Tuples of (state_metrics, action_idx, reward, next_state_metrics)
        """
        for exp in experiences:
            try:
                # Convert metrics dict to ServiceMetrics objects
                state_metrics = self._convert_to_service_metrics(exp.state, exp.timestamp)
                next_state_metrics = self._convert_to_service_metrics(exp.next_state, exp.timestamp)

                # Get action index
                action_idx = self.action_to_idx.get(exp.action)
                if action_idx is None:
                    continue

                yield (
                    state_metrics,
                    action_idx,
                    exp.reward,
                    next_state_metrics
                )

            except Exception as e:
                rl_logger.logger.warning(f"Skipping experience due to processing error: {e}")
                continue
