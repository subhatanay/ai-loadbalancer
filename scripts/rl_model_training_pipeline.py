#!/usr/bin/env python3
"""
RL Model Training Pipeline
Complete pipeline for training AI load balancer models from collected experiences
"""

import os
import sys
import json
import subprocess
import time
from pathlib import Path
from datetime import datetime
import argparse

class RLModelTrainingPipeline:
    def __init__(self, base_dir="/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer"):
        self.base_dir = Path(base_dir)
        self.rl_agent_dir = self.base_dir / "rl_agent"
        self.training_dir = self.rl_agent_dir / "offline-training"
        self.models_dir = self.rl_agent_dir / "models"
        self.experience_file = "exported_experiences.jsonl"
        
    def check_prerequisites(self):
        """Check if all prerequisites are met for training"""
        print("🔍 Checking prerequisites...")
        
        issues = []
        
        # Check if experience file exists
        if not Path(self.experience_file).exists():
            issues.append(f"❌ Experience file not found: {self.experience_file}")
            issues.append("   Run verify_rl_integration.py first to export experiences")
        
        # Check if RL agent directory exists
        if not self.rl_agent_dir.exists():
            issues.append(f"❌ RL agent directory not found: {self.rl_agent_dir}")
        
        # Check if offline training scripts exist
        required_files = [
            "offline_trainer.py",
            "experience_loader.py", 
            "data_preprocessor.py"
        ]
        
        for file in required_files:
            if not (self.training_dir / file).exists():
                issues.append(f"❌ Missing training script: {file}")
        
        if issues:
            print("\n".join(issues))
            return False
        
        print("✅ All prerequisites met")
        return True
    
    def setup_training_environment(self):
        """Setup Python environment for training"""
        print("🔧 Setting up training environment...")
        
        try:
            # Check if virtual environment exists
            venv_dir = self.rl_agent_dir / "rl-agent-env"
            if not venv_dir.exists():
                print("Creating Python virtual environment...")
                subprocess.run([
                    "python3", "-m", "venv", str(venv_dir)
                ], check=True, cwd=self.rl_agent_dir)
            
            # Install requirements
            pip_path = venv_dir / "bin" / "pip"
            requirements_file = self.rl_agent_dir / "requirements.txt"
            
            if requirements_file.exists():
                print("Installing Python dependencies...")
                subprocess.run([
                    str(pip_path), "install", "-r", str(requirements_file)
                ], check=True)
            
            print("✅ Training environment ready")
            return True
            
        except subprocess.CalledProcessError as e:
            print(f"❌ Failed to setup environment: {e}")
            return False
    
    def analyze_experience_data(self):
        """Analyze collected experience data"""
        print("📊 Analyzing experience data...")
        
        try:
            with open(self.experience_file, 'r') as f:
                experiences = [json.loads(line) for line in f]
            
            print(f"📈 Total experiences: {len(experiences)}")
            
            if len(experiences) == 0:
                print("❌ No experiences found. Cannot train model.")
                return False
            
            # Analyze actions
            actions = [exp.get('action', 'unknown') for exp in experiences]
            action_counts = {}
            for action in actions:
                action_counts[action] = action_counts.get(action, 0) + 1
            
            print("🎯 Action distribution:")
            for action, count in action_counts.items():
                print(f"   {action}: {count} times")
            
            # Analyze rewards
            rewards = [exp.get('reward', 0) for exp in experiences]
            avg_reward = sum(rewards) / len(rewards)
            min_reward = min(rewards)
            max_reward = max(rewards)
            
            print(f"🏆 Reward statistics:")
            print(f"   Average: {avg_reward:.4f}")
            print(f"   Min: {min_reward:.4f}")
            print(f"   Max: {max_reward:.4f}")
            
            # Check data quality
            if len(experiences) < 100:
                print("⚠️  Warning: Less than 100 experiences. Consider collecting more data.")
            
            if len(set(actions)) < 2:
                print("⚠️  Warning: Only one action type found. Model may not learn effectively.")
            
            return True
            
        except Exception as e:
            print(f"❌ Error analyzing data: {e}")
            return False
    
    def run_offline_training(self, training_mode="container"):
        """Run offline training"""
        print(f"🚀 Starting offline training (mode: {training_mode})...")
        
        if training_mode == "container":
            return self._run_container_training()
        else:
            return self._run_local_training()
    
    def _run_container_training(self):
        """Run training inside RL agent container"""
        try:
            print("🐳 Running training in RL agent container...")
            
            # Copy experience file to RL agent container
            subprocess.run([
                "kubectl", "cp", "-n", "ai-loadbalancer",
                self.experience_file,
                "deployment/rl-agent:app/data/experiences.jsonl"
            ], check=True)
            
            # Execute training inside container
            result = subprocess.run([
                "kubectl", "exec", "-n", "ai-loadbalancer",
                "deployment/rl-agent", "--",
                "python", "offline-training/offline_trainer.py",
                "--experience-file", "data/experiences.jsonl",
                "--output-dir", "models/trained",
                "--max-episodes", "1000"
            ], capture_output=True, text=True, timeout=1800)  # 30 min timeout
            
            if result.returncode == 0:
                print("✅ Container training completed successfully")
                print("Training output:")
                print(result.stdout)
                return True
            else:
                print(f"❌ Container training failed: {result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            print("❌ Training timed out (30 minutes)")
            return False
        except Exception as e:
            print(f"❌ Container training error: {e}")
            return False
    
    def _run_local_training(self):
        """Run training locally outside container"""
        try:
            print("💻 Running training locally...")
            
            # Copy experience file to RL agent directory
            local_exp_file = self.rl_agent_dir / "data" / "experiences.jsonl"
            local_exp_file.parent.mkdir(exist_ok=True)
            
            subprocess.run([
                "cp", self.experience_file, str(local_exp_file)
            ], check=True)
            
            # Run training with virtual environment
            venv_python = self.rl_agent_dir / "rl-agent-env" / "bin" / "python"
            
            result = subprocess.run([
                str(venv_python), "offline-training/offline_trainer.py",
                "--experience-file", "data/experiences.jsonl",
                "--output-dir", "models/trained",
                "--max-episodes", "1000"
            ], cwd=self.rl_agent_dir, capture_output=True, text=True, timeout=1800)
            
            if result.returncode == 0:
                print("✅ Local training completed successfully")
                print("Training output:")
                print(result.stdout)
                return True
            else:
                print(f"❌ Local training failed: {result.stderr}")
                return False
                
        except subprocess.TimeoutExpired:
            print("❌ Training timed out (30 minutes)")
            return False
        except Exception as e:
            print(f"❌ Local training error: {e}")
            return False
    
    def deploy_trained_model(self):
        """Deploy trained model back to RL agent container"""
        print("🚀 Deploying trained model...")
        
        try:
            # Find latest trained model
            models_dir = self.rl_agent_dir / "models" / "trained"
            if not models_dir.exists():
                print("❌ No trained models found")
                return False
            
            model_files = list(models_dir.glob("*.pkl")) + list(models_dir.glob("*.pt"))
            if not model_files:
                print("❌ No model files found")
                return False
            
            latest_model = max(model_files, key=lambda x: x.stat().st_mtime)
            print(f"📦 Deploying model: {latest_model.name}")
            
            # Copy model to RL agent container
            subprocess.run([
                "kubectl", "cp", "-n", "ai-loadbalancer",
                str(latest_model),
                f"deployment/rl-agent:app/models/current_model.pkl"
            ], check=True)
            
            # Restart RL agent to load new model
            subprocess.run([
                "kubectl", "rollout", "restart", "-n", "ai-loadbalancer",
                "deployment/rl-agent"
            ], check=True)
            
            print("✅ Model deployed and RL agent restarted")
            return True
            
        except Exception as e:
            print(f"❌ Model deployment failed: {e}")
            return False

def main():
    parser = argparse.ArgumentParser(description="RL Model Training Pipeline")
    parser.add_argument("--mode", choices=["container", "local"], default="local",
                       help="Training mode: container (inside k8s) or local (outside)")
    parser.add_argument("--skip-analysis", action="store_true",
                       help="Skip data analysis step")
    parser.add_argument("--skip-deployment", action="store_true", 
                       help="Skip model deployment step")
    
    args = parser.parse_args()
    
    pipeline = RLModelTrainingPipeline()
    
    print("🎯 RL Model Training Pipeline")
    print("=" * 50)
    
    # Step 1: Check prerequisites
    if not pipeline.check_prerequisites():
        print("❌ Prerequisites not met. Exiting.")
        sys.exit(1)
    
    # Step 2: Setup environment (for local training)
    if args.mode == "local":
        if not pipeline.setup_training_environment():
            print("❌ Environment setup failed. Exiting.")
            sys.exit(1)
    
    # Step 3: Analyze data
    if not args.skip_analysis:
        if not pipeline.analyze_experience_data():
            print("❌ Data analysis failed. Exiting.")
            sys.exit(1)
    
    # Step 4: Run training
    if not pipeline.run_offline_training(args.mode):
        print("❌ Training failed. Exiting.")
        sys.exit(1)
    
    # Step 5: Deploy model
    if not args.skip_deployment:
        if not pipeline.deploy_trained_model():
            print("❌ Model deployment failed.")
            sys.exit(1)
    
    print("\n🎉 RL Model Training Pipeline Complete!")
    print("✅ Model has been trained and deployed")
    print("✅ RL agent is now using the new model")
    print("\n🔄 Next steps:")
    print("- Monitor load balancer performance")
    print("- Collect more experiences for continuous learning")
    print("- Run periodic retraining")

if __name__ == "__main__":
    main()
