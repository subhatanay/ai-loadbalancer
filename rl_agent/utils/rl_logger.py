import logging
import logging.handlers
from pathlib import Path
from typing import Optional
import json
from datetime import datetime

class RLLogger:
    """Enhanced logger for RL Agent with structured logging"""

    def __init__(self, log_path: str = "logs/rl_agent.log", log_level: int = logging.INFO):
        self.log_path = Path(log_path)
        self.log_path.parent.mkdir(parents=True, exist_ok=True)

        # Create logger
        self.logger = logging.getLogger("rl_agent")
        self.logger.setLevel(log_level)

        # Clear existing handlers
        self.logger.handlers.clear()

        # Create formatters
        detailed_formatter = logging.Formatter(
            '%(asctime)s | %(levelname)-8s | %(name)s | %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )

        # Console handler
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        console_handler.setFormatter(detailed_formatter)
        self.logger.addHandler(console_handler)

        # File handler with rotation
        file_handler = logging.handlers.RotatingFileHandler(
            self.log_path,
            maxBytes=10*1024*1024,  # 10MB
            backupCount=5
        )
        file_handler.setLevel(logging.DEBUG)
        file_handler.setFormatter(detailed_formatter)
        self.logger.addHandler(file_handler)

    def log_episode_start(self, episode: int, epsilon: float):
        """Log episode start with key parameters"""
        self.logger.info(f"🎬 Episode {episode} started | Epsilon: {epsilon:.4f}")

    def log_episode_end(self, episode: int, total_reward: float, steps: int, avg_q_value: float):
        """Log episode completion with performance metrics"""
        self.logger.info(
            f"🏁 Episode {episode} completed | "
            f"Total Reward: {total_reward:.3f} | "
            f"Steps: {steps} | "
            f"Avg Q-Value: {avg_q_value:.3f}"
        )

    def log_action_taken(self, state_key: str, action: str, q_values: dict):
        """Log action selection with Q-values"""
        best_q = max(q_values.values()) if q_values else 0.0
        self.logger.debug(
            f"🎯 Action taken: {action} | State: {state_key[:50]}... | Best Q: {best_q:.3f}"
        )

    def log_q_update(self, state_key: str, action: str, old_q: float, new_q: float, reward: float):
        """Log Q-table updates"""
        td_error = new_q - old_q
        self.logger.debug(
            f"📊 Q-Update | Action: {action} | "
            f"Q: {old_q:.3f} → {new_q:.3f} | "
            f"Reward: {reward:.3f} | TD-Error: {td_error:.3f}"
        )

    def log_metrics_collected(self, metrics_count: int, services: list):
        """Log metrics collection"""
        self.logger.debug(f"📈 Metrics collected for {metrics_count} services: {services}")

    def log_reward_calculation(self, components: dict, total_reward: float):
        """Log reward function components"""
        self.logger.debug(
            f"🎁 Reward calculated: {total_reward:.3f} | "
            f"Components: {json.dumps(components, indent=None)}"
        )

    def log_state_encoding(self, raw_metrics: dict, encoded_state: tuple):
        """Log state encoding process"""
        self.logger.debug(
            f"🔢 State encoded | Raw metrics count: {len(raw_metrics)} | "
            f"Encoded state: {encoded_state}"
        )

    def log_model_saved(self, path: str, q_table_size: int):
        """Log model persistence"""
        self.logger.info(f"💾 Model saved to {path} | Q-table size: {q_table_size}")

    def log_error(self, error_msg: str, exception: Optional[Exception] = None):
        """Log errors with optional exception details"""
        if exception:
            self.logger.error(f"❌ {error_msg}", exc_info=True)
        else:
            self.logger.error(f"❌ {error_msg}")

    def log_training_progress(self, episode: int, total_episodes: int,
                              avg_reward: float, epsilon: float, q_table_size: int):
        """Log training progress summary"""
        progress = (episode / total_episodes) * 100
        self.logger.info(
            f"📊 Training Progress: {progress:.1f}% ({episode}/{total_episodes}) | "
            f"Avg Reward: {avg_reward:.3f} | "
            f"Epsilon: {epsilon:.4f} | "
            f"Q-table size: {q_table_size}"
        )

# Global logger instance
rl_logger = RLLogger()
