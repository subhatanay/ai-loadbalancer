#!/usr/bin/env python3
import argparse
import sys
from pathlib import Path

# Add project root to path
sys.path.append(str(Path(__file__).parent.parent))

from offline_training.offline_trainer import OfflineTrainer
from utils.rl_logger import rl_logger

def main():
    parser = argparse.ArgumentParser(description='Run offline RL training')
    parser.add_argument('--experience-log', required=True,
                        help='Path to experience log file from RL collector')
    parser.add_argument('--max-experiences', type=int, default=None,
                        help='Maximum number of experiences to load')
    parser.add_argument('--validation-split', type=float, default=0.2,
                        help='Fraction of data for validation')
    parser.add_argument('--no-checkpoints', action='store_true',
                        help='Disable checkpoint saving')

    args = parser.parse_args()

    # Validate input file
    if not Path(args.experience_log).exists():
        rl_logger.logger.error(f"Experience log file not found: {args.experience_log}")
        return 1

    # Initialize trainer
    trainer = OfflineTrainer(args.experience_log)

    # Run training
    try:
        results = trainer.train(
            max_experiences=args.max_experiences,
            validation_split=args.validation_split,
            save_checkpoints=not args.no_checkpoints
        )

        if results['status'] == 'completed':
            rl_logger.logger.info("Training completed successfully!")
            rl_logger.logger.info(f"Final validation accuracy: {results['final_performance']['validation_accuracy']:.3f}")
            rl_logger.logger.info(f"Q-table size: {results['final_performance']['q_table_size']}")

            # Save final model
            trainer.persistence.save_final_model(
                trainer.agent,
                trainer.state_encoder,
                results
            )

            return 0
        else:
            rl_logger.logger.error(f"Training failed: {results.get('reason', 'unknown')}")
            return 1

    except Exception as e:
        rl_logger.logger.error(f"Training failed with exception: {e}", exc_info=True)
        return 1

if __name__ == "__main__":
    sys.exit(main())
