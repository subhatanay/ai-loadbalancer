#!/usr/bin/env python3
"""
RL Training Executor
Orchestrates comprehensive RL training data collection with multiple scenarios
Designed to generate diverse, high-quality offline training data for AI Load Balancer
"""

import argparse
import json
import logging
import os
import sys
import time
from datetime import datetime
from typing import Dict, List
import subprocess

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/rl_training_executor.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class RLTrainingExecutor:
    def __init__(self):
        """Initialize RL Training Executor"""
        self.start_time = datetime.now()
        self.execution_results = []
        
    def check_prerequisites(self) -> bool:
        """Check if all prerequisites are met for RL training"""
        logger.info("🔍 Checking prerequisites for RL training...")
        
        # Check if configuration files exist
        required_files = [
            'comprehensive_rl_training_config.json',
            'stress_test_rl_config.json',
            'enhanced_rl_training_load_test.py'
        ]
        
        missing_files = []
        for file in required_files:
            if not os.path.exists(file):
                missing_files.append(file)
        
        if missing_files:
            logger.error(f"❌ Missing required files: {missing_files}")
            return False
        
        # Check if load balancer is accessible
        try:
            import requests
            response = requests.get('http://localhost:8080/proxy/user-service/actuator/health', timeout=5)
            if response.status_code == 200:
                logger.info("✅ Load balancer is accessible")
            else:
                logger.warning(f"⚠️ Load balancer returned status {response.status_code}")
        except Exception as e:
            logger.error(f"❌ Cannot reach load balancer: {e}")
            return False
        
        logger.info("✅ All prerequisites met")
        return True
    
    def run_scenario(self, scenario_type: str, config_file: str, scenario_name: str) -> Dict:
        """Run a specific RL training scenario"""
        logger.info(f"🚀 Starting {scenario_type} scenario: {scenario_name}")
        
        start_time = time.time()
        
        try:
            # Prepare command
            cmd = [
                sys.executable,
                'enhanced_rl_training_load_test.py',
                '--config', config_file,
                '--scenario', scenario_name
            ]
            
            # Execute scenario
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=10800  # 3 hour timeout (increased from 2 hours to accommodate comprehensive scenarios)
            )
            
            duration = time.time() - start_time
            
            if result.returncode == 0:
                logger.info(f"✅ {scenario_type} completed successfully in {duration:.1f}s")
                return {
                    'scenario_type': scenario_type,
                    'scenario_name': scenario_name,
                    'success': True,
                    'duration': duration,
                    'stdout': result.stdout,
                    'stderr': result.stderr
                }
            else:
                logger.error(f"❌ {scenario_type} failed with return code {result.returncode}")
                logger.error(f"Error output: {result.stderr}")
                return {
                    'scenario_type': scenario_type,
                    'scenario_name': scenario_name,
                    'success': False,
                    'duration': duration,
                    'error': result.stderr,
                    'return_code': result.returncode
                }
                
        except subprocess.TimeoutExpired:
            logger.error(f"❌ {scenario_type} timed out after 2 hours")
            return {
                'scenario_type': scenario_type,
                'scenario_name': scenario_name,
                'success': False,
                'duration': 7200,
                'error': 'Timeout after 2 hours'
            }
        except Exception as e:
            duration = time.time() - start_time
            logger.error(f"❌ {scenario_type} failed with exception: {e}")
            return {
                'scenario_type': scenario_type,
                'scenario_name': scenario_name,
                'success': False,
                'duration': duration,
                'error': str(e)
            }
    
    def run_comprehensive_training(self) -> List[Dict]:
        """Run comprehensive RL training with multiple scenarios"""
        logger.info("🎯 Starting comprehensive RL training data collection")
        
        scenarios = [
            {
                'type': 'Comprehensive Training',
                'config': 'comprehensive_rl_training_config.json',
                'scenario': 'comprehensive_rl_training',
                'description': 'Full realistic e-commerce traffic patterns'
            },
            {
                'type': 'Stress Testing',
                'config': 'stress_test_rl_config.json',
                'scenario': 'flash_sale_simulation',
                'description': 'Extreme load flash sale simulation'
            },
            {
                'type': 'Service Degradation',
                'config': 'stress_test_rl_config.json',
                'scenario': 'service_degradation_simulation',
                'description': 'Service failure and recovery patterns'
            },
            {
                'type': 'Chaos Testing',
                'config': 'stress_test_rl_config.json',
                'scenario': 'mixed_load_chaos',
                'description': 'Chaotic mixed load patterns'
            }
        ]
        
        results = []
        
        for i, scenario in enumerate(scenarios, 1):
            logger.info(f"📊 Executing scenario {i}/{len(scenarios)}: {scenario['description']}")
            
            result = self.run_scenario(
                scenario['type'],
                scenario['config'],
                scenario['scenario']
            )
            
            results.append(result)
            
            # Add delay between scenarios for system recovery
            if i < len(scenarios):
                logger.info("⏱️ Waiting 2 minutes for system recovery...")
                time.sleep(120)
        
        return results
    
    def run_quick_validation(self) -> Dict:
        """Run quick validation test to ensure system is working"""
        logger.info("⚡ Running quick validation test...")
        
        # Create minimal test configuration
        quick_config = {
            "rl_training_scenarios": {
                "base_config": {
                    "base_url": "http://localhost:8080/proxy",
                    "request_timeout": 10,
                    "retry_attempts": 2,
                    "retry_delay": 1.0
                },
                "training_scenarios": {
                    "quick_validation": {
                        "description": "Quick validation test",
                        "total_users": 10,
                        "test_duration_minutes": 2,
                        "max_concurrent_users": 5,
                        "ramp_up_time_seconds": 30,
                        "traffic_patterns": [
                            {
                                "name": "validation_pattern",
                                "start_minute": 0,
                                "duration_minutes": 2,
                                "intensity": "low",
                                "concurrent_users": 5,
                                "user_behavior_distribution": {
                                    "quick_browsers": 1.0
                                },
                                "scenario_weights": {
                                    "browse_weight": 40,
                                    "cart_weight": 30,
                                    "order_weight": 30
                                },
                                "service_load_characteristics": {
                                    "user_service_load": "low",
                                    "inventory_service_load": "low",
                                    "cart_service_load": "low",
                                    "order_service_load": "low"
                                }
                            }
                        ]
                    }
                },
                "user_behavior_profiles": {
                    "quick_browsers": {
                        "description": "Fast validation browsing",
                        "session_duration_range": [1, 2],
                        "think_time_range": [0.5, 1.0],
                        "action_probabilities": {
                            "browse": 0.4,
                            "cart": 0.3,
                            "order": 0.3
                        },
                        "conversion_rate": 0.7,
                        "pages_per_session": [3, 5],
                        "cart_abandonment_rate": 0.2
                    }
                },
                "enhanced_test_products": [
                    {
                        "sku": "LAPTOP-001",
                        "name": "Test Laptop",
                        "category": "Electronics",
                        "price": 999.99,
                        "image": "https://example.com/laptop.jpg",
                        "popularity_score": 0.9
                    }
                ],
                "realistic_shipping_addresses": [
                    {"street": "123 Test St", "city": "Test City", "state": "TS", "zipCode": "12345", "country": "US", "phone": "+15550123"}
                ]
            }
        }
        
        # Save quick config
        with open('logs/quick_validation_config.json', 'w') as f:
            json.dump(quick_config, f, indent=2)
        
        # Run quick validation
        return self.run_scenario('Quick Validation', 'logs/quick_validation_config.json', 'quick_validation')
    
    def generate_summary_report(self, results: List[Dict]) -> str:
        """Generate comprehensive summary report"""
        total_scenarios = len(results)
        successful_scenarios = sum(1 for r in results if r['success'])
        total_duration = sum(r['duration'] for r in results)
        
        report = f"""
# RL Training Execution Summary Report

## Overview
- **Execution Date**: {self.start_time.strftime('%Y-%m-%d %H:%M:%S')}
- **Total Scenarios**: {total_scenarios}
- **Successful Scenarios**: {successful_scenarios}
- **Success Rate**: {successful_scenarios/total_scenarios*100:.1f}%
- **Total Duration**: {total_duration/60:.1f} minutes

## Scenario Results
"""
        
        for i, result in enumerate(results, 1):
            status = "✅ SUCCESS" if result['success'] else "❌ FAILED"
            report += f"""
### {i}. {result['scenario_type']} - {result['scenario_name']}
- **Status**: {status}
- **Duration**: {result['duration']:.1f} seconds
"""
            if not result['success']:
                report += f"- **Error**: {result.get('error', 'Unknown error')}\n"
        
        report += f"""
## Data Collection Summary
- **Training Data Files**: Check for `logs/rl_training_results_*.json` files
- **Metrics Files**: Check for `logs/rl_training_metrics_*.json` files
- **Log Files**: Check `logs/enhanced_rl_training_load_test.log` and `logs/rl_training_executor.log`

## Next Steps
1. Analyze collected training data for quality and diversity
2. Process metrics for RL model training
3. Validate data completeness and coverage
4. Begin offline RL model training with collected data

Generated at: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
"""
        
        return report
    
    def save_execution_summary(self, results: List[Dict]):
        """Save execution summary to file"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        
        # Save detailed results
        results_file = f"logs/rl_training_execution_results_{timestamp}.json"
        with open(results_file, 'w') as f:
            json.dump({
                'execution_start': self.start_time.isoformat(),
                'execution_end': datetime.now().isoformat(),
                'total_scenarios': len(results),
                'successful_scenarios': sum(1 for r in results if r['success']),
                'results': results
            }, f, indent=2)
        
        # Save summary report
        report = self.generate_summary_report(results)
        report_file = f"logs/rl_training_summary_report_{timestamp}.md"
        with open(report_file, 'w') as f:
            f.write(report)
        
        logger.info(f"📊 Execution results saved to {results_file}")
        logger.info(f"📋 Summary report saved to {report_file}")

def main():
    """Main execution function"""
    parser = argparse.ArgumentParser(description='RL Training Executor')
    parser.add_argument('--mode', choices=['validation', 'comprehensive', 'custom'], 
                       default='comprehensive', help='Execution mode')
    parser.add_argument('--scenario', help='Custom scenario name')
    parser.add_argument('--config', help='Custom config file')
    
    args = parser.parse_args()
    
    executor = RLTrainingExecutor()
    
    logger.info("🚀 RL Training Executor Starting")
    
    # Check prerequisites
    if not executor.check_prerequisites():
        logger.error("❌ Prerequisites not met. Exiting.")
        sys.exit(1)
    
    try:
        if args.mode == 'validation':
            logger.info("⚡ Running quick validation mode")
            result = executor.run_quick_validation()
            results = [result]
            
        elif args.mode == 'comprehensive':
            logger.info("🎯 Running comprehensive training mode")
            results = executor.run_comprehensive_training()
            
        elif args.mode == 'custom':
            if not args.scenario or not args.config:
                logger.error("❌ Custom mode requires --scenario and --config arguments")
                sys.exit(1)
            
            logger.info(f"🔧 Running custom scenario: {args.scenario}")
            result = executor.run_scenario('Custom', args.config, args.scenario)
            results = [result]
        
        # Generate and save summary
        executor.save_execution_summary(results)
        
        # Print final summary
        successful = sum(1 for r in results if r['success'])
        total = len(results)
        
        if successful == total:
            logger.info(f"🎉 RL Training Execution Completed Successfully! ({successful}/{total} scenarios)")
        else:
            logger.warning(f"⚠️ RL Training Execution Completed with Issues ({successful}/{total} scenarios)")
        
        # Print summary report
        print("\n" + executor.generate_summary_report(results))
        
    except KeyboardInterrupt:
        logger.info("⏹️ Execution interrupted by user")
        sys.exit(1)
    except Exception as e:
        logger.error(f"💥 Execution failed with exception: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
