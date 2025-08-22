import pickle
import json
from pathlib import Path
from typing import Dict, Any, Optional
import tempfile
import shutil
from datetime import datetime

from utils.rl_logger import rl_logger

class ModelPersistence:
    """
    Handles saving and loading of RL agent models and state.
    Implements atomic saves and backup management.
    """

    def __init__(self):
        self.backup_count = 5

    def save_model(self, model_to_save: Any, file_path: str):
        """
        Save the model object directly using pickle with an atomic write.
        This can be the entire agent or a lean dictionary.
        """
        try:
            file_path = Path(file_path)
            file_path.parent.mkdir(parents=True, exist_ok=True)

            with tempfile.NamedTemporaryFile(mode='wb', dir=file_path.parent, delete=False) as temp_file:
                pickle.dump(model_to_save, temp_file)
                temp_path = temp_file.name
            
            shutil.move(temp_path, file_path)
            self._create_backup(file_path)
            rl_logger.logger.info(f"Model saved successfully to {file_path}")

        except Exception as e:
            rl_logger.log_error(f"Failed to save model to {file_path}", e)
            if 'temp_path' in locals() and Path(temp_path).exists():
                Path(temp_path).unlink()

    def load_model(self, file_path: str) -> Optional[Any]:
        """
        Load a pickled model object directly from a file.
        Returns the loaded object, which could be an agent or a dictionary.
        """
        try:
            file_path = Path(file_path)
            if not file_path.exists():
                rl_logger.logger.warning(f"Model file not found: {file_path}")
                return None

            with open(file_path, 'rb') as f:
                loaded_object = pickle.load(f)
            
            rl_logger.logger.info(f"Model object loaded successfully from {file_path}")
            return loaded_object

        except Exception as e:
            rl_logger.log_error(f"Failed to load model from {file_path}", e)
            backup_data = self._try_load_from_backup(file_path)
            if backup_data:
                rl_logger.logger.info("Successfully loaded model from backup.")
                return backup_data
            return None

    def _create_backup(self, file_path: Path):
        """Create backup of saved model"""
        try:
            backup_dir = file_path.parent / 'backups'
            backup_dir.mkdir(exist_ok=True)

            # Create timestamped backup
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            backup_name = f"{file_path.stem}_{timestamp}.pkl"
            backup_path = backup_dir / backup_name

            shutil.copy2(file_path, backup_path)

            # Clean old backups
            self._cleanup_old_backups(backup_dir, file_path.stem)

        except Exception as e:
            rl_logger.log_error("Failed to create backup", e)

    def _cleanup_old_backups(self, backup_dir: Path, base_name: str):
        """Remove old backup files, keeping only the most recent ones"""
        try:
            # Find all backup files for this model
            pattern = f"{base_name}_*.pkl"
            backup_files = list(backup_dir.glob(pattern))

            # Sort by modification time (newest first)
            backup_files.sort(key=lambda x: x.stat().st_mtime, reverse=True)

            # Remove old backups beyond the limit
            for old_backup in backup_files[self.backup_count:]:
                old_backup.unlink()
                rl_logger.logger.debug(f"Removed old backup: {old_backup}")

        except Exception as e:
            rl_logger.log_error("Failed to cleanup old backups", e)

    def _try_load_from_backup(self, original_path: Path) -> Optional[Dict[str, Any]]:
        """Try to load from most recent backup"""
        try:
            backup_dir = original_path.parent / 'backups'
            if not backup_dir.exists():
                return None

            # Find most recent backup
            pattern = f"{original_path.stem}_*.pkl"
            backup_files = list(backup_dir.glob(pattern))

            if not backup_files:
                return None

            # Sort by modification time (newest first)
            backup_files.sort(key=lambda x: x.stat().st_mtime, reverse=True)

            # Try to load most recent backup
            most_recent = backup_files[0]
            return self.load_model(str(most_recent))

        except Exception as e:
            rl_logger.log_error("Failed to load from backup", e)
            return None

    def export_model_summary(self, model_data: Dict[str, Any], export_path: str):
        """Export human-readable model summary"""
        try:
            summary = {
                'training_info': {
                    'episodes_completed': model_data.get('episode_count', 0),
                    'current_epsilon': model_data.get('current_epsilon', 0),
                    'q_table_size': len(model_data.get('q_table', {}))
                },
                'performance': {
                    'episode_rewards': model_data.get('episode_rewards', [])[-10:],  # Last 10
                    'training_statistics': model_data.get('training_statistics', {})
                },
                'export_timestamp': datetime.now().isoformat()
            }

            export_path = Path(export_path)
            export_path.parent.mkdir(parents=True, exist_ok=True)

            with open(export_path, 'w') as f:
                json.dump(summary, f, indent=2, default=str)

            rl_logger.logger.info(f"Model summary exported to {export_path}")

        except Exception as e:
            rl_logger.log_error(f"Failed to export model summary to {export_path}", e)
