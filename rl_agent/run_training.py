#!/usr/bin/env python3
"""
Simple training runner script
"""
import sys
import os
sys.path.append('.')

def main():
    try:
        print("🚀 Starting RL training...")
        
        # Import training components
        from offline_training.offline_trainer import OfflineTrainer
        print("✅ Imported OfflineTrainer")
        
        # Initialize trainer
        trainer = OfflineTrainer('data/rl_experiences.jsonl')
        print("✅ Trainer initialized")
        
        # Check if experience file exists and has data
        import os
        if os.path.exists('data/rl_experiences.jsonl'):
            with open('data/rl_experiences.jsonl', 'r') as f:
                lines = f.readlines()
                print(f"📊 Found {len(lines)} experiences")
        else:
            print("❌ Experience file not found")
            return
        
        # Run training
        print("🎯 Starting training process...")
        result = trainer.train()
        print("✅ Training completed successfully!")
        print("📈 Training results:", result)
        
        # Check if models were saved
        if os.path.exists('models/q_table.pkl'):
            print("✅ Q-table model saved")
        if os.path.exists('models/q_table_encoder.pkl'):
            print("✅ State encoder saved")
            
    except Exception as e:
        print(f"❌ Training failed: {str(e)}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
