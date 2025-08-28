from dataclasses import dataclass, field
from typing import Dict, List, Tuple, Any
import json

@dataclass
class QLearningConfig:
    """Q-Learning hyperparameters configuration"""
    learning_rate: float = 0.3
    discount_factor: float = 0.95
    epsilon_start: float = 1.0
    epsilon_min: float = 0.05
    epsilon_decay: float = 0.995
    exploration_episodes: int = 1000

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
    latency_weight: float = -1.0
    error_rate_weight: float = -5.0
    throughput_weight: float = 0.2
    utilization_balance_weight: float = -2.0
    response_time_threshold_ms: float = 1000.0
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

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for serialization"""
        return {
            'q_learning': self.q_learning.__dict__,
            'state_encoding': self.state_encoding.__dict__,
            'reward': self.reward.__dict__,
            'training': self.training.__dict__
        }

    @classmethod
    def from_dict(cls, config_dict: Dict[str, Any]) -> 'RLAgentSettings':
        """Create from dictionary"""
        return cls(
            q_learning=QLearningConfig(**config_dict.get('q_learning', {})),
            state_encoding=StateEncodingConfig(**config_dict.get('state_encoding', {})),
            reward=RewardConfig(**config_dict.get('reward', {})),
            training=TrainingConfig(**config_dict.get('training', {}))
        )

# Global settings instance
rl_settings = RLAgentSettings()