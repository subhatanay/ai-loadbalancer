"""
RL Model Performance Metrics Collection
Tracks and exposes comprehensive metrics about RL model performance
"""

import time
from typing import Dict, List, Optional, Any
from collections import deque, defaultdict
from datetime import datetime, timedelta
import numpy as np
from dataclasses import dataclass, asdict
import json

@dataclass
class DecisionMetrics:
    """Metrics for a single routing decision"""
    timestamp: datetime
    service_name: str
    selected_pod: str
    available_pods: List[str]
    decision_time_ms: float
    confidence: float
    decision_type: str  # exploration/exploitation
    q_value: float
    epsilon: float
    state_encoded: str
    
@dataclass
class FeedbackMetrics:
    """Metrics from feedback about decision outcomes"""
    timestamp: datetime
    service_name: str
    selected_pod: str
    response_time_ms: float
    status_code: int
    error_occurred: bool
    reward: float
    q_value_updated: float

@dataclass
class ModelPerformanceSnapshot:
    """Snapshot of model performance at a point in time"""
    timestamp: datetime
    q_table_size: int
    epsilon: float
    episode_count: int
    avg_reward_last_100: float
    avg_response_time_last_100: float
    success_rate_last_100: float
    exploration_rate: float
    decisions_per_minute: float
    state_space_coverage: float
    convergence_indicator: float

