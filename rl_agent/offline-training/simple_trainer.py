#!/usr/bin/env python3
"""
Simplified RL trainer that works with state-only data without external dependencies.
"""

import json
import sys
import os
from pathlib import Path
from typing import List, Dict, Any
from datetime import datetime
import pickle
from collections import defaultdict

class SimpleQLearningAgent:
    """Simple Q-learning agent without external dependencies"""
    
    def __init__(self, learning_rate=0.1, discount_factor=0.95, epsilon=0.1):
        self.q_table = defaultdict(lambda: defaultdict(float))
        self.learning_rate = learning_rate
        self.discount_factor = discount_factor
        self.epsilon = epsilon
        
    def update(self, state, action, reward, next_state):
        """Update Q-table using Q-learning formula"""
        # Get current Q-value
        current_q = self.q_table[state][action]
        
        # Get max Q-value for next state
        max_next_q = max(self.q_table[next_state].values()) if self.q_table[next_state] else 0
        
        # Q-learning update
        new_q = current_q + self.learning_rate * (reward + self.discount_factor * max_next_q - current_q)
        self.q_table[state][action] = new_q
        
    def get_best_action(self, state, available_actions):
        """Get best action for a given state"""
        if not available_actions:
            return None
            
        if state not in self.q_table:
            return available_actions[0]  # Random choice if state not seen
            
        # Get action with highest Q-value
        best_action = max(available_actions, 
                         key=lambda a: self.q_table[state].get(a, 0))
        return best_action

