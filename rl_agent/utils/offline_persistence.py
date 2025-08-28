import pickle
import json
from pathlib import Path
from datetime import datetime
from typing import Dict, Any

from utils.rl_logger import rl_logger

class OfflineModelPersistence:
    """Handles saving and loading of offline training artifacts"""

    def __init__(self, base_path: str = "models/offline"):
        self.base_path = Path(base_path)
        self.base_path.mkdir(parents=True, exist_ok=True)

    def save_checkpoint(self, agent, state_encoder, epoch: int):
        """Save training checkpoint by calling the agent's save method."""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        checkpoint_dir = self.base_path / f"checkpoint_epoch_{epoch}_{timestamp}"
        checkpoint_dir.mkdir(exist_ok=True)

        # Let the agent save itself in the new lean format
        agent_path = checkpoint_dir / "agent.pkl"
        agent.save_model(path=str(agent_path))

        # Save metadata
        metadata = {
            'epoch': epoch,
            'timestamp': timestamp,
            'q_table_size': len(agent.q_table),
            'state_space_size': state_encoder.get_state_space_size()
        }

        metadata_path = checkpoint_dir / "metadata.json"
        with open(metadata_path, 'w') as f:
            json.dump(metadata, f, indent=2)

        rl_logger.logger.info(f"Saved checkpoint at epoch {epoch} to {checkpoint_dir}")
        return str(checkpoint_dir)

    def save_final_model(self, agent, state_encoder, training_results: Dict[str, Any]):
        """Save final trained model without training history to reduce size"""
        final_dir = self.base_path / "final_model"
        final_dir.mkdir(exist_ok=True)

        # Save only essential agent components (no training history)
        agent_essentials = {
            'q_table': dict(agent.q_table),
            'episode_count': agent.episode_count,
            'current_epsilon': agent.current_epsilon
        }
        
        with open(final_dir / "agent.pkl", 'wb') as f:
            pickle.dump(agent_essentials, f)

        with open(final_dir / "state_encoder.pkl", 'wb') as f:
            pickle.dump(state_encoder, f)

        # Save only essential training results (no full history)
        essential_results = {
            'status': training_results.get('status'),
            'training_time_seconds': training_results.get('training_time_seconds'),
            'data_stats': training_results.get('data_stats'),
            'final_performance': training_results.get('final_performance'),
            'action_mappings': training_results.get('action_mappings')
        }
        
        with open(final_dir / "training_results.json", 'w') as f:
            json.dump(essential_results, f, indent=2)

        rl_logger.logger.info(f"Saved lean final model to {final_dir}")

    def load_model(self, model_path: str = None):
        """Load trained model"""
        if model_path is None:
            model_path = self.base_path / "final_model"
        else:
            model_path = Path(model_path)

        if not model_path.exists():
            rl_logger.logger.error(f"Model path does not exist: {model_path}")
            return None, None

        try:
            with open(model_path / "agent.pkl", 'rb') as f:
                agent = pickle.load(f)

            with open(model_path / "state_encoder.pkl", 'rb') as f:
                state_encoder = pickle.load(f)

            rl_logger.logger.info(f"Loaded model from {model_path}")
            return agent, state_encoder

        except Exception as e:
            rl_logger.logger.error(f"Failed to load model: {e}")
            return None, None
