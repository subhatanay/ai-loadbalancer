import numpy as np
from typing import Dict, List, Any
from collections import defaultdict

import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from utils.rl_logger import rl_logger

class TrainingEvaluator:
    """Evaluates offline training progress and performance"""

    def __init__(self, max_history_size: int = 100):
        # Use limited-size histories to prevent memory bloat
        self.max_history_size = max_history_size
        self.training_history = {
            'episodes': [],
            'total_rewards': [],
            'q_value_stats': [],
            'action_distributions': [],
            'loss_history': [],
            'validation_performance': []
        }

    def evaluate_episode(self, episode: int, experiences: List, agent, state_encoder):
        """Evaluate a single training episode"""
        total_reward = sum(exp[2] for exp in experiences)  # Sum of rewards

        # Calculate Q-value statistics
        q_values = []
        action_counts = defaultdict(int)

        for state_metrics, action_idx, reward, next_state_metrics in experiences:
            # Encode state
            encoded_state = state_encoder.encode_state(state_metrics)

            # Get Q-values for this state
            state_q_values = agent.q_table.get(encoded_state, {})
            if state_q_values:
                q_values.extend(state_q_values.values())

            action_counts[action_idx] += 1

        # Store statistics with size limit to prevent memory bloat
        self._append_with_limit('episodes', episode)
        self._append_with_limit('total_rewards', total_reward)

        if q_values:
            q_stats = {
                'mean': np.mean(q_values),
                'std': np.std(q_values),
                'min': np.min(q_values),
                'max': np.max(q_values)
            }
            self._append_with_limit('q_value_stats', q_stats)

        self._append_with_limit('action_distributions', dict(action_counts))

        # Log progress
        if episode % 100 == 0:
            self._log_progress(episode)

    def _log_progress(self, episode: int):
        """Log training progress"""
        recent_rewards = self.training_history['total_rewards'][-100:]
        recent_q_stats = self.training_history['q_value_stats'][-100:]

        avg_reward = np.mean(recent_rewards) if recent_rewards else 0

        if recent_q_stats:
            avg_q_mean = np.mean([stats['mean'] for stats in recent_q_stats])
            rl_logger.logger.info(
                f"Episode {episode}: Avg Reward: {avg_reward:.3f}, "
                f"Avg Q-Value: {avg_q_mean:.3f}, "
                f"Q-Table Size: {len(self.training_history['q_value_stats'])}"
            )

    def get_training_summary(self) -> Dict[str, Any]:
        """Get comprehensive training summary"""
        if not self.training_history['total_rewards']:
            return {'status': 'no_data'}

        rewards = self.training_history['total_rewards']

        return {
            'total_episodes': len(rewards),
            'final_average_reward': np.mean(rewards[-100:]) if len(rewards) >= 100 else np.mean(rewards),
            'best_reward': max(rewards),
            'worst_reward': min(rewards),
            'reward_improvement': rewards[-1] - rewards[0] if len(rewards) > 1 else 0,
            'convergence_episode': self._find_convergence_episode(),
            'final_q_table_size': len(self.training_history['q_value_stats'])
        }

    def _find_convergence_episode(self, window_size: int = 200) -> int:
        """Find episode where training appears to converge"""
        if len(self.training_history['total_rewards']) < window_size * 2:
            return -1

        rewards = self.training_history['total_rewards']

        for i in range(window_size, len(rewards) - window_size):
            early_window = rewards[i-window_size:i]
            late_window = rewards[i:i+window_size]

            # Check if improvement has plateaued
            early_avg = np.mean(early_window)
            late_avg = np.mean(late_window)

            if abs(late_avg - early_avg) < 0.01:  # Convergence threshold
                return i

        return -1

    def _append_with_limit(self, key: str, value):
        """Append to history with size limit to prevent memory bloat"""
        history_list = self.training_history[key]
        history_list.append(value)
        
        # Keep only the most recent entries
        if len(history_list) > self.max_history_size:
            history_list.pop(0)  # Remove oldest entry