class SimpleTrainer:
    """Simplified trainer that works with state-only data"""
    
    def __init__(self, data_file: str):
        self.data_file = Path(data_file)
        self.agent = SimpleQLearningAgent()
        
    def load_states(self, max_states: int = None) -> List[Dict]:
        """Load state snapshots from collector data"""
        states = []
        count = 0
        
        print(f"📊 Loading states from {self.data_file}")
        
        with open(self.data_file, 'r') as f:
            for line_num, line in enumerate(f, 1):
                if max_states and count >= max_states:
                    break
                    
                try:
                    data = json.loads(line.strip())
                    if 'state' in data and 'metrics' in data['state']:
                        states.append(data['state'])
                        count += 1
                except (json.JSONDecodeError, KeyError) as e:
                    if line_num <= 10:  # Only show first 10 errors
                        print(f"⚠️  Skipping invalid line {line_num}: {e}")
                    continue
                    
        print(f"✅ Loaded {len(states)} valid states")
        return states
    
    def create_synthetic_experiences(self, states: List[Dict]) -> List[Dict]:
        """Create synthetic RL experiences from consecutive states"""
        experiences = []
        
        print("🔧 Creating synthetic RL experiences...")
        
        for i in range(len(states) - 1):
            current_state = states[i]
            next_state = states[i + 1]
            
            # Extract metrics
            current_metrics = current_state['metrics']
            next_metrics = next_state['metrics']
            
            # Choose a synthetic action (simulate load balancer decision)
            action = self._choose_synthetic_action(current_metrics)
            
            # Calculate reward based on performance improvement
            reward = self._calculate_reward(current_metrics, next_metrics, action)
            
            experience = {
                'state': current_metrics,
                'action': action,
                'reward': reward,
                'next_state': next_metrics,
                'metadata': {
                    'synthetic': True,
                    'timestamp': current_state.get('timestamp', ''),
                    'next_timestamp': next_state.get('timestamp', '')
                }
            }
            
            experiences.append(experience)
            
        print(f"✅ Created {len(experiences)} synthetic experiences")
        return experiences
    
    def _choose_synthetic_action(self, metrics: Dict) -> str:
        """Choose synthetic action based on current metrics"""
        # Find the pod with best performance (lowest latency + CPU)
        best_pod = None
        best_score = float('inf')
        
        for pod_name, pod_metrics in metrics.items():
            if pod_metrics.get('uptimeSeconds', 0) > 0:  # Only consider running pods
                cpu = pod_metrics.get('cpuUsagePercent', 100)
                latency = pod_metrics.get('avgResponseTimeMs', 1000)
                error_rate = pod_metrics.get('errorRatePercent', 100)
                
                # Simple scoring: lower is better
                score = cpu + latency + (error_rate * 10)
                
                if score < best_score:
                    best_score = score
                    best_pod = pod_name
        
        return best_pod or 'unknown-pod'
    
    def _calculate_reward(self, current_metrics: Dict, next_metrics: Dict, action: str) -> float:
        """Calculate reward based on performance change"""
        if action not in current_metrics or action not in next_metrics:
            return -0.1  # Penalty for invalid action
            
        current_pod = current_metrics[action]
        next_pod = next_metrics[action]
        
        # Reward based on latency improvement
        current_latency = current_pod.get('avgResponseTimeMs', 1000)
        next_latency = next_pod.get('avgResponseTimeMs', 1000)
        latency_improvement = (current_latency - next_latency) / max(current_latency, 1)
        
        # Reward based on error rate improvement
        current_errors = current_pod.get('errorRatePercent', 100)
        next_errors = next_pod.get('errorRatePercent', 100)
        error_improvement = (current_errors - next_errors) / max(current_errors + 1, 1)
        
        # Combined reward
        reward = (latency_improvement * 0.7) + (error_improvement * 0.3)
        
        # Normalize to [-1, 1] range
        return max(-1.0, min(1.0, reward))
    
    def _encode_state_simple(self, metrics: Dict) -> str:
        """Simple state encoding - create a hash from key metrics"""
        # Extract key metrics from all running pods
        running_pods = []
        for pod_name, pod_metrics in metrics.items():
            if pod_metrics.get('uptimeSeconds', 0) > 0:
                cpu = pod_metrics.get('cpuUsagePercent', 0)
                latency = pod_metrics.get('avgResponseTimeMs', 0)
                errors = pod_metrics.get('errorRatePercent', 0)
                
                # Discretize values into bins
                cpu_bin = min(int(cpu / 20), 4)  # 0-4 bins for CPU
                latency_bin = min(int(latency / 50), 4)  # 0-4 bins for latency
                error_bin = min(int(errors / 5), 4)  # 0-4 bins for errors
                
                running_pods.append(f"{pod_name[:8]}_{cpu_bin}_{latency_bin}_{error_bin}")
        
        # Create state string
        return "|".join(sorted(running_pods)[:5])  # Limit to top 5 pods
    
    def _save_model_simple(self, agent, results: Dict) -> Path:
        """Simple model saving"""
        model_dir = Path("models")
        model_dir.mkdir(exist_ok=True)
        
        model_path = model_dir / f"rl_model_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pkl"
        
        model_data = {
            'q_table': dict(agent.q_table),  # Convert defaultdict to regular dict
            'training_results': results,
            'timestamp': datetime.now().isoformat(),
            'agent_params': {
                'learning_rate': agent.learning_rate,
                'discount_factor': agent.discount_factor,
                'epsilon': agent.epsilon
            }
        }
        
        with open(model_path, 'wb') as f:
            pickle.dump(model_data, f)
        
        return model_path
    
    def train(self, max_states: int = 10000) -> Dict:
        """Train the RL agent with synthetic experiences"""
        print("🚀 Starting simplified RL training...")
        
        # Load states
        states = self.load_states(max_states)
        if len(states) < 2:
            print("❌ Need at least 2 states for training")
            return {'success': False, 'error': 'Insufficient data'}
        
        # Create synthetic experiences
        experiences = self.create_synthetic_experiences(states)
        
        # Train the agent
        total_reward = 0
        successful_updates = 0
        
        print("🎯 Training RL agent...")
        
        for i, exp in enumerate(experiences):
            try:
                # Simple state encoding - convert metrics dict to string
                state = self._encode_state_simple(exp['state'])
                next_state = self._encode_state_simple(exp['next_state'])
                action = exp['action']
                reward = exp['reward']
                
                # Update Q-table with simple encoding
                self.agent.update(state, action, reward, next_state)
                
                total_reward += reward
                successful_updates += 1
                
                if (i + 1) % 1000 == 0:
                    print(f"📈 Processed {i + 1}/{len(experiences)} experiences")
                    
            except Exception as e:
                if i < 10:  # Only show first 10 errors
                    print(f"⚠️  Error processing experience {i}: {e}")
                continue
        
        # Calculate performance metrics
        avg_reward = total_reward / max(successful_updates, 1)
        success_rate = successful_updates / len(experiences)
        
        results = {
            'success': True,
            'total_experiences': len(experiences),
            'successful_updates': successful_updates,
            'success_rate': success_rate,
            'average_reward': avg_reward,
            'total_reward': total_reward,
            'q_table_size': len(self.agent.q_table)
        }
        
        # Save the trained model
        try:
            model_path = self._save_model_simple(self.agent, results)
            results['model_path'] = str(model_path)
            print(f"💾 Model saved to: {model_path}")
        except Exception as e:
            print(f"⚠️  Failed to save model: {e}")
        
        print("✅ Training completed!")
        print(f"📊 Results: {results}")
        
        return results

if __name__ == "__main__":
    data_file = "../../load-testing/collector_experiences.jsonl"
    
    if not os.path.exists(data_file):
        print(f"❌ Data file not found: {data_file}")
        sys.exit(1)
    
    trainer = SimpleTrainer(data_file)
    results = trainer.train(max_states=30000)  # Start with 5K states for quick training
    
    if results['success']:
        print(f"🎉 Training successful! Average reward: {results['average_reward']:.4f}")
        print(f"📈 Success rate: {results['success_rate']:.2%}")
        print(f"🧠 Q-table size: {results['q_table_size']}")
    else:
        print(f"❌ Training failed: {results.get('error', 'Unknown error')}")
