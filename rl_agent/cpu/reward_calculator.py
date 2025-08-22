import numpy as np
from typing import Dict, List, Tuple, Any
from datetime import datetime

from models.metrics_model import ServiceMetrics
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

class RewardCalculator:
    """
    Multi-objective reward function calculator for load balancing optimization.
    Balances latency, error rates, throughput, and resource utilization.
    """

    def __init__(self):
        self.config = rl_settings.reward
        self.baseline_metrics = {}
        self.reward_history = []

    def calculate_reward(self,
                         current_metrics: List[ServiceMetrics],
                         previous_metrics: List[ServiceMetrics],
                         action_taken: str) -> float:
        """
        Calculate reward based on current system state and previous action.

        Args:
            current_metrics: Current service metrics
            previous_metrics: Previous service metrics (before action)
            action_taken: Action that was taken

        Returns:
            Calculated reward value
        """
        # Handle edge cases
        if not current_metrics:
            return -10.0  # Heavy penalty for no metrics

        # Calculate individual reward components
        reward_components = {}

        # 1. Latency component (lower is better)
        reward_components['latency'] = self._calculate_latency_reward(
            current_metrics, previous_metrics
        )

        # 2. Error rate component (lower is better)
        reward_components['error_rate'] = self._calculate_error_reward(
            current_metrics, previous_metrics
        )

        # 3. Throughput component (higher is better)
        reward_components['throughput'] = self._calculate_throughput_reward(
            current_metrics, previous_metrics
        )

        # 4. Load balancing component (more balanced is better)
        reward_components['load_balance'] = self._calculate_balance_reward(
            current_metrics
        )

        # 5. System stability component
        reward_components['stability'] = self._calculate_stability_reward(
            current_metrics, previous_metrics
        )

        # Calculate weighted total reward
        total_reward = (
                self.config.latency_weight * reward_components['latency'] +
                self.config.error_rate_weight * reward_components['error_rate'] +
                self.config.throughput_weight * reward_components['throughput'] +
                self.config.utilization_balance_weight * reward_components['load_balance'] +
                0.5 * reward_components['stability']  # Stability weight
        )

        # Apply action-specific modifiers
        total_reward = self._apply_action_modifiers(
            total_reward, action_taken, current_metrics
        )

        # Log reward calculation
        rl_logger.log_reward_calculation(reward_components, total_reward)

        # Store in history
        self.reward_history.append({
            'timestamp': datetime.now(),
            'total_reward': total_reward,
            'components': reward_components,
            'action': action_taken
        })

        return total_reward

    def _calculate_latency_reward(self,
                                  current_metrics: List[ServiceMetrics],
                                  previous_metrics: List[ServiceMetrics]) -> float:
        """Calculate reward component based on response time"""
        current_latencies = [
            m.avg_response_time_ms for m in current_metrics
            if m.avg_response_time_ms is not None
        ]

        if not current_latencies:
            return 0.0

        avg_latency = np.mean(current_latencies)
        max_latency = np.max(current_latencies)

        # Normalize by threshold
        normalized_avg = avg_latency / self.config.response_time_threshold_ms
        normalized_max = max_latency / self.config.response_time_threshold_ms

        # Reward: negative for high latency, with exponential penalty for very high latency
        latency_reward = -normalized_avg - 0.5 * normalized_max

        # Extra penalty for exceeding threshold
        if max_latency > self.config.response_time_threshold_ms:
            latency_reward -= 2.0 * (normalized_max - 1.0)

        # Improvement bonus if we have previous metrics
        if previous_metrics:
            prev_latencies = [
                m.avg_response_time_ms for m in previous_metrics
                if m.avg_response_time_ms is not None
            ]
            if prev_latencies:
                prev_avg = np.mean(prev_latencies)
                improvement = (prev_avg - avg_latency) / prev_avg
                latency_reward += improvement * 0.5  # Bonus for improvement

        return latency_reward

    def _calculate_error_reward(self,
                                current_metrics: List[ServiceMetrics],
                                previous_metrics: List[ServiceMetrics]) -> float:
        """Calculate reward component based on error rates"""
        current_errors = [
            m.error_rate_percent for m in current_metrics
            if m.error_rate_percent is not None
        ]

        if not current_errors:
            return 0.0

        avg_error_rate = np.mean(current_errors) / 100.0  # Convert to decimal
        max_error_rate = np.max(current_errors) / 100.0

        # Heavy penalty for any errors, exponential for high error rates
        error_reward = -10.0 * avg_error_rate - 20.0 * max_error_rate

        # Extra penalty for exceeding threshold
        if max_error_rate > self.config.error_rate_threshold:
            error_reward -= 50.0 * (max_error_rate - self.config.error_rate_threshold)

        # Improvement bonus
        if previous_metrics:
            prev_errors = [
                m.error_rate_percent for m in previous_metrics
                if m.error_rate_percent is not None
            ]
            if prev_errors:
                prev_avg = np.mean(prev_errors) / 100.0
                if prev_avg > 0:
                    improvement = (prev_avg - avg_error_rate) / prev_avg
                    error_reward += improvement * 2.0

        return error_reward

    def _calculate_throughput_reward(self,
                                     current_metrics: List[ServiceMetrics],
                                     previous_metrics: List[ServiceMetrics]) -> float:
        """Calculate reward component based on throughput"""
        current_throughput = [
            m.request_rate_per_second for m in current_metrics
            if m.request_rate_per_second is not None
        ]

        if not current_throughput:
            return 0.0

        total_throughput = np.sum(current_throughput)
        avg_throughput = np.mean(current_throughput)

        # Reward based on total and average throughput
        throughput_reward = np.log1p(total_throughput) + 0.5 * np.log1p(avg_throughput)

        # Bonus for consistent throughput across services
        if len(current_throughput) > 1:
            throughput_std = np.std(current_throughput)
            throughput_mean = np.mean(current_throughput)
            if throughput_mean > 0:
                cv = throughput_std / throughput_mean  # Coefficient of variation
                consistency_bonus = max(0, 1.0 - cv)  # Bonus for low variation
                throughput_reward += consistency_bonus

        # Improvement bonus
        if previous_metrics:
            prev_throughput = [
                m.request_rate_per_second for m in previous_metrics
                if m.request_rate_per_second is not None
            ]
            if prev_throughput:
                prev_total = np.sum(prev_throughput)
                if prev_total > 0:
                    improvement = (total_throughput - prev_total) / prev_total
                    throughput_reward += improvement * 1.0

        return throughput_reward

    def _calculate_balance_reward(self, current_metrics: List[ServiceMetrics]) -> float:
        """Calculate reward component based on load balancing"""
        if len(current_metrics) < 2:
            return 0.0  # Cannot balance single service

        # Extract CPU and memory utilization
        cpu_utilizations = [
            m.cpu_usage_percent for m in current_metrics
            if m.cpu_usage_percent is not None
        ]

        memory_utilizations = [
            m.jvm_memory_usage_percent for m in current_metrics
            if m.jvm_memory_usage_percent is not None
        ]

        balance_reward = 0.0

        # CPU balance reward
        if len(cpu_utilizations) > 1:
            cpu_variance = np.var(cpu_utilizations)
            cpu_mean = np.mean(cpu_utilizations)

            # Normalize variance by mean to handle different scales
            normalized_cpu_variance = cpu_variance / (cpu_mean + 1e-6)
            balance_reward -= normalized_cpu_variance / 100.0  # Penalty for imbalance

            # Bonus for keeping utilization in optimal range (30-70%)
            optimal_range_bonus = sum(
                1.0 for util in cpu_utilizations if 30 <= util <= 70
            ) / len(cpu_utilizations)
            balance_reward += optimal_range_bonus

        # Memory balance reward
        if len(memory_utilizations) > 1:
            mem_variance = np.var(memory_utilizations)
            mem_mean = np.mean(memory_utilizations)

            normalized_mem_variance = mem_variance / (mem_mean + 1e-6)
            balance_reward -= normalized_mem_variance / 100.0

        # Request rate balance
        request_rates = [
            m.request_rate_per_second for m in current_metrics
            if m.request_rate_per_second is not None
        ]

        if len(request_rates) > 1:
            rate_variance = np.var(request_rates)
            rate_mean = np.mean(request_rates)

            if rate_mean > 0:
                normalized_rate_variance = rate_variance / rate_mean
                balance_reward -= normalized_rate_variance / 10.0

        return balance_reward

    def _calculate_stability_reward(self,
                                    current_metrics: List[ServiceMetrics],
                                    previous_metrics: List[ServiceMetrics]) -> float:
        """Calculate reward component based on system stability"""
        if not previous_metrics:
            return 0.0

        stability_reward = 0.0

        # Compare service availability
        current_services = {m.instance_id for m in current_metrics}
        previous_services = {m.instance_id for m in previous_metrics}

        # Penalty for service unavailability
        disappeared_services = previous_services - current_services
        stability_reward -= len(disappeared_services) * 5.0

        # Bonus for service consistency
        stable_services = current_services & previous_services
        stability_reward += len(stable_services) * 0.1

        # Penalty for high metric volatility
        for current_metric in current_metrics:
            for prev_metric in previous_metrics:
                if current_metric.instance_id == prev_metric.instance_id:
                    # Calculate volatility penalty
                    volatility = self._calculate_metric_volatility(
                        current_metric, prev_metric
                    )
                    stability_reward -= volatility
                    break

        return stability_reward

    def _calculate_metric_volatility(self,
                                     current: ServiceMetrics,
                                     previous: ServiceMetrics) -> float:
        """Calculate volatility between two metric snapshots"""
        volatility = 0.0

        # CPU volatility
        if (current.cpu_usage_percent is not None and
                previous.cpu_usage_percent is not None):
            cpu_change = abs(current.cpu_usage_percent - previous.cpu_usage_percent)
            volatility += cpu_change / 100.0

        # Response time volatility
        if (current.avg_response_time_ms is not None and
                previous.avg_response_time_ms is not None):
            time_change = abs(current.avg_response_time_ms - previous.avg_response_time_ms)
            volatility += time_change / self.config.response_time_threshold_ms

        return volatility

    def _apply_action_modifiers(self,
                                base_reward: float,
                                action: str,
                                current_metrics: List[ServiceMetrics]) -> float:
        """Apply action-specific reward modifiers"""
        modified_reward = base_reward

        # Find the target service metrics
        target_service = None
        for metric in current_metrics:
            if action in metric.instance_id:
                target_service = metric
                break

        if target_service:
            # Penalty for routing to overloaded services
            if (target_service.cpu_usage_percent and
                    target_service.cpu_usage_percent > 80):
                modified_reward -= 2.0

            # Penalty for routing to services with high error rates
            if (target_service.error_rate_percent and
                    target_service.error_rate_percent > 5.0):
                modified_reward -= 3.0

            # Bonus for routing to underutilized services
            if (target_service.cpu_usage_percent and
                    target_service.cpu_usage_percent < 30):
                modified_reward += 1.0

        return modified_reward

    def get_reward_statistics(self) -> Dict[str, Any]:
        """Get statistics about reward history"""
        if not self.reward_history:
            return {}

        recent_rewards = self.reward_history[-100:]  # Last 100 rewards

        return {
            'total_episodes': len(self.reward_history),
            'recent_average': np.mean([r['total_reward'] for r in recent_rewards]),
            'recent_std': np.std([r['total_reward'] for r in recent_rewards]),
            'best_reward': max(r['total_reward'] for r in self.reward_history),
            'worst_reward': min(r['total_reward'] for r in self.reward_history),
            'improvement_trend': self._calculate_improvement_trend()
        }

    def _calculate_improvement_trend(self) -> float:
        """Calculate improvement trend over recent episodes"""
        if len(self.reward_history) < 20:
            return 0.0

        recent_rewards = [r['total_reward'] for r in self.reward_history[-20:]]
        older_rewards = [r['total_reward'] for r in self.reward_history[-40:-20]]

        if not older_rewards:
            return 0.0

        recent_avg = np.mean(recent_rewards)
        older_avg = np.mean(older_rewards)

        return (recent_avg - older_avg) / (abs(older_avg) + 1e-6)
