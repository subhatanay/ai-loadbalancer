import random
from typing import List, Tuple, Dict, Any
import numpy as np

from models.metrics_model import ServiceInstance
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

class ActionSelector:
    """
    Action selection component for Q-learning agent.
    Manages exploration vs exploitation and action space.
    """

    def __init__(self):
        self.config = rl_settings.q_learning
        self.action_history = []
        self.action_performance = {}

    def get_available_actions(self, service_instances: List[ServiceInstance]) -> List[str]:
        """
        Get list of available actions from current service instances.

        Args:
            service_instances: List of available service instances

        Returns:
            List of action identifiers (instance IDs)
        """
        # Filter only healthy instances
        healthy_instances = [
            instance for instance in service_instances
            if instance.status == "healthy"
        ]

        if not healthy_instances:
            rl_logger.logger.warning("No healthy instances available for action selection")
            return []

        # Return instance IDs as actions
        actions = [instance.instance_id for instance in healthy_instances]

        rl_logger.logger.debug(f"Available actions: {len(actions)} healthy instances")
        return actions

    def select_action(self,
                      state_key: Tuple[int, ...],
                      q_table: Dict[Tuple, float],
                      available_actions: List[str],
                      epsilon: float,
                      episode: int = 0) -> str:
        """
        Select action using epsilon-greedy strategy with enhancements.

        Args:
            state_key: Current state representation
            q_table: Current Q-table
            available_actions: List of available actions
            epsilon: Current exploration rate
            episode: Current episode number

        Returns:
            Selected action
        """
        if not available_actions:
            raise ValueError("No available actions for selection")

        # Adaptive epsilon based on episode progress
        adaptive_epsilon = self._calculate_adaptive_epsilon(epsilon, episode)

        # Epsilon-greedy selection with enhancements
        if random.random() < adaptive_epsilon:
            # Exploration: Choose action based on exploration strategy
            action = self._exploration_strategy(available_actions, state_key, q_table)
            selection_type = "exploration"
        else:
            # Exploitation: Choose best known action
            action = self._exploitation_strategy(state_key, q_table, available_actions)
            selection_type = "exploitation"

        # Log action selection
        q_values = {
            action: q_table.get((state_key, action), 0.0)
            for action in available_actions
        }

        rl_logger.log_action_taken(str(state_key), action, q_values)
        rl_logger.logger.debug(f"Action selection: {selection_type} (ε={adaptive_epsilon:.3f})")

        # Record action history
        self.action_history.append({
            'episode': episode,
            'state': state_key,
            'action': action,
            'epsilon': adaptive_epsilon,
            'selection_type': selection_type,
            'available_actions_count': len(available_actions)
        })

        return action

    def _calculate_adaptive_epsilon(self, base_epsilon: float, episode: int) -> float:
        """Calculate adaptive epsilon based on learning progress"""
        # Standard exponential decay
        decayed_epsilon = max(
            self.config.epsilon_min,
            base_epsilon * (self.config.epsilon_decay ** episode)
        )

        # Additional adaptations

        # 1. Boost exploration if performance is stagnating
        if len(self.action_history) > 50:
            recent_diversity = self._calculate_action_diversity()
            if recent_diversity < 0.3:  # Low diversity threshold
                decayed_epsilon = min(1.0, decayed_epsilon * 1.5)

        # 2. Reduce exploration in later episodes if performing well
        if episode > self.config.exploration_episodes:
            performance_score = self._get_recent_performance_score()
            if performance_score > 0.8:  # High performance threshold
                decayed_epsilon *= 0.5

        return decayed_epsilon

    def _exploration_strategy(self,
                              available_actions: List[str],
                              state_key: Tuple[int, ...],
                              q_table: Dict[Tuple, float]) -> str:
        """
        Enhanced exploration strategy beyond pure random selection.
        """
        # Strategy 1: Upper Confidence Bound (UCB) exploration
        if len(self.action_history) > 10:
            action = self._ucb_selection(available_actions, state_key, q_table)
            if action:
                return action

        # Strategy 2: Least-tried action exploration
        action_counts = self._get_action_counts()
        least_tried_actions = [
            action for action in available_actions
            if action_counts.get(action, 0) == min(
                action_counts.get(a, 0) for a in available_actions
            )
        ]

        if least_tried_actions:
            return random.choice(least_tried_actions)

        # Fallback: Pure random selection
        return random.choice(available_actions)

    def _exploitation_strategy(self,
                               state_key: Tuple[int, ...],
                               q_table: Dict[Tuple, float],
                               available_actions: List[str]) -> str:
        """
        Enhanced exploitation strategy with tie-breaking.
        """
        # Get Q-values for all available actions
        action_q_values = []
        for action in available_actions:
            q_value = q_table.get((state_key, action), 0.0)
            action_q_values.append((action, q_value))

        # Sort by Q-value (descending)
        action_q_values.sort(key=lambda x: x[1], reverse=True)

        # Handle ties by considering additional factors
        max_q_value = action_q_values[0][1]
        best_actions = [
            action for action, q_value in action_q_values
            if abs(q_value - max_q_value) < 1e-6
        ]

        if len(best_actions) == 1:
            return best_actions[0]

        # Tie-breaking strategies

        # 1. Prefer less recently used actions among the best
        recent_actions = [h['action'] for h in self.action_history[-10:]]
        less_recent_best = [
            action for action in best_actions
            if action not in recent_actions
        ]

        if less_recent_best:
            return random.choice(less_recent_best)

        # 2. Prefer actions with better historical performance
        best_performer = self._get_best_performing_action(best_actions)
        if best_performer:
            return best_performer

        # 3. Random tie-breaking
        return random.choice(best_actions)

    def _ucb_selection(self,
                       available_actions: List[str],
                       state_key: Tuple[int, ...],
                       q_table: Dict[Tuple, float]) -> str:
        """
        Upper Confidence Bound action selection for exploration.
        """
        total_visits = len(self.action_history)
        if total_visits == 0:
            return None

        action_scores = []

        for action in available_actions:
            q_value = q_table.get((state_key, action), 0.0)

            # Count visits for this state-action pair
            visits = sum(
                1 for h in self.action_history
                if h['state'] == state_key and h['action'] == action
            )

            if visits == 0:
                # Infinite confidence for unvisited actions
                ucb_score = float('inf')
            else:
                # UCB formula: Q(s,a) + c * sqrt(ln(t) / n(s,a))
                confidence = 2.0 * np.sqrt(np.log(total_visits) / visits)
                ucb_score = q_value + confidence

            action_scores.append((action, ucb_score))

        # Select action with highest UCB score
        action_scores.sort(key=lambda x: x[1], reverse=True)
        return action_scores[0][0]

    def _calculate_action_diversity(self) -> float:
        """Calculate diversity of recent action selections"""
        if len(self.action_history) < 10:
            return 1.0

        recent_actions = [h['action'] for h in self.action_history[-20:]]
        unique_actions = len(set(recent_actions))
        total_actions = len(recent_actions)

        return unique_actions / total_actions

    def _get_recent_performance_score(self) -> float:
        """Estimate recent performance score (placeholder)"""
        # This would typically be based on recent rewards
        # For now, return a neutral score
        return 0.5

    def _get_action_counts(self) -> Dict[str, int]:
        """Get count of how many times each action has been selected"""
        action_counts = {}
        for history_item in self.action_history:
            action = history_item['action']
            action_counts[action] = action_counts.get(action, 0) + 1
        return action_counts

    def _get_best_performing_action(self, actions: List[str]) -> str:
        """Get best performing action from the list based on historical data"""
        # This is a placeholder - in practice, you'd use reward history
        # For now, just return the first action
        if not actions:
            return None
        return actions[0]

    def get_action_statistics(self) -> Dict[str, Any]:
        """Get statistics about action selection"""
        if not self.action_history:
            return {}

        action_counts = self._get_action_counts()
        recent_diversity = self._calculate_action_diversity()

        exploration_count = sum(
            1 for h in self.action_history
            if h.get('selection_type') == 'exploration'
        )

        return {
            'total_actions': len(self.action_history),
            'unique_actions': len(action_counts),
            'action_distribution': action_counts,
            'exploration_rate': exploration_count / len(self.action_history),
            'recent_diversity': recent_diversity,
            'most_selected_action': max(action_counts.items(), key=lambda x: x[1])[0] if action_counts else None
        }
