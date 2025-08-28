import sys
import os
sys.path.append('..')
from offline_trainer import OfflineTrainer

# Use the collected data from the RL experience collector
data_file = '../data/rl_experiences.jsonl'

if not os.path.exists(data_file):
    print(f"❌ Error: Data file not found at {data_file}")
    sys.exit(1)

print(f"🚀 Starting offline RL training with {data_file}")
trainer = OfflineTrainer(data_file)
result = trainer.train(save_checkpoints=False)
print('✅ Training completed!')
print('📊 Results:', result)
