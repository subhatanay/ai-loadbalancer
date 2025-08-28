import json
import os
from pathlib import Path
from typing import List, Dict, Any, Generator, Tuple
from datetime import datetime
from dataclasses import dataclass

import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))
from utils.rl_logger import rl_logger

@dataclass
class Experience:
    """Single RL experience tuple"""
    state: Dict[str, Any]
    action: str
    reward: float
    next_state: Dict[str, Any]
    metadata: Dict[str, Any]
    timestamp: datetime

class ExperienceLoader:
    """Loads and parses experience data from RL collector logs"""

    def __init__(self, log_file_path: str):
        self.log_file_path = Path(log_file_path)
        self.total_experiences = 0

    def load_experiences(self, max_experiences: int = None, offset: int = 0, limit: int = -1) -> Generator[Experience, None, None]:
        """
        Generator that yields Experience objects from the collector log file

        Args:
            max_experiences: Maximum number of experiences to load (None for all)
            offset: Number of experiences to skip from the beginning
            limit: Maximum number of experiences to yield (-1 for no limit)
        """
        if not self.log_file_path.exists():
            rl_logger.logger.error(f"Experience log file not found: {self.log_file_path}")
            return

        # Only log on first load to avoid spam
        if offset == 0 and limit == -1:
            rl_logger.logger.info(f"Loading experiences from {self.log_file_path}")

        count = 0
        yielded_count = 0
        processed_count = 0
        
        with open(self.log_file_path, 'r') as f:
            for line_num, line in enumerate(f, 1):
                try:
                    if max_experiences and processed_count >= max_experiences:
                        break
                    
                    # Skip lines until we reach the offset
                    if processed_count < offset:
                        processed_count += 1
                        continue
                    
                    # Stop if we've reached the limit
                    if limit != -1 and yielded_count >= limit:
                        break

                    data = json.loads(line.strip())

                    # Parse the experience data structure from collector
                    experience = Experience(
                        state=data['state']['metrics'],
                        action=data['action'],
                        reward=data['reward'],
                        next_state=data['next_state']['metrics'],
                        metadata=data.get('metadata', {}),
                        timestamp=self._parse_timestamp(data['state']['timestamp'])
                    )

                    yield experience
                    yielded_count += 1
                    processed_count += 1

                except (json.JSONDecodeError, KeyError, ValueError) as e:
                    rl_logger.logger.warning(f"Skipping invalid experience at line {line_num}: {e}")
                    processed_count += 1
                    continue

        self.total_experiences = yielded_count
        # Only log completion for significant loads to reduce spam
        if yielded_count > 1000:
            rl_logger.logger.info(f"Loaded {yielded_count} valid experiences from {self.log_file_path}")

    def _parse_timestamp(self, timestamp_str):
        """Parse timestamp string handling various formats including Z suffix"""
        if isinstance(timestamp_str, (int, float)):
            return datetime.fromtimestamp(timestamp_str)
        
        # Handle ISO format with Z suffix
        if timestamp_str.endswith('Z'):
            timestamp_str = timestamp_str[:-1] + '+00:00'
        
        try:
            return datetime.fromisoformat(timestamp_str)
        except ValueError:
            # Fallback to current time if parsing fails
            return datetime.now()

    def get_experience_statistics(self) -> Dict[str, Any]:
        """Get statistics about the loaded experiences"""
        actions = {}
        rewards = []

        for exp in self.load_experiences():
            actions[exp.action] = actions.get(exp.action, 0) + 1
            rewards.append(exp.reward)

        return {
            'total_experiences': len(rewards),
            'unique_actions': len(actions),
            'action_distribution': actions,
            'reward_stats': {
                'mean': sum(rewards) / len(rewards) if rewards else 0,
                'min': min(rewards) if rewards else 0,
                'max': max(rewards) if rewards else 0
            }
        }
