import random
import numpy as np
import logging
from typing import List, Dict, Tuple, Any
from config.rl_settings import rl_settings
from models.metrics_model import ServiceInstance
from utils.rl_logger import rl_logger

logger = logging.getLogger(__name__)

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
        import time
        start_time = time.time()
        
        if not available_actions:
            raise ValueError("No available actions for selection")

        # Adaptive epsilon based on episode progress
        step_start = time.time()
        adaptive_epsilon = self._calculate_adaptive_epsilon(epsilon, episode)
        epsilon_time = (time.time() - step_start) * 1000

        # Epsilon-greedy selection with enhancements
        step_start = time.time()
        if random.random() < adaptive_epsilon:
            # Exploration: Choose action based on exploration strategy
            action = self._exploration_strategy(available_actions, state_key, q_table)
            selection_type = "exploration"
        else:
            # Exploitation: Choose best known action
            action = self._exploitation_strategy(state_key, q_table, available_actions)
            selection_type = "exploitation"
        strategy_time = (time.time() - step_start) * 1000

        # Log action selection
        step_start = time.time()
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
        logging_time = (time.time() - step_start) * 1000
        
        total_time = (time.time() - start_time) * 1000
        logger.debug(f"ACTION_SELECTOR_TIMING: {total_time:.2f}ms total (epsilon: {epsilon_time:.2f}ms, strategy: {strategy_time:.2f}ms, logging: {logging_time:.2f}ms)")

        return action

    def _calculate_adaptive_epsilon(self, base_epsilon: float, episode: int) -> float:
        """Calculate adaptive epsilon based on learning progress"""
        # Standard exponential decay
        decayed_epsilon = max(
            self.config.epsilon_min,
            base_epsilon * (self.config.epsilon_decay ** episode)
        )

        # Additional adaptations for better load balancing

        # 1. Boost exploration if performance is stagnating OR low diversity
        if len(self.action_history) > 50:
            recent_diversity = self._calculate_action_diversity()
            if recent_diversity < 0.4:  # Increased diversity threshold for load balancing
                decayed_epsilon = min(1.0, decayed_epsilon * 2.0)  # Stronger boost

        # 2. Maintain minimum exploration for load balancing
        min_epsilon_for_balancing = 0.05  # Always maintain 5% exploration
        decayed_epsilon = max(min_epsilon_for_balancing, decayed_epsilon)

        # 3. Reduce exploration only if diversity is maintained
        if episode > self.config.exploration_episodes:
            recent_diversity = self._calculate_action_diversity()
            performance_score = self._get_recent_performance_score()
            # More stringent diversity requirement for load balancing
            if performance_score > 0.8 and recent_diversity > 0.7:  # Increased diversity threshold
                decayed_epsilon *= 0.8  # Less aggressive reduction to maintain exploration

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
        Enhanced exploitation strategy with aggressive load balancing.
        """
        # Get Q-values for all available actions
        action_q_values = []
        for action in available_actions:
            q_value = q_table.get((state_key, action), 0.0)
            action_q_values.append((action, q_value))

        # Sort by Q-value (descending)
        action_q_values.sort(key=lambda x: x[1], reverse=True)

        # Enhanced tie-breaking with aggressive load balancing
        max_q_value = action_q_values[0][1]
        
        # Use much wider tolerance for "best" actions to encourage distribution
        tolerance = max(0.2, abs(max_q_value) * 0.15)  # Increased to 15% tolerance or minimum 0.2
        best_actions = [
            action for action, q_value in action_q_values
            if abs(q_value - max_q_value) <= tolerance
        ]

        # If only one action is "best", still check for load balancing override
        if len(best_actions) == 1:
            # Check if this action has been used too frequently
            recent_actions = [h['action'] for h in self.action_history[-10:]]
            if len(recent_actions) >= 5:
                best_action_usage = recent_actions.count(best_actions[0])
                if best_action_usage >= 4:  # Used 4+ times in last 10 decisions
                    # Force load balancing - expand to top 2-3 actions
                    expanded_best = [action for action, _ in action_q_values[:min(3, len(action_q_values))]]
                    best_actions = expanded_best
                    logger.info(f"Load balancing override: expanding from {best_actions[0]} to {best_actions}")

        if len(best_actions) == 1:
            return best_actions[0]

        # Aggressive load balancing strategies

        # 1. Strongly prefer least recently used actions
        recent_actions = [h['action'] for h in self.action_history[-15:]]  # Shorter window for more aggressive balancing
        action_usage_counts = {}
        for action in recent_actions:
            action_usage_counts[action] = action_usage_counts.get(action, 0) + 1
        
        # Find actions with minimum recent usage
        min_usage = min(action_usage_counts.get(action, 0) for action in best_actions)
        least_used_best = [
            action for action in best_actions
            if action_usage_counts.get(action, 0) == min_usage
        ]

        if least_used_best:
            selected = random.choice(least_used_best)
            logger.debug(f"Load balancing: selected {selected} (usage: {action_usage_counts.get(selected, 0)}) from {best_actions}")
            return selected

        # 2. Fallback: Random selection among best actions
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
