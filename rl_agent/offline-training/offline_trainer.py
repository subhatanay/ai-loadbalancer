import time
from pathlib import Path
from typing import Optional

from experience_loader import ExperienceLoader
from data_preprocessor import DataPreprocessor
from training_evaluator import TrainingEvaluator
import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from cpu.q_learning_agent import QLearningAgent
from cpu.state_encoder import StateEncoder
from cpu.reward_calculator import RewardCalculator
from utils.offline_persistence import OfflineModelPersistence
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

class OfflineTrainer:
    """Main orchestrator for offline RL training"""

    def __init__(self, experience_log_path: str):
        self.experience_log_path = experience_log_path
        self.loader = ExperienceLoader(experience_log_path)
        self.preprocessor = DataPreprocessor()
        self.evaluator = TrainingEvaluator(max_history_size=50)

        # Initialize RL components
        self.agent = QLearningAgent()
        self.state_encoder = StateEncoder()
        self.reward_calculator = RewardCalculator()
        self.persistence = OfflineModelPersistence()

        # Training configuration
        self.config = rl_settings.training

    def train(self,
              max_experiences: Optional[int] = None,
              validation_split: float = 0.2,
              save_checkpoints: bool = True) -> dict:
        """
        Run complete offline training process

        Args:
            max_experiences: Maximum experiences to load (None for all)
            validation_split: Fraction of data to use for validation
            save_checkpoints: Whether to save training checkpoints

        Returns:
            Training summary and final performance metrics
        """
        rl_logger.logger.info("Starting offline RL training")
        start_time = time.time()

        # Step 1: Load experiences
        rl_logger.logger.info("Loading experiences...")
        experiences = list(self.loader.load_experiences(max_experiences))

        if not experiences:
            rl_logger.logger.error("No valid experiences loaded")
            return {'status': 'failed', 'reason': 'no_experiences'}

        # Step 2: Split data
        split_idx = int(len(experiences) * (1 - validation_split))
        train_experiences = experiences[:split_idx]
        val_experiences = experiences[split_idx:]

        rl_logger.logger.info(f"Split data: {len(train_experiences)} training, {len(val_experiences)} validation")

        # Step 3: Preprocess experiences and fit encoders
        rl_logger.logger.info("Preprocessing experiences...")
        # Build action mappings on all experiences first
        all_experiences = train_experiences + val_experiences
        self.preprocessor._build_action_mappings(all_experiences)
        
        processed_train, train_metadata = self.preprocessor.process_experiences(train_experiences)
        processed_val, val_metadata = self.preprocessor.process_experiences(val_experiences)

        # Step 4: Fit state encoder with all processed data
        rl_logger.logger.info("Fitting state encoder...")
        all_states_for_fitting = []
        for state_metrics, _, _, next_state_metrics in processed_train + processed_val:
            all_states_for_fitting.extend(state_metrics)
            all_states_for_fitting.extend(next_state_metrics)
        
        self.state_encoder.fit(all_states_for_fitting)
        rl_logger.logger.info("State encoder fitting completed")

        # Step 5: Train agent
        rl_logger.logger.info("Training Q-learning agent...")
        training_summary = self._train_agent(len(train_experiences), len(val_experiences), save_checkpoints)

        # Step 6: Final evaluation (requires a fresh generator)
        val_experiences_gen = self.loader.load_experiences(offset=len(train_experiences), limit=len(val_experiences))
        processed_val_data = self.preprocessor.process_experiences_generator(val_experiences_gen)
        final_performance = self._evaluate_final_performance(processed_val_data)

        total_time = time.time() - start_time

        # Compile results
        data_stats = {
            'total_experiences': len(experiences),
            'training_experiences': len(processed_train),
            'validation_experiences': len(processed_val),
            'unique_actions': train_metadata['unique_actions']
        }
        
        # Save final model
        final_checkpoint_path = self.persistence.save_checkpoint(
            self.agent, self.state_encoder, self.config.max_episodes
        )
        
        rl_logger.logger.info(f"Final model saved to: {final_checkpoint_path}")
        
        # Copy final models to main models directory for production use
        self._copy_final_models_to_production(final_checkpoint_path)
        
        return {
            'status': 'completed',
            'training_time_seconds': total_time,
            'data_stats': data_stats,
            'training_summary': training_summary,
            'final_performance': final_performance,
            'action_mappings': self.preprocessor.action_to_idx,
            'final_model_path': final_checkpoint_path
        }

    def _train_agent(self, train_count, val_count, save_checkpoints):
        """Train the Q-learning agent using pre-loaded data with shuffling to avoid repeated file reads."""
        batch_size = self.config.batch_size
        rl_logger.logger.info(f"Training on {train_count} experiences with batch size {batch_size}")
        
        # Load training data once at the beginning
        rl_logger.logger.info("Loading training data once for all epochs...")
        train_experiences = list(self.loader.load_experiences(limit=train_count))
        processed_train_data = list(self.preprocessor.process_experiences_generator(iter(train_experiences)))
        rl_logger.logger.info(f"Loaded {len(processed_train_data)} processed training experiences")

        for epoch in range(self.config.max_episodes):
            # Shuffle data each epoch for better training
            import random
            random.shuffle(processed_train_data)
            
            total_loss = 0
            num_batches = 0

            # Train in batches
            batch = []
            for experience in processed_train_data:
                batch.append(experience)
                if len(batch) == batch_size:
                    for state_metrics, action_idx, reward, next_state_metrics in batch:
                        state = self.state_encoder.encode_state(state_metrics)
                        next_state = self.state_encoder.encode_state(next_state_metrics)
                        action = self.preprocessor.idx_to_action[action_idx]

                        old_q = self.agent.q_table.get((state, action), 0.0)
                        self.agent._update_q_value(state, action, reward, next_state)
                        new_q = self.agent.q_table.get((state, action), 0.0)

                        td_error = abs(new_q - old_q)
                        total_loss += td_error

                    batch = []
                    num_batches += 1

            # Process remaining batch
            if batch:
                for state_metrics, action_idx, reward, next_state_metrics in batch:
                    state = self.state_encoder.encode_state(state_metrics)
                    next_state = self.state_encoder.encode_state(next_state_metrics)
                    action = self.preprocessor.idx_to_action[action_idx]

                    old_q = self.agent.q_table.get((state, action), 0.0)
                    self.agent._update_q_value(state, action, reward, next_state)
                    new_q = self.agent.q_table.get((state, action), 0.0)

                    td_error = abs(new_q - old_q)
                    total_loss += td_error

                num_batches += 1

            # Evaluate epoch
            avg_loss = total_loss / num_batches if num_batches > 0 else 0
            # Only store loss history for recent epochs to save memory
            if epoch % 10 == 0:  # Store every 10th epoch only
                self.evaluator.training_history['loss_history'].append(avg_loss)

            # Validation evaluation
            if epoch % self.config.evaluation_frequency == 0:
                # Load validation data once if not already loaded
                if epoch == 0:
                    rl_logger.logger.info("Loading validation data once...")
                    val_experiences = list(self.loader.load_experiences(offset=train_count, limit=val_count))
                    self.processed_val_data = list(self.preprocessor.process_experiences_generator(iter(val_experiences)))
                    rl_logger.logger.info(f"Loaded {len(self.processed_val_data)} validation experiences")
                
                val_performance = self._evaluate_on_validation(iter(self.processed_val_data))
                self.evaluator.training_history['validation_performance'].append(val_performance)
                rl_logger.logger.info(f"Epoch {epoch}: Loss: {avg_loss:.4f}, Val Performance: {val_performance:.3f}")

            # Save checkpoint
            if save_checkpoints and epoch % self.config.save_frequency == 0:
                self.persistence.save_checkpoint(self.agent, self.state_encoder, epoch)

        return self.evaluator.get_training_summary()

    def _evaluate_on_validation(self, val_data):
        """Evaluate agent performance on validation data"""
        total_q_value = 0.0
        count = 0

        for state_metrics, action_idx, reward, next_state_metrics in val_data:
            state = self.state_encoder.encode_state(state_metrics)
            action = self.preprocessor.idx_to_action[action_idx]

            q_value = self.agent.q_table.get((state, action), 0.0)
            total_q_value += q_value
            count += 1

        return total_q_value / count if count > 0 else 0.0

    def _evaluate_final_performance(self, val_data):
        """Comprehensive final evaluation"""
        correct_predictions = 0
        total_predictions = 0

        for state_metrics, true_action_idx, reward, next_state_metrics in val_data:
            state = self.state_encoder.encode_state(state_metrics)

            # Get agent's best action
            best_action = self.agent.get_best_action(state)

            if best_action:
                predicted_action_idx = self.preprocessor.action_to_idx[best_action]
                if predicted_action_idx == true_action_idx:
                    correct_predictions += 1

            total_predictions += 1

        accuracy = correct_predictions / total_predictions if total_predictions > 0 else 0.0

        avg_q_value = self._calculate_average_q_value()

        return {
            'validation_accuracy': accuracy,
            'q_table_size': len(self.agent.q_table),
            'states_visited': len(self.agent.q_table),
            'average_q_value': avg_q_value
        }
    
    def _copy_final_models_to_production(self, checkpoint_path: str):
        """Copy final trained models to main models directory for production use"""
        import shutil
        import os
        
        try:
            # Get the main models directory (parent of offline-training)
            main_models_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'models')
            os.makedirs(main_models_dir, exist_ok=True)
            
            # Copy agent model
            agent_src = os.path.join(checkpoint_path, 'agent.pkl')
            agent_dst = os.path.join(main_models_dir, 'q_table.pkl')
            if os.path.exists(agent_src):
                shutil.copy2(agent_src, agent_dst)
                rl_logger.logger.info(f"✅ Copied agent model to: {agent_dst}")
            
            # Copy state encoder
            encoder_src = os.path.join(checkpoint_path, 'state_encoder.pkl')
            encoder_dst = os.path.join(main_models_dir, 'state_encoder.pkl')
            if os.path.exists(encoder_src):
                shutil.copy2(encoder_src, encoder_dst)
                rl_logger.logger.info(f"✅ Copied state encoder to: {encoder_dst}")
            
            # Create action mappings file
            mappings_dst = os.path.join(main_models_dir, 'action_mappings.json')
            with open(mappings_dst, 'w') as f:
                import json
                json.dump(self.preprocessor.action_to_idx, f, indent=2)
            rl_logger.logger.info(f"✅ Saved action mappings to: {mappings_dst}")
            
            rl_logger.logger.info("🎉 Final models successfully copied to production directory!")
            
        except Exception as e:
            rl_logger.logger.error(f"❌ Failed to copy models to production: {str(e)}")
            import traceback
            traceback.print_exc()

    def _calculate_average_q_value(self):
        """Calculate average Q-value across all state-action pairs"""
        all_q_values = []
        for state_actions in self.agent.q_table.values():
            if isinstance(state_actions, dict):
                all_q_values.extend(state_actions.values())
            else:
                all_q_values.append(state_actions)

        return sum(all_q_values) / len(all_q_values) if all_q_values else 0.0
