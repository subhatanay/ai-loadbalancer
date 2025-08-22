#!/usr/bin/env python3
"""
RL Integration Verification Script
Verifies that Load Balancer is sending experiences to RL Collector
"""

import requests
import json
import time
from datetime import datetime
import subprocess

def check_rl_collector_health():
    """Check if RL collector is running and healthy"""
    try:
        response = requests.get("http://localhost:8080/proxy/rl-experience-collector/health", timeout=5)
        if response.status_code == 200:
            print("✅ RL Collector is healthy")
            return True
        else:
            print(f"❌ RL Collector health check failed: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ RL Collector not accessible: {e}")
        return False

def check_experience_file():
    """Check if experiences are being collected"""
    try:
        # Check if RL collector pod has the experience file
        result = subprocess.run([
            "kubectl", "exec", "-n", "ai-loadbalancer", 
            "deployment/rl-experience-collector", "--", 
            "ls", "-la", "data/"
        ], capture_output=True, text=True, timeout=10)
        
        if result.returncode == 0:
            print("✅ RL Collector data directory:")
            print(result.stdout)
            
            # Check file size
            result = subprocess.run([
                "kubectl", "exec", "-n", "ai-loadbalancer", 
                "deployment/rl-experience-collector", "--", 
                "wc", "-l", "data/rl_experiences.jsonl"
            ], capture_output=True, text=True, timeout=10)
            
            if result.returncode == 0:
                lines = result.stdout.strip().split()[0]
                print(f"✅ Experience file has {lines} experiences")
                return int(lines) > 0
        
        print("❌ Could not access experience file")
        return False
    except Exception as e:
        print(f"❌ Error checking experience file: {e}")
        return False

def trigger_load_test():
    """Trigger a small load test to generate experiences"""
    print("🚀 Triggering load test to generate RL experiences...")
    try:
        # Run a quick load test
        subprocess.run([
            "python3", "enhanced_rl_training_load_test.py", 
            "--config", "logs/quick_validation_config.json", 
            "--scenario", "quick_validation"
        ], cwd="../load-testing", timeout=120)
        print("✅ Load test completed")
        return True
    except Exception as e:
        print(f"❌ Load test failed: {e}")
        return False

def export_experiences():
    """Export experiences from RL collector for analysis"""
    try:
        print("📥 Exporting experiences from RL collector...")
        
        # Copy experience file from container
        result = subprocess.run([
            "kubectl", "cp", "-n", "ai-loadbalancer",
            "deployment/rl-experience-collector:data/rl_experiences.jsonl",
            "./exported_experiences.jsonl"
        ], capture_output=True, text=True, timeout=30)
        
        if result.returncode == 0:
            print("✅ Experiences exported to ./exported_experiences.jsonl")
            
            # Show sample experiences
            with open("./exported_experiences.jsonl", "r") as f:
                lines = f.readlines()
                print(f"📊 Total experiences: {len(lines)}")
                if lines:
                    print("📝 Sample experience:")
                    sample = json.loads(lines[0])
                    print(json.dumps(sample, indent=2)[:500] + "...")
            return True
        else:
            print(f"❌ Failed to export experiences: {result.stderr}")
            return False
    except Exception as e:
        print(f"❌ Error exporting experiences: {e}")
        return False

def main():
    print("🔍 RL Integration Verification")
    print("=" * 50)
    
    # Step 1: Check RL collector health
    if not check_rl_collector_health():
        print("❌ RL Collector is not healthy. Please check deployment.")
        return
    
    # Step 2: Check current experience data
    has_data = check_experience_file()
    
    # Step 3: If no data, trigger load test
    if not has_data:
        print("📊 No experiences found. Triggering load test...")
        if not trigger_load_test():
            print("❌ Could not generate experiences")
            return
        
        # Wait and check again
        time.sleep(10)
        has_data = check_experience_file()
    
    # Step 4: Export experiences for analysis
    if has_data:
        export_experiences()
        print("\n🎉 RL Integration Verification Complete!")
        print("✅ Load Balancer → RL Collector integration is working")
        print("✅ Experiences are being collected")
        print("✅ Data is ready for model training")
    else:
        print("\n❌ RL Integration has issues:")
        print("- Check if ExperienceLoggingInterceptor is enabled")
        print("- Check load balancer logs for RL experience sending")
        print("- Verify RL collector is receiving requests")

if __name__ == "__main__":
    main()
