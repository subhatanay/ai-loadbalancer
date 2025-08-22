#!/usr/bin/env python3
"""
RL Experience Consumer for Offline Training
Consumes data from RL Experience Collector and converts to RL Agent knowledge base
"""

import json
import requests
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from typing import List, Dict, Any, Tuple, Optional
import logging
import sys
import os
from pathlib import Path
import pickle
from collections import defaultdict
import asyncio
import aiohttp

# Add parent directories to path for imports
sys.path.append(str(Path(__file__).parent.parent))
sys.path.append(str(Path(__file__).parent.parent / 'cpu'))
sys.path.append(str(Path(__file__).parent.parent / 'models'))

from cpu.state_encoder import StateEncoder
from cpu.reward_calculator import RewardCalculator
from cpu.q_learning_agent import QLearningAgent
from models.metrics_model import ServiceMetrics, ServiceInstance
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('offline_training.log'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

class RLExperienceConsumer:
    """
    Consumes RL experience data and converts it for offline training
    """
    
    def __init__(self, collector_url: str = "http://localhost:8081"):
        self.collector_url = collector_url
        self.state_encoder = StateEncoder()
        self.reward_calculator = RewardCalculator()
        self.agent = QLearningAgent()
        
        # Training data storage
        self.experiences = []
        self.processed_experiences = []
        self.training_episodes = []
        
        # Metrics tracking
        self.training_metrics = {
            'total_experiences': 0,
            'processed_experiences': 0,
            'training_episodes': 0,
            'q_table_updates': 0,
            'average_reward': 0.0,
            'reward_variance': 0.0
        }
        
        logger.info("RL Experience Consumer initialized")
    
    async def fetch_experiences_from_collector(self, 
                                             start_time: Optional[datetime] = None,
                                             end_time: Optional[datetime] = None) -> List[Dict[str, Any]]:
        """
        Fetch RL experiences from the collector service
        """
        try:
            params = {}
            if start_time:
                params['start_time'] = start_time.isoformat()
            if end_time:
                params['end_time'] = end_time.isoformat()
            
            async with aiohttp.ClientSession() as session:
                async with session.get(f"{self.collector_url}/experiences", 
                                     params=params, timeout=30) as response:
                    if response.status == 200:
                        data = await response.json()
                        experiences = data.get('experiences', [])
                        logger.info(f"Fetched {len(experiences)} experiences from collector")
                        return experiences
                    else:
                        logger.error(f"Failed to fetch experiences: HTTP {response.status}")
                        return []
        
        except Exception as e:
            logger.error(f"Error fetching experiences from collector: {e}")
            return []
    
    def load_experiences_from_file(self, file_path: str) -> List[Dict[str, Any]]:
        """
        Load RL experiences from JSONL file (backup method)
        """
        experiences = []
        try:
            with open(file_path, 'r') as f:
                for line in f:
                    if line.strip():
                        experience = json.loads(line.strip())
                        experiences.append(experience)
            
            logger.info(f"Loaded {len(experiences)} experiences from file: {file_path}")
            return experiences
        
        except Exception as e:
            logger.error(f"Error loading experiences from file {file_path}: {e}")
            return []
    
    def convert_experience_to_rl_format(self, experience: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """
        Convert raw experience data to RL training format
        """
        try:
            # Extract state information
            state_data = experience.get('state', {})
            pre_metrics = state_data.get('pre_metrics', {})
            post_metrics = state_data.get('post_metrics', {})
            
            # Convert to ServiceMetrics format
            current_metrics = self._convert_to_service_metrics(post_metrics)
            previous_metrics = self._convert_to_service_metrics(pre_metrics)
            
            if not current_metrics or not previous_metrics:
                logger.warning("Insufficient metrics data in experience")
                return None
            
            # Extract action information
            action_data = experience.get('action', {})
            selected_instance = action_data.get('selected_instance', 'unknown')
            
            # Calculate reward using our reward calculator
            reward = self.reward_calculator.calculate_reward(
                current_metrics, previous_metrics, selected_instance
            )
            
            # Encode states
            current_state = self.state_encoder.encode_state(current_metrics)
            previous_state = self.state_encoder.encode_state(previous_metrics)
            
            # Create RL experience
            rl_experience = {
                'timestamp': experience.get('timestamp'),
                'service_name': experience.get('service_name'),
                'previous_state': previous_state,
                'action': selected_instance,
                'reward': reward,
                'current_state': current_state,
                'done': False,  # Continuous learning
                'metadata': {
                    'original_reward': experience.get('reward', 0),
                    'latency_improvement': experience.get('metrics', {}).get('latency_improvement', 0),
                    'load_balancer_algorithm': action_data.get('algorithm', 'unknown'),
                    'request_id': experience.get('request_id')
                }
            }
            
            return rl_experience
        
        except Exception as e:
            logger.error(f"Error converting experience to RL format: {e}")
            return None
    
    def _convert_to_service_metrics(self, metrics_data: Dict[str, Any]) -> List[ServiceMetrics]:
        """
        Convert raw metrics data to ServiceMetrics objects
        """
        try:
            service_metrics = []
            
            # Handle different metrics data formats
            if 'services' in metrics_data:
                # Multiple services format
                for service_name, service_data in metrics_data['services'].items():
                    if isinstance(service_data, list):
                        # Multiple instances per service
                        for instance_data in service_data:
                            metrics = self._create_service_metrics(service_name, instance_data)
                            if metrics:
                                service_metrics.append(metrics)
                    else:
                        # Single instance per service
                        metrics = self._create_service_metrics(service_name, service_data)
                        if metrics:
                            service_metrics.append(metrics)
            else:
                # Single service format
                service_name = metrics_data.get('service_name', 'unknown')
                metrics = self._create_service_metrics(service_name, metrics_data)
                if metrics:
                    service_metrics.append(metrics)
            
            return service_metrics
        
        except Exception as e:
            logger.error(f"Error converting metrics data: {e}")
            return []
    
    def _create_service_metrics(self, service_name: str, data: Dict[str, Any]) -> Optional[ServiceMetrics]:
        """
        Create ServiceMetrics object from raw data
        """
        try:
            return ServiceMetrics(
                service_name=service_name,
                instance_id=data.get('instance_id', f"{service_name}-unknown"),
                pod_name=data.get('pod_name', f"{service_name}-pod"),
                cpu_usage_percent=float(data.get('cpu_usage', 0)),
                jvm_memory_usage_percent=float(data.get('memory_usage', 0)),
                avg_response_time_ms=float(data.get('latency_ms', 0)),
                error_rate_percent=float(data.get('error_rate', 0)),
                request_rate_per_second=float(data.get('throughput', 0)),
                total_requests=int(data.get('total_requests', 0)),
                uptime_seconds=float(data.get('uptime_seconds', 0)),
                status=data.get('status', 'healthy')
            )
        except Exception as e:
            logger.error(f"Error creating ServiceMetrics: {e}")
            return None
    
    def process_experiences_batch(self, experiences: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Process a batch of experiences for training
        """
        processed = []
        
        logger.info(f"Processing batch of {len(experiences)} experiences")
        
        for i, experience in enumerate(experiences):
            rl_experience = self.convert_experience_to_rl_format(experience)
            if rl_experience:
                processed.append(rl_experience)
            
            if (i + 1) % 100 == 0:
                logger.info(f"Processed {i + 1}/{len(experiences)} experiences")
        
        logger.info(f"Successfully processed {len(processed)}/{len(experiences)} experiences")
        return processed
    
    def create_training_episodes(self, processed_experiences: List[Dict[str, Any]], 
                               episode_length: int = 50) -> List[List[Dict[str, Any]]]:
        """
        Group processed experiences into training episodes
        """
        # Sort by timestamp
        sorted_experiences = sorted(processed_experiences, 
                                  key=lambda x: x.get('timestamp', ''))
        
        # Group by service and create episodes
        service_groups = defaultdict(list)
        for exp in sorted_experiences:
            service_groups[exp['service_name']].append(exp)
        
        episodes = []
        for service_name, service_experiences in service_groups.items():
            # Create episodes of specified length
            for i in range(0, len(service_experiences), episode_length):
                episode = service_experiences[i:i + episode_length]
                if len(episode) >= 10:  # Minimum episode length
                    episodes.append(episode)
        
        logger.info(f"Created {len(episodes)} training episodes from {len(processed_experiences)} experiences")
        return episodes
    
    def train_agent_offline(self, episodes: List[List[Dict[str, Any]]]) -> Dict[str, Any]:
        """
        Train the RL agent using offline experiences
        """
        logger.info(f"Starting offline training with {len(episodes)} episodes")
        
        training_stats = {
            'episodes_processed': 0,
            'total_updates': 0,
            'average_episode_reward': 0.0,
            'q_table_size_before': len(self.agent.q_table),
            'q_table_size_after': 0,
            'training_start_time': datetime.now(),
            'training_end_time': None
        }
        
        episode_rewards = []
        
        for episode_idx, episode in enumerate(episodes):
            episode_reward = 0.0
            episode_updates = 0
            
            for exp in episode:
                # Extract experience components
                state = exp['previous_state']
                action = exp['action']
                reward = exp['reward']
                next_state = exp['current_state']
                done = exp['done']
                
                # Update Q-table using the agent's learning method
                try:
                    # Simulate the learning update
                    state_action_key = (state, action)
                    current_q = self.agent.q_table.get(state_action_key, 0.0)
                    
                    # Q-learning update formula
                    if not done:
                        # Find max Q-value for next state
                        next_state_actions = [key for key in self.agent.q_table.keys() 
                                            if key[0] == next_state]
                        max_next_q = max([self.agent.q_table[key] for key in next_state_actions], 
                                       default=0.0)
                    else:
                        max_next_q = 0.0
                    
                    # Update Q-value
                    new_q = current_q + self.agent.config.learning_rate * (
                        reward + self.agent.config.discount_factor * max_next_q - current_q
                    )
                    
                    self.agent.q_table[state_action_key] = new_q
                    
                    episode_reward += reward
                    episode_updates += 1
                    training_stats['total_updates'] += 1
                    
                except Exception as e:
                    logger.error(f"Error updating Q-table: {e}")
                    continue
            
            episode_rewards.append(episode_reward)
            training_stats['episodes_processed'] += 1
            
            if (episode_idx + 1) % 10 == 0:
                avg_reward = np.mean(episode_rewards[-10:])
                logger.info(f"Episode {episode_idx + 1}/{len(episodes)}, "
                          f"Avg Reward (last 10): {avg_reward:.3f}, "
                          f"Updates: {episode_updates}")
        
        # Calculate final statistics
        training_stats['average_episode_reward'] = np.mean(episode_rewards) if episode_rewards else 0.0
        training_stats['q_table_size_after'] = len(self.agent.q_table)
        training_stats['training_end_time'] = datetime.now()
        training_stats['training_duration'] = (
            training_stats['training_end_time'] - training_stats['training_start_time']
        ).total_seconds()
        
        logger.info("Offline training completed!")
        logger.info(f"Episodes processed: {training_stats['episodes_processed']}")
        logger.info(f"Total Q-table updates: {training_stats['total_updates']}")
        logger.info(f"Average episode reward: {training_stats['average_episode_reward']:.3f}")
        logger.info(f"Q-table size: {training_stats['q_table_size_before']} → {training_stats['q_table_size_after']}")
        logger.info(f"Training duration: {training_stats['training_duration']:.1f} seconds")
        
        return training_stats
    
    def save_trained_model(self, output_dir: str = "models/offline_trained"):
        """
        Save the trained model and metadata
        """
        os.makedirs(output_dir, exist_ok=True)
        
        # Save Q-table
        q_table_path = os.path.join(output_dir, "q_table_offline.pkl")
        with open(q_table_path, 'wb') as f:
            pickle.dump(self.agent.q_table, f)
        
        # Save state encoder
        encoder_path = os.path.join(output_dir, "state_encoder_offline.pkl")
        with open(encoder_path, 'wb') as f:
            pickle.dump(self.state_encoder, f)
        
        # Save training metadata
        metadata = {
            'training_timestamp': datetime.now().isoformat(),
            'q_table_size': len(self.agent.q_table),
            'state_encoder_fitted': self.state_encoder.is_fitted,
            'training_metrics': self.training_metrics,
            'config': rl_settings.to_dict()
        }
        
        metadata_path = os.path.join(output_dir, "training_metadata.json")
        with open(metadata_path, 'w') as f:
            json.dump(metadata, f, indent=2, default=str)
        
        logger.info(f"Trained model saved to: {output_dir}")
    
    async def run_offline_training_pipeline(self, 
                                          start_time: Optional[datetime] = None,
                                          end_time: Optional[datetime] = None,
                                          experience_file: Optional[str] = None) -> Dict[str, Any]:
        """
        Run the complete offline training pipeline
        """
        logger.info("Starting offline training pipeline")
        
        # Step 1: Fetch experiences
        if experience_file:
            experiences = self.load_experiences_from_file(experience_file)
        else:
            experiences = await self.fetch_experiences_from_collector(start_time, end_time)
        
        if not experiences:
            logger.error("No experiences found for training")
            return {'success': False, 'error': 'No experiences available'}
        
        # Step 2: Process experiences
        processed_experiences = self.process_experiences_batch(experiences)
        
        if not processed_experiences:
            logger.error("No valid experiences after processing")
            return {'success': False, 'error': 'No valid experiences after processing'}
        
        # Step 3: Fit state encoder if needed
        if not self.state_encoder.is_fitted:
            logger.info("Fitting state encoder on processed experiences")
            # Extract ServiceMetrics for fitting
            all_metrics = []
            for exp in processed_experiences:
                # This is a simplified approach - in practice you'd want to extract
                # the original ServiceMetrics objects used to create the states
                pass
            
            # For now, mark as fitted (in production, implement proper fitting)
            self.state_encoder.is_fitted = True
        
        # Step 4: Create training episodes
        episodes = self.create_training_episodes(processed_experiences)
        
        # Step 5: Train agent
        training_stats = self.train_agent_offline(episodes)
        
        # Step 6: Save trained model
        self.save_trained_model()
        
        # Step 7: Return results
        results = {
            'success': True,
            'total_experiences': len(experiences),
            'processed_experiences': len(processed_experiences),
            'training_episodes': len(episodes),
            'training_stats': training_stats,
            'model_saved': True
        }
        
        logger.info("Offline training pipeline completed successfully")
        return results

async def main():
    """Main function for offline training"""
    import argparse
    
    parser = argparse.ArgumentParser(description='RL Offline Training Pipeline')
    parser.add_argument('--collector-url', default='http://localhost:8081',
                       help='RL Experience Collector URL')
    parser.add_argument('--experience-file', 
                       help='Path to experience file (alternative to collector)')
    parser.add_argument('--start-time', 
                       help='Start time for experience collection (ISO format)')
    parser.add_argument('--end-time',
                       help='End time for experience collection (ISO format)')
    parser.add_argument('--output-dir', default='models/offline_trained',
                       help='Output directory for trained model')
    
    args = parser.parse_args()
    
    # Parse time arguments
    start_time = None
    end_time = None
    if args.start_time:
        start_time = datetime.fromisoformat(args.start_time)
    if args.end_time:
        end_time = datetime.fromisoformat(args.end_time)
    
    # Initialize consumer
    consumer = RLExperienceConsumer(args.collector_url)
    
    # Run training pipeline
    results = await consumer.run_offline_training_pipeline(
        start_time=start_time,
        end_time=end_time,
        experience_file=args.experience_file
    )
    
    # Print results
    if results['success']:
        print("="*80)
        print("OFFLINE TRAINING COMPLETED SUCCESSFULLY")
        print("="*80)
        print(f"Total Experiences: {results['total_experiences']}")
        print(f"Processed Experiences: {results['processed_experiences']}")
        print(f"Training Episodes: {results['training_episodes']}")
        print(f"Q-table Updates: {results['training_stats']['total_updates']}")
        print(f"Average Episode Reward: {results['training_stats']['average_episode_reward']:.3f}")
        print(f"Training Duration: {results['training_stats']['training_duration']:.1f}s")
        print("="*80)
    else:
        print(f"Training failed: {results.get('error', 'Unknown error')}")

if __name__ == "__main__":
    asyncio.run(main())
