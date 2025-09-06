from dataclasses import dataclass, field
from typing import Dict, List, Tuple, Any
import json

@dataclass
class QLearningConfig:
    """Q-Learning hyperparameters configuration"""
    learning_rate: float = 0.3
    discount_factor: float = 0.95
    epsilon_start: float = 0.25  # Reduced from 0.1 for more exploitation
    epsilon_min: float = 0.01   # Reduced from 0.02 for less exploration
    epsilon_decay: float = 0.99  # Faster decay from 0.995
    exploration_episodes: int = 100  # Reduced from 1000 for faster convergence
    
    # Production mode settings
    production_epsilon: float = 0.02  # Ultra-low exploration for benchmarks
    benchmark_mode: bool = False  # Flag to enable benchmark-optimized settings

@dataclass
class StateEncodingConfig:
    """State encoding and discretization configuration"""
    cpu_bins: int = 16
    memory_bins: int = 16
    latency_bins: int = 20
    error_rate_bins: int = 12
    throughput_bins: int = 16
    bin_strategy: str = "quantile"  # uniform, quantile, kmeans

@dataclass
class RewardConfig:
    """Reward function weights and parameters"""
    # Relative importance weights (will be normalized to sum to 1.0)
    latency_weight: float = 0.35        # 35% importance
    error_rate_weight: float = 0.35     # 35% importance  
    throughput_weight: float = 0.15     # 15% importance
    utilization_balance_weight: float = 0.10  # 10% importance
    stability_weight: float = 0.05      # 5% importance
    
    # Thresholds for reward calculation
    response_time_threshold_ms: float = 100.0  # Much more sensitive to latency changes
    error_rate_threshold: float = 0.05

@dataclass
class TrainingConfig:
    """Training loop configuration"""
    max_episodes: int = 500
    episode_length: int = 100
    batch_size: int = 64
    update_frequency: int = 10
    save_frequency: int = 50
    evaluation_frequency: int = 25

@dataclass
class RLAgentSettings:
    """Complete RL Agent settings"""
    q_learning: QLearningConfig = field(default_factory=QLearningConfig)
    state_encoding: StateEncodingConfig = field(default_factory=StateEncodingConfig)
    reward: RewardConfig = field(default_factory=RewardConfig)
    training: TrainingConfig = field(default_factory=TrainingConfig)

    # Integration settings
    metrics_collection_interval: float = 5.0
    action_execution_delay: float = 2.0

    # Persistence
    model_save_path: str = "models/q_table.pkl"
    logs_path: str = "logs/rl_agent.log"
    
    # Performance optimization settings
    enable_production_mode: bool = False  # Enable for benchmark/production
    adaptive_exploration: bool = True     # Enable adaptive epsilon adjustment

    def enable_benchmark_mode(self):
        """Enable benchmark-optimized settings for maximum performance"""
        self.q_learning.benchmark_mode = True
        self.enable_production_mode = True
        self.q_learning.epsilon_start = 0.02
        self.q_learning.epsilon_min = 0.01
        self.q_learning.epsilon_decay = 0.98
        
    def disable_benchmark_mode(self):
        """Restore learning-optimized settings"""
        self.q_learning.benchmark_mode = False
        self.enable_production_mode = False
        self.q_learning.epsilon_start = 0.1
        self.q_learning.epsilon_min = 0.02
        self.q_learning.epsilon_decay = 0.99
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for serialization"""
        return {
            'q_learning': self.q_learning.__dict__,
            'state_encoding': self.state_encoding.__dict__,
            'reward': self.reward.__dict__,
            'training': self.training.__dict__,
            'enable_production_mode': self.enable_production_mode,
            'adaptive_exploration': self.adaptive_exploration
        }

    @classmethod
    def from_dict(cls, config_dict: Dict[str, Any]) -> 'RLAgentSettings':
        """Create from dictionary"""
        settings = cls(
            q_learning=QLearningConfig(**config_dict.get('q_learning', {})),
            state_encoding=StateEncodingConfig(**config_dict.get('state_encoding', {})),
            reward=RewardConfig(**config_dict.get('reward', {})),
            training=TrainingConfig(**config_dict.get('training', {}))
        )
        settings.enable_production_mode = config_dict.get('enable_production_mode', False)
        settings.adaptive_exploration = config_dict.get('adaptive_exploration', True)
        return settings

# Global settings instance
rl_settings = RLAgentSettings()

# Alias for backward compatibility
RLConfig = RLAgentSettings