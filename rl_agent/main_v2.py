import asyncio
import argparse
from pathlib import Path
import sys

from cpu.training_loop import RLTrainingLoop
from config.rl_settings import rl_settings
from utils.rl_logger import rl_logger

async def main():
    """Main application entry point"""
    parser = argparse.ArgumentParser(description="RL Agent for Load Balancing")
    parser.add_argument('--mode', choices=['train', 'inference'], default='train',
                        help='Run mode: train or inference')
    parser.add_argument('--episodes', type=int, default=None,
                        help='Number of training episodes')
    parser.add_argument('--config', type=str, default=None,
                        help='Path to config file')

    args = parser.parse_args()

    # Load custom config if provided
    if args.config:
        # Implementation for loading custom config
        pass

    rl_logger.logger.info("🚀 Starting RL Agent for Load Balancing")
    rl_logger.logger.info(f"Mode: {args.mode}")

    # Initialize training loop
    training_loop = RLTrainingLoop()

    try:
        # Initialize components
        await training_loop.initialize()

        if args.mode == 'train':
            # Run training
            await training_loop.run_training(max_episodes=args.episodes)
        else:
            # Run inference
            await training_loop.run_inference_mode()

    except KeyboardInterrupt:
        rl_logger.logger.info("Received interrupt signal, shutting down gracefully")
    except Exception as e:
        rl_logger.log_error("Application failed", e)
        sys.exit(1)
    finally:
        rl_logger.logger.info("RL Agent shutdown complete")

if __name__ == "__main__":
    asyncio.run(main())
