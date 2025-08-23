"""
Prometheus Metrics Exporter for RL Agent
Exposes RL model performance metrics in Prometheus format
"""

from prometheus_client import Gauge, Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST
from prometheus_client.core import CollectorRegistry
from typing import Dict, Any
import time
from performance_metrics import performance_collector

class RLPrometheusExporter:
    """Exports RL model metrics to Prometheus"""
    
    def __init__(self):
        # Create custom registry to avoid conflicts
        self.registry = CollectorRegistry()
        
        # Model performance metrics
        self.rl_model_q_table_size = Gauge(
            'rl_model_q_table_size',
            'Size of the Q-learning table',
            registry=self.registry
        )
        
        self.rl_model_epsilon = Gauge(
            'rl_model_epsilon',
            'Current epsilon value for exploration',
            registry=self.registry
        )
        
        self.rl_model_episode_count = Counter(
            'rl_model_episode_count',
            'Total number of episodes completed',
            registry=self.registry
        )
        
        self.rl_model_avg_reward = Gauge(
            'rl_model_avg_reward',
            'Average reward over last 100 decisions',
            registry=self.registry
        )
        
        self.rl_model_convergence = Gauge(
            'rl_model_convergence',
            'Model convergence indicator (0-1)',
            registry=self.registry
        )
        
        self.rl_model_exploration_rate = Gauge(
            'rl_model_exploration_rate',
            'Actual exploration rate in recent decisions',
            registry=self.registry
        )
        
        self.rl_model_decisions_per_minute = Gauge(
            'rl_model_decisions_per_minute',
            'Number of decisions made per minute',
            registry=self.registry
        )
        
        self.rl_model_state_coverage = Gauge(
            'rl_model_state_coverage',
            'State space coverage (0-1)',
            registry=self.registry
        )
        
        # Performance metrics
        self.rl_performance_avg_response_time = Gauge(
            'rl_performance_avg_response_time',
            'Average response time in milliseconds',
            registry=self.registry
        )
        
        self.rl_performance_success_rate = Gauge(
            'rl_performance_success_rate',
            'Success rate of routing decisions (0-1)',
            registry=self.registry
        )
        
        # Decision metrics
        self.rl_decisions_total = Counter(
            'rl_decisions_total',
            'Total number of routing decisions made',
            ['service_name', 'decision_type'],
            registry=self.registry
        )
        
        self.rl_decision_time = Histogram(
            'rl_decision_time_seconds',
            'Time taken to make routing decisions',
            ['service_name'],
            registry=self.registry
        )
        
        # Feedback metrics
        self.rl_feedback_total = Counter(
            'rl_feedback_total',
            'Total feedback received',
            ['service_name', 'status_code_class'],
            registry=self.registry
        )
        
        self.rl_response_time = Histogram(
            'rl_response_time_seconds',
            'Response time of routed requests',
            ['service_name'],
            buckets=[0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0],
            registry=self.registry
        )
        
        self.rl_reward_value = Histogram(
            'rl_reward_value',
            'Reward values received',
            ['service_name'],
            buckets=[-2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0],
            registry=self.registry
        )
        
        # Service-specific metrics
        self.rl_service_decisions = Gauge(
            'rl_service_decisions_total',
            'Total decisions per service',
            ['service_name'],
            registry=self.registry
        )
        
        self.rl_service_success_rate = Gauge(
            'rl_service_success_rate',
            'Success rate per service',
            ['service_name'],
            registry=self.registry
        )
        
        self.rl_service_avg_response_time = Gauge(
            'rl_service_avg_response_time',
            'Average response time per service',
            ['service_name'],
            registry=self.registry
        )
        
        # Health metrics
        self.rl_model_health_score = Gauge(
            'rl_model_health_score',
            'Overall model health score (0-1)',
            registry=self.registry
        )
        
        self.rl_model_status = Gauge(
            'rl_model_status',
            'Model status (0=critical, 1=warning, 2=healthy)',
            registry=self.registry
        )
        
    def update_metrics(self, rl_agent=None):
        """Update all Prometheus metrics with current values"""
        try:
            # Create performance snapshot
            if rl_agent:
                snapshot = performance_collector.create_performance_snapshot(rl_agent)
                
                # Update model metrics
                self.rl_model_q_table_size.set(snapshot.q_table_size)
                self.rl_model_epsilon.set(snapshot.epsilon)
                self.rl_model_avg_reward.set(snapshot.avg_reward_last_100)
                self.rl_model_convergence.set(snapshot.convergence_indicator)
                self.rl_model_exploration_rate.set(snapshot.exploration_rate)
                self.rl_model_decisions_per_minute.set(snapshot.decisions_per_minute)
                self.rl_model_state_coverage.set(snapshot.state_space_coverage)
                
                # Update performance metrics
                self.rl_performance_avg_response_time.set(snapshot.avg_response_time_last_100)
                self.rl_performance_success_rate.set(snapshot.success_rate_last_100)
            
            # Update service-specific metrics
            service_performance = performance_collector.get_service_performance_summary()
            for service_name, metrics in service_performance.items():
                self.rl_service_decisions.labels(service_name=service_name).set(
                    metrics['total_decisions']
                )
                self.rl_service_success_rate.labels(service_name=service_name).set(
                    metrics['success_rate']
                )
                self.rl_service_avg_response_time.labels(service_name=service_name).set(
                    metrics['avg_response_time']
                )
            
            # Update health metrics
            health = performance_collector.get_model_health_indicators()
            if health.get('status') != 'no_data':
                self.rl_model_health_score.set(health.get('health_score', 0.0))
                
                # Convert status to numeric
                status_map = {'critical': 0, 'warning': 1, 'healthy': 2}
                status_value = status_map.get(health.get('status', 'critical'), 0)
                self.rl_model_status.set(status_value)
                
        except Exception as e:
            print(f"Error updating Prometheus metrics: {e}")
    
    def record_decision_metric(self, service_name: str, decision_type: str, decision_time_ms: float):
        """Record a routing decision metric"""
        self.rl_decisions_total.labels(
            service_name=service_name,
            decision_type=decision_type
        ).inc()
        
        self.rl_decision_time.labels(service_name=service_name).observe(
            decision_time_ms / 1000.0  # Convert to seconds
        )
    
    def record_feedback_metric(self, service_name: str, status_code: int, 
                             response_time_ms: float, reward: float):
        """Record feedback metrics"""
        # Determine status code class
        if 200 <= status_code < 300:
            status_class = "2xx"
        elif 300 <= status_code < 400:
            status_class = "3xx"
        elif 400 <= status_code < 500:
            status_class = "4xx"
        else:
            status_class = "5xx"
        
        self.rl_feedback_total.labels(
            service_name=service_name,
            status_code_class=status_class
        ).inc()
        
        self.rl_response_time.labels(service_name=service_name).observe(
            response_time_ms / 1000.0  # Convert to seconds
        )
        
        self.rl_reward_value.labels(service_name=service_name).observe(reward)
    
    def generate_metrics(self) -> str:
        """Generate Prometheus metrics in text format"""
        return generate_latest(self.registry).decode('utf-8')
    
    def get_content_type(self) -> str:
        """Get the content type for Prometheus metrics"""
        return CONTENT_TYPE_LATEST

# Global exporter instance
prometheus_exporter = RLPrometheusExporter()