class PerformanceMetricsCollector:
    """Collects and tracks RL model performance metrics"""
    
    def __init__(self, max_history_size: int = 10000):
        self.max_history_size = max_history_size
        
        # Decision and feedback history
        self.decision_history = deque(maxlen=max_history_size)
        self.feedback_history = deque(maxlen=max_history_size)
        
        # Performance snapshots
        self.performance_snapshots = deque(maxlen=1000)
        
        # Real-time metrics
        self.decisions_per_service = defaultdict(int)
        self.success_rate_per_service = defaultdict(list)
        self.response_times_per_service = defaultdict(list)
        
        # Model learning metrics
        self.q_value_changes = deque(maxlen=1000)
        self.reward_trends = deque(maxlen=1000)
        
        # Performance indicators
        self.last_snapshot_time = datetime.now()
        self.snapshot_interval = timedelta(minutes=1)
        
    def record_decision(self, 
                       service_name: str,
                       selected_pod: str,
                       available_pods: List[str],
                       decision_time_ms: float,
                       confidence: float,
                       decision_type: str,
                       q_value: float,
                       epsilon: float,
                       state_encoded: str):
        """Record a routing decision"""
        
        decision = DecisionMetrics(
            timestamp=datetime.now(),
            service_name=service_name,
            selected_pod=selected_pod,
            available_pods=available_pods.copy(),
            decision_time_ms=decision_time_ms,
            confidence=confidence,
            decision_type=decision_type,
            q_value=q_value,
            epsilon=epsilon,
            state_encoded=state_encoded
        )
        
        self.decision_history.append(decision)
        self.decisions_per_service[service_name] += 1
        
    def record_feedback(self,
                       service_name: str,
                       selected_pod: str,
                       response_time_ms: float,
                       status_code: int,
                       error_occurred: bool,
                       reward: float,
                       q_value_updated: float):
        """Record feedback about a decision outcome"""
        
        feedback = FeedbackMetrics(
            timestamp=datetime.now(),
            service_name=service_name,
            selected_pod=selected_pod,
            response_time_ms=response_time_ms,
            status_code=status_code,
            error_occurred=error_occurred,
            reward=reward,
            q_value_updated=q_value_updated
        )
        
        self.feedback_history.append(feedback)
        
        # Update service-specific metrics
        self.response_times_per_service[service_name].append(response_time_ms)
        success = not error_occurred and 200 <= status_code < 300
        self.success_rate_per_service[service_name].append(success)
        
        # Track reward trends
        self.reward_trends.append(reward)
        
        # Keep only recent data for service metrics
        if len(self.response_times_per_service[service_name]) > 1000:
            self.response_times_per_service[service_name] = \
                self.response_times_per_service[service_name][-1000:]
        if len(self.success_rate_per_service[service_name]) > 1000:
            self.success_rate_per_service[service_name] = \
                self.success_rate_per_service[service_name][-1000:]
    
    def create_performance_snapshot(self, rl_agent) -> ModelPerformanceSnapshot:
        """Create a performance snapshot from current state"""
        
        now = datetime.now()
        
        # Calculate metrics from recent history
        recent_feedback = [f for f in self.feedback_history 
                          if (now - f.timestamp).total_seconds() < 6000]  # Last 100 minutes
        
        avg_reward = np.mean([f.reward for f in recent_feedback]) if recent_feedback else 0.0
        avg_response_time = np.mean([f.response_time_ms for f in recent_feedback]) if recent_feedback else 0.0
        success_rate = np.mean([not f.error_occurred and 200 <= f.status_code < 300 
                               for f in recent_feedback]) if recent_feedback else 0.0
        
        # Calculate decisions per minute
        recent_decisions = [d for d in self.decision_history 
                           if (now - d.timestamp).total_seconds() < 60]
        decisions_per_minute = len(recent_decisions)
        
        # Calculate exploration rate
        exploration_decisions = [d for d in recent_decisions if d.decision_type == "exploration"]
        exploration_rate = len(exploration_decisions) / len(recent_decisions) if recent_decisions else 0.0
        
        # State space coverage (approximate)
        unique_states = len(set(d.state_encoded for d in self.decision_history))
        state_space_coverage = min(1.0, unique_states / 1000.0)  # Normalize to 0-1
        
        # Convergence indicator (based on Q-value stability)
        convergence_indicator = self._calculate_convergence_indicator()
        
        snapshot = ModelPerformanceSnapshot(
            timestamp=now,
            q_table_size=len(rl_agent.q_table) if rl_agent else 0,
            epsilon=rl_agent.current_epsilon if rl_agent else 0.0,
            episode_count=rl_agent.episode_count if rl_agent else 0,
            avg_reward_last_100=avg_reward,
            avg_response_time_last_100=avg_response_time,
            success_rate_last_100=success_rate,
            exploration_rate=exploration_rate,
            decisions_per_minute=decisions_per_minute,
            state_space_coverage=state_space_coverage,
            convergence_indicator=convergence_indicator
        )
        
        self.performance_snapshots.append(snapshot)
        self.last_snapshot_time = now
        
        return snapshot
    
    def _calculate_convergence_indicator(self) -> float:
        """Calculate how well the model is converging (0-1 scale)"""
        if len(self.reward_trends) < 50:
            return 0.0
        
        # Look at reward stability over recent episodes
        recent_rewards = list(self.reward_trends)[-50:]
        reward_std = np.std(recent_rewards)
        reward_mean = np.mean(recent_rewards)
        
        # Lower variance indicates better convergence
        if reward_mean > 0:
            coefficient_of_variation = reward_std / abs(reward_mean)
            convergence = max(0.0, 1.0 - coefficient_of_variation)
        else:
            convergence = 0.0
        
        return min(1.0, convergence)
    
    def get_service_performance_summary(self) -> Dict[str, Any]:
        """Get performance summary by service"""
        summary = {}
        
        for service_name in self.decisions_per_service.keys():
            response_times = self.response_times_per_service.get(service_name, [])
            success_rates = self.success_rate_per_service.get(service_name, [])
            
            summary[service_name] = {
                "total_decisions": self.decisions_per_service[service_name],
                "avg_response_time": np.mean(response_times) if response_times else 0.0,
                "success_rate": np.mean(success_rates) if success_rates else 0.0,
                "recent_decisions": len([d for d in self.decision_history 
                                       if d.service_name == service_name and 
                                       (datetime.now() - d.timestamp).total_seconds() < 300])
            }
        
        return summary
    
    def get_model_health_indicators(self) -> Dict[str, Any]:
        """Get key health indicators for the RL model"""
        if not self.performance_snapshots:
            return {"status": "no_data"}
        
        latest = self.performance_snapshots[-1]
        
        # Determine health status
        health_score = 0.0
        issues = []
        
        # Check success rate
        if latest.success_rate_last_100 > 0.95:
            health_score += 0.3
        elif latest.success_rate_last_100 > 0.9:
            health_score += 0.2
        else:
            issues.append(f"Low success rate: {latest.success_rate_last_100:.2%}")
        
        # Check response time
        if latest.avg_response_time_last_100 < 100:
            health_score += 0.2
        elif latest.avg_response_time_last_100 < 500:
            health_score += 0.1
        else:
            issues.append(f"High response time: {latest.avg_response_time_last_100:.1f}ms")
        
        # Check convergence
        if latest.convergence_indicator > 0.8:
            health_score += 0.3
        elif latest.convergence_indicator > 0.6:
            health_score += 0.2
        else:
            issues.append(f"Poor convergence: {latest.convergence_indicator:.2f}")
        
        # Check decision rate
        if latest.decisions_per_minute > 0:
            health_score += 0.2
        else:
            issues.append("No recent decisions")
        
        # Determine status
        if health_score >= 0.8:
            status = "healthy"
        elif health_score >= 0.5:
            status = "warning"
        else:
            status = "critical"
        
        return {
            "status": status,
            "health_score": health_score,
            "issues": issues,
            "latest_metrics": asdict(latest)
        }
    
    def export_metrics_for_prometheus(self) -> Dict[str, float]:
        """Export metrics in Prometheus format"""
        if not self.performance_snapshots:
            return {}
        
        latest = self.performance_snapshots[-1]
        service_summary = self.get_service_performance_summary()
        
        metrics = {
            # Model metrics
            "rl_model_q_table_size": latest.q_table_size,
            "rl_model_epsilon": latest.epsilon,
            "rl_model_episode_count": latest.episode_count,
            "rl_model_avg_reward": latest.avg_reward_last_100,
            "rl_model_convergence": latest.convergence_indicator,
            "rl_model_exploration_rate": latest.exploration_rate,
            "rl_model_decisions_per_minute": latest.decisions_per_minute,
            "rl_model_state_coverage": latest.state_space_coverage,
            
            # Performance metrics
            "rl_performance_avg_response_time": latest.avg_response_time_last_100,
            "rl_performance_success_rate": latest.success_rate_last_100,
            
            # Service-specific metrics
            "rl_total_services": len(service_summary),
            "rl_total_decisions": sum(s["total_decisions"] for s in service_summary.values()),
        }
        
        return metrics

# Global instance
performance_collector = PerformanceMetricsCollector()
