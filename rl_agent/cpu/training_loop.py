import time
import asyncio
from typing import List, Optional, Dict, Any
from datetime import datetime, timedelta
import signal
import sys

from collectors.loadbalancer_client import LoadBalancerClient
from collectors.prometheus_client import PrometheusClient
from cpu.q_learning_agent import QLearningAgent
from integration.metrics_processor import MetricsProcessor
from integration.loadbalancer_interface import LoadBalancerInterface
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

class RLTrainingLoop:
    """
    Main training loop for the Q-Learning agent.
    Coordinates data collection, action execution, and learning.
    """

    def __init__(self):
        # Core components
        self.agent = QLearningAgent()
        self.lb_client = LoadBalancerClient()
        self.prometheus_client = PrometheusClient()
        self.metrics_processor = MetricsProcessor()
        self.lb_interface = LoadBalancerInterface()

        # Training state
        self.is_training = False
        self.current_episode = 0
        self.episode_start_time = None
        self.episode_reward = 0.0
        self.episode_steps = 0

        # Data collection
        self.metrics_history = []
        self.previous_metrics = None

        # Configuration
        self.config = rl_settings.training

        # Setup signal handlers for graceful shutdown
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)

        rl_logger.logger.info("Training Loop initialized")

    def _signal_handler(self, signum, frame):
        """Handle shutdown signals gracefully"""
        rl_logger.logger.info(f"Received signal {signum}, initiating graceful shutdown")
        self.is_training = False
        self.agent.save_model()
        sys.exit(0)

    async def initialize(self):
        """Initialize training components"""
        rl_logger.logger.info("Initializing training components")

        # Check connectivity
        if not self.lb_client.health_check():
            raise RuntimeError("Load balancer not accessible")

        if not self.prometheus_client.health_check():
            raise RuntimeError("Prometheus not accessible")

        # Collect historical data for state encoder
        rl_logger.logger.info("Collecting historical metrics for initialization")
        historical_metrics = await self._collect_historical_metrics()

        # Initialize agent
        self.agent.initialize(
            historical_metrics=historical_metrics,
            load_existing_model=True
        )

        rl_logger.logger.info("Training components initialized successfully")

    async def _collect_historical_metrics(self,
                                          lookback_hours: int = 24) -> List:
        """Collect historical metrics for agent initialization"""
        try:
            # Get current services
            services = self.lb_client.get_registered_services()
            if not services:
                rl_logger.logger.warning("No services found for historical data collection")
                return []

            # Collect recent metrics
            current_metrics = self.prometheus_client.get_service_metrics(services)

            # For now, return current metrics as historical
            # In production, you'd query historical data from Prometheus
            return current_metrics * 10  # Simulate some historical data

        except Exception as e:
            rl_logger.log_error("Failed to collect historical metrics", e)
            return []

    async def run_training(self, max_episodes: Optional[int] = None):
        """
        Run the main training loop.

        Args:
            max_episodes: Maximum episodes to run (uses config if None)
        """
        max_episodes = max_episodes or self.config.max_episodes
        self.is_training = True

        rl_logger.logger.info(
            f"Starting RL training | Max episodes: {max_episodes} | "
            f"Episode length: {self.config.episode_length}"
        )

        try:
            for episode in range(1, max_episodes + 1):
                if not self.is_training:
                    break

                await self._run_episode(episode)

                # Periodic saves and evaluations
                if episode % self.config.save_frequency == 0:
                    self.agent.save_model()

                if episode % self.config.evaluation_frequency == 0:
                    await self._evaluate_agent()

                # Brief pause between episodes
                await asyncio.sleep(1)

        except Exception as e:
            rl_logger.log_error("Training loop failed", e)
        finally:
            self.is_training = False
            self.agent.save_model()
            rl_logger.logger.info("Training completed")

    async def _run_episode(self, episode_num: int):
        """Run a single training episode"""
        self.current_episode = episode_num
        self.episode_start_time = datetime.now()
        self.episode_reward = 0.0
        self.episode_steps = 0

        # Start episode in agent
        self.agent.start_episode()

        try:
            for step in range(self.config.episode_length):
                if not self.is_training:
                    break

                # Execute one training step
                step_reward = await self._execute_training_step()
                self.episode_reward += step_reward
                self.episode_steps += 1

                # Increment step counter in agent
                self.agent.increment_step()

                # Wait between steps
                await asyncio.sleep(rl_settings.action_execution_delay)

            # End episode
            self.agent.end_episode(self.episode_reward)

        except Exception as e:
            rl_logger.log_error(f"Episode {episode_num} failed", e)

    async def _execute_training_step(self) -> float:
        """Execute a single training step"""
        try:
            # 1. Collect current metrics
            services = self.lb_client.get_registered_services()
            if not services:
                rl_logger.logger.warning("No services available")
                return -5.0  # Penalty for no services

            current_metrics = self.prometheus_client.get_service_metrics(services)
            if not current_metrics:
                rl_logger.logger.warning("No metrics collected")
                return -5.0

            # 2. Select action using agent
            action = self.agent.select_action(current_metrics, services)

            # 3. Execute action (simulate routing decision)
            execution_success = await self._execute_action(action, services)
            if not execution_success:
                return -2.0  # Penalty for failed action execution

            # 4. Wait for metrics to reflect the action
            await asyncio.sleep(rl_settings.metrics_collection_interval)

            # 5. Collect new metrics
            new_services = self.lb_client.get_registered_services()
            new_metrics = self.prometheus_client.get_service_metrics(new_services)

            # 6. Update Q-table
            if self.previous_metrics is not None:
                self.agent.update_q_table(new_metrics, self.previous_metrics)
                reward = self.agent.last_reward
            else:
                reward = 0.0

            # 7. Store metrics for next iteration
            self.previous_metrics = current_metrics
            self.metrics_history.append({
                'timestamp': datetime.now(),
                'metrics': current_metrics,
                'action': action,
                'reward': reward
            })

            # 8. Cleanup old history
            if len(self.metrics_history) > 1000:
                self.metrics_history = self.metrics_history[-500:]

            return reward

        except Exception as e:
            rl_logger.log_error("Training step failed", e)
            return -10.0  # Penalty for step failure

    async def _execute_action(self, action: str, services: List) -> bool:
        """
        Execute the selected action (simulate routing decision).

        Args:
            action: Selected action (instance ID)
            services: Available services

        Returns:
            True if action executed successfully
        """
        try:
            # Find the target service
            target_service = None
            for service in services:
                if service.instance_id == action:
                    target_service = service
                    break

            if not target_service:
                rl_logger.logger.warning(f"Target service not found: {action}")
                return False

            # Simulate routing decision execution
            success = await self.lb_interface.route_request_to_service(target_service)

            if success:
                rl_logger.logger.debug(f"Action executed: routed to {action}")
            else:
                rl_logger.logger.warning(f"Failed to execute action: {action}")

            return success

        except Exception as e:
            rl_logger.log_error(f"Action execution failed: {action}", e)
            return False

    async def _evaluate_agent(self):
        """Evaluate agent performance"""
        rl_logger.logger.info("Evaluating agent performance")

        try:
            # Get training statistics
            stats = self.agent.get_training_statistics()

            # Log key metrics
            rl_logger.logger.info(
                f"🎯 Agent Evaluation | "
                f"Episodes: {stats.get('episodes_completed', 0)} | "
                f"Avg Reward: {stats.get('average_recent_reward', 0):.3f} | "
                f"Q-table size: {stats.get('total_q_entries', 0)} | "
                f"Exploration rate: {stats.get('current_epsilon', 0):.4f}"
            )

            # Additional performance metrics
            if 'learning_progress' in stats:
                rl_logger.logger.info(
                    f"📈 Learning Progress: {stats['learning_progress']:.3f}"
                )

            # Log action selection statistics
            action_stats = stats.get('action_selector_stats', {})
            if action_stats:
                rl_logger.logger.info(
                    f"🎲 Action Selection | "
                    f"Unique actions: {action_stats.get('unique_actions', 0)} | "
                    f"Diversity: {action_stats.get('recent_diversity', 0):.3f}"
                )

        except Exception as e:
            rl_logger.log_error("Agent evaluation failed", e)

    async def run_inference_mode(self):
        """Run agent in inference mode (no learning)"""
        rl_logger.logger.info("Starting inference mode")

        self.is_training = False

        while True:
            try:
                # Collect metrics
                services = self.lb_client.get_registered_services()
                if services:
                    current_metrics = self.prometheus_client.get_service_metrics(services)

                    if current_metrics:
                        # Select best action (pure exploitation)
                        state_key = self.agent.state_encoder.encode_state(current_metrics)
                        best_action = self.agent.get_best_action(state_key)

                        if best_action:
                            success = await self._execute_action(best_action, services)
                            rl_logger.logger.info(
                                f"Inference action: {best_action} | Success: {success}"
                            )

                # Wait before next inference
                await asyncio.sleep(rl_settings.metrics_collection_interval)

            except Exception as e:
                rl_logger.log_error("Inference step failed", e)
                await asyncio.sleep(5)

    def get_training_summary(self) -> Dict[str, Any]:
        """Get comprehensive training summary"""
        return {
            'training_status': 'active' if self.is_training else 'stopped',
            'current_episode': self.current_episode,
            'episode_reward': self.episode_reward,
            'episode_steps': self.episode_steps,
            'episode_duration': (
                    datetime.now() - self.episode_start_time
            ).total_seconds() if self.episode_start_time else 0,
            'agent_stats': self.agent.get_training_statistics(),
            'metrics_history_size': len(self.metrics_history)
        }
