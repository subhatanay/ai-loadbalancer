import numpy as np
from typing import Dict, List, Tuple, Any, Optional
from collections import defaultdict
import time

from models.metrics_model import ServiceMetrics, ServiceInstance
from cpu.state_encoder import StateEncoder
from cpu.reward_calculator import RewardCalculator
from cpu.action_selector import ActionSelector
from utils.persistence import ModelPersistence
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

class QLearningAgent:
    """
    Advanced Q-Learning agent for microservices load balancing.
    Implements tabular Q-learning with enhancements for production use.
    """

    def __init__(self):
        # Configuration
        self.config = rl_settings.q_learning

        # Core components
        self.state_encoder = StateEncoder()
        self.reward_calculator = RewardCalculator()
        self.action_selector = ActionSelector()
        self.persistence = ModelPersistence()

        # Q-learning state
        self.q_table: Dict[Tuple[Tuple[int, ...], str], float] = defaultdict(float)
        self.current_epsilon = self.config.epsilon_start
        self.episode_count = 0

        # Training state
        self.current_state = None
        self.previous_state = None
        self.last_action = None
        self.last_reward = 0.0

        # Performance tracking
        self.episode_rewards = []
        self.episode_steps = []
        self.q_value_history = []

        # Caching for performance
        self.state_cache = {}
        self.action_cache = {}

        rl_logger.logger.info("Q-Learning Agent initialized")

    def initialize(self,
                   historical_metrics: Optional[List[ServiceMetrics]] = None,
                   load_existing_model: bool = True):
        """
        Initialize the agent with historical data and optionally load existing model.

        Args:
            historical_metrics: Historical metrics for state encoder fitting
            load_existing_model: Whether to load existing Q-table
        """
        rl_logger.logger.info("Initializing Q-Learning Agent")

        # Fit state encoder on historical data
        if historical_metrics:
            self.state_encoder.fit(historical_metrics)
            rl_logger.logger.info(f"State encoder fitted on {len(historical_metrics)} samples")

        # Load existing model if requested
        if load_existing_model:
            self.load_model()

        # Log initialization
        state_space_size = self.state_encoder.get_state_space_size()
        rl_logger.logger.info(
            f"Agent initialized | State space size: {state_space_size:,} | "
            f"Q-table entries: {len(self.q_table):,}"
        )

    def select_action(self,
                      current_metrics: List[ServiceMetrics],
                      available_instances: List[ServiceInstance]) -> str:
        """
        Select action based on current metrics and available instances.

        Args:
            current_metrics: Current service metrics
            available_instances: Available service instances

        Returns:
            Selected action (instance ID)
        """
        # Encode current state
        state_key = self.state_encoder.encode_state(current_metrics)

        # Get available actions
        available_actions = self.action_selector.get_available_actions(available_instances)

        if not available_actions:
            raise ValueError("No available actions for selection")

        # Select action
        selected_action = self.action_selector.select_action(
            state_key=state_key,
            q_table=self.q_table,
            available_actions=available_actions,
            epsilon=self.current_epsilon,
            episode=self.episode_count
        )

        # Update agent state
        self.previous_state = self.current_state
        self.current_state = state_key
        self.last_action = selected_action

        return selected_action

    def update_q_table(self,
                       new_metrics: List[ServiceMetrics],
                       previous_metrics: List[ServiceMetrics]):
        """
        Update Q-table based on new metrics and calculate reward.

        Args:
            new_metrics: Metrics after action execution
            previous_metrics: Metrics before action execution
        """
        if (self.previous_state is None or
                self.last_action is None or
                self.current_state is None):
            rl_logger.logger.warning("Cannot update Q-table: missing state/action information")
            return

        # Calculate reward
        reward = self.reward_calculator.calculate_reward(
            current_metrics=new_metrics,
            previous_metrics=previous_metrics,
            action_taken=self.last_action
        )

        # Encode next state
        next_state = self.state_encoder.encode_state(new_metrics)

        # Q-learning update
        self._update_q_value(
            state=self.previous_state,
            action=self.last_action,
            reward=reward,
            next_state=next_state
        )

        # Store reward
        self.last_reward = reward

        # Update current state
        self.current_state = next_state

    def _update_q_value(self,
                        state: Tuple[int, ...],
                        action: str,
                        reward: float,
                        next_state: Tuple[int, ...]):
        """
        Core Q-learning update using Bellman equation.

        Args:
            state: Previous state
            action: Action taken
            reward: Reward received
            next_state: Resulting state
        """
        # Current Q-value
        current_q = self.q_table[(state, action)]

        # Find maximum Q-value for next state
        next_state_q_values = [
            self.q_table[(next_state, a)]
            for a in self._get_possible_actions_for_state(next_state)
        ]

        max_next_q = max(next_state_q_values) if next_state_q_values else 0.0

        # Q-learning update
        td_target = reward + self.config.discount_factor * max_next_q
        td_error = td_target - current_q
        new_q = current_q + self.config.learning_rate * td_error

        # Update Q-table
        self.q_table[(state, action)] = new_q

        # Log update
        rl_logger.log_q_update(str(state), action, current_q, new_q, reward)

        # Track Q-value statistics
        self.q_value_history.append({
            'episode': self.episode_count,
            'state': state,
            'action': action,
            'old_q': current_q,
            'new_q': new_q,
            'reward': reward,
            'td_error': td_error
        })

    def _get_possible_actions_for_state(self, state: Tuple[int, ...]) -> List[str]:
        """Get possible actions for a given state from Q-table history"""
        possible_actions = set()

        for (s, a) in self.q_table.keys():
            if s == state:
                possible_actions.add(a)

        # If no actions found, return empty list
        return list(possible_actions) if possible_actions else []

    def start_episode(self):
        """Start a new training episode"""
        self.episode_count += 1
        self.episode_steps.append(0)

        # Update epsilon
        self.current_epsilon = max(
            self.config.epsilon_min,
            self.current_epsilon * self.config.epsilon_decay
        )

        # Reset episode state
        self.current_state = None
        self.previous_state = None
        self.last_action = None
        self.last_reward = 0.0

        rl_logger.log_episode_start(self.episode_count, self.current_epsilon)

    def end_episode(self, total_reward: float):
        """End current training episode"""
        if not self.episode_steps:
            return

        # Record episode statistics
        self.episode_rewards.append(total_reward)
        steps = self.episode_steps[-1]

        # Calculate average Q-value
        recent_q_values = [
            entry['new_q'] for entry in self.q_value_history[-steps:]
        ] if steps > 0 else [0.0]

        avg_q_value = np.mean(recent_q_values) if recent_q_values else 0.0

        rl_logger.log_episode_end(self.episode_count, total_reward, steps, avg_q_value)

        # Periodic logging of training progress
        if self.episode_count % 50 == 0:
            self._log_training_progress()

    def increment_step(self):
        """Increment step counter for current episode"""
        if self.episode_steps:
            self.episode_steps[-1] += 1

    def _log_training_progress(self):
        """Log detailed training progress"""
        if len(self.episode_rewards) < 10:
            return

        recent_rewards = self.episode_rewards[-50:]
        avg_reward = np.mean(recent_rewards)

        rl_logger.log_training_progress(
            episode=self.episode_count,
            total_episodes=rl_settings.training.max_episodes,
            avg_reward=avg_reward,
            epsilon=self.current_epsilon,
            q_table_size=len(self.q_table)
        )

    def get_policy(self, state_key: Tuple[int, ...]) -> Dict[str, float]:
        """
        Get current policy (action probabilities) for a given state.

        Args:
            state_key: State representation

        Returns:
            Dictionary mapping actions to their Q-values
        """
        state_actions = {}

        for (s, a), q_value in self.q_table.items():
            if s == state_key:
                state_actions[a] = q_value

        return state_actions

    def get_best_action(self, state_key: Tuple[int, ...]) -> Optional[str]:
        """Get best action for a given state (pure exploitation)"""
        policy = self.get_policy(state_key)

        if not policy:
            return None

        return max(policy.items(), key=lambda x: x[1])[0]

    def get_training_statistics(self) -> Dict[str, Any]:
        """Get comprehensive training statistics"""
        if not self.episode_rewards:
            return {}

        # Basic statistics
        recent_rewards = self.episode_rewards[-100:] if len(self.episode_rewards) >= 100 else self.episode_rewards

        stats = {
            'episodes_completed': self.episode_count,
            'total_q_entries': len(self.q_table),
            'current_epsilon': self.current_epsilon,
            'average_recent_reward': np.mean(recent_rewards),
            'best_episode_reward': max(self.episode_rewards),
            'worst_episode_reward': min(self.episode_rewards),
            'reward_std': np.std(recent_rewards),
            'state_space_explored': len(set(s for (s, a) in self.q_table.keys())),
        }

        # Add component statistics
        stats.update({
            'action_selector_stats': self.action_selector.get_action_statistics(),
            'reward_calculator_stats': self.reward_calculator.get_reward_statistics()
        })

        # Learning progress
        if len(self.episode_rewards) >= 20:
            early_rewards = np.mean(self.episode_rewards[:10])
            recent_rewards_mean = np.mean(self.episode_rewards[-10:])
            stats['learning_progress'] = (recent_rewards_mean - early_rewards) / (abs(early_rewards) + 1e-6)

        return stats

    def save_model(self, path: Optional[str] = None):
        """Save only the essential model components to a file."""
        save_path = path or rl_settings.model_save_path

        # Create a lean dictionary with only what's needed for inference
        model_to_save = {
            'q_table': dict(self.q_table),
            'state_encoder': self.state_encoder, # The encoder is needed for inference
            'episode_count': self.episode_count
        }

        self.persistence.save_model(model_to_save, save_path)
        rl_logger.log_model_saved(save_path, len(self.q_table))

    def load_model(self, path: Optional[str] = None):
        """Load model from a file, supporting both old and new formats."""
        load_path = path or rl_settings.model_save_path

        try:
            loaded_data = self.persistence.load_model(load_path)
            if not loaded_data:
                rl_logger.logger.warning(f"Model file is empty or invalid: {load_path}")
                return False

            # Case 1: The loaded data is the entire agent object (old format)
            if isinstance(loaded_data, QLearningAgent):
                rl_logger.logger.warning("Loading model from old format (entire agent object).")
                # Directly access attributes to avoid issues with __dict__
                self.q_table = defaultdict(float, getattr(loaded_data, 'q_table', {}))
                self.state_encoder = getattr(loaded_data, 'state_encoder', StateEncoder())
                self.episode_count = getattr(loaded_data, 'episode_count', 0)
                rl_logger.logger.info("Successfully extracted data from old agent format.")

            # Case 2: The loaded data is a dictionary (new, lean format)
            elif isinstance(loaded_data, dict):
                rl_logger.logger.info("Loading model from new lean format.")
                self.q_table = defaultdict(float, loaded_data.get('q_table', {}))
                self.state_encoder = loaded_data.get('state_encoder', StateEncoder())
                self.episode_count = loaded_data.get('episode_count', 0)

            else:
                rl_logger.logger.error(f"Unknown model format in {load_path}: {type(loaded_data)}")
                return False

            rl_logger.logger.info(
                f"Model loaded successfully | Q-table size: {len(self.q_table)} | Episodes: {self.episode_count}"
            )
            return True

        except FileNotFoundError:
            rl_logger.logger.warning(f"Model file not found at {load_path}. Starting fresh.")
        except Exception as e:
            rl_logger.logger.error(f"Failed to load model from {load_path}: {e}", exc_info=True)
        
        return False

    def reset(self):
        """Reset agent to initial state"""
        self.q_table.clear()
        self.current_epsilon = self.config.epsilon_start
        self.episode_count = 0
        self.episode_rewards.clear()
        self.episode_steps.clear()
        self.q_value_history.clear()

        # Reset components
        self.action_selector = ActionSelector()
        self.reward_calculator = RewardCalculator()

        rl_logger.logger.info("Q-Learning Agent reset to initial state")
