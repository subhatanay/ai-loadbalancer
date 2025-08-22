#!/usr/bin/env python3
"""
Quick deployment validation for RL Load Balancer
Validates the architecture changes and deployment readiness
"""

import os
import json
import subprocess
import sys
from pathlib import Path

def check_file_exists(file_path: str, description: str) -> bool:
    """Check if a file exists"""
    if os.path.exists(file_path):
        print(f"✅ {description}: {file_path}")
        return True
    else:
        print(f"❌ {description}: {file_path} - NOT FOUND")
        return False

def check_java_compilation() -> bool:
    """Check if Java code compiles successfully"""
    try:
        result = subprocess.run(
            ["mvn", "compile", "-q"],
            cwd="/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/load-balancer",
            capture_output=True,
            text=True
        )
        
        if result.returncode == 0:
            print("✅ Java compilation: SUCCESS")
            return True
        else:
            print(f"❌ Java compilation: FAILED - {result.stderr}")
            return False
    except Exception as e:
        print(f"❌ Java compilation: ERROR - {str(e)}")
        return False

def validate_kubernetes_config() -> bool:
    """Validate Kubernetes configuration"""
    config_path = "/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/kubernetes-stack/config-yaml/ai-loadbalancer.yaml"
    
    if not os.path.exists(config_path):
        print(f"❌ Kubernetes config not found: {config_path}")
        return False
    
    try:
        with open(config_path, 'r') as f:
            content = f.read()
        
        # Check for sidecar container
        if "name: rl-agent" in content:
            print("✅ Kubernetes config: RL agent sidecar container configured")
        else:
            print("❌ Kubernetes config: RL agent sidecar container missing")
            return False
        
        # Check for RL_API_URL environment variable
        if "RL_API_URL" in content:
            print("✅ Kubernetes config: RL_API_URL environment variable configured")
        else:
            print("❌ Kubernetes config: RL_API_URL environment variable missing")
            return False
        
        # Check for dual port configuration
        if "port: 8088" in content:
            print("✅ Kubernetes config: RL agent port (8088) configured")
        else:
            print("❌ Kubernetes config: RL agent port (8088) missing")
            return False
        
        return True
        
    except Exception as e:
        print(f"❌ Kubernetes config validation: ERROR - {str(e)}")
        return False

def validate_architecture_components() -> bool:
    """Validate all architecture components are in place"""
    components = [
        ("/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/rl_agent/rl_decision_api.py", "RL Decision API"),
        ("/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/load-balancer/src/main/java/com/bits/loadbalancer/services/RLDecisionClient.java", "RL Decision Client"),
        ("/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/load-balancer/src/main/java/com/bits/loadbalancer/services/RLApiLoadBalancer.java", "RL API Load Balancer"),
        ("/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/load-balancer/src/main/resources/application.yml", "Application Configuration"),
        ("/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/rl_agent/Dockerfile", "RL Agent Dockerfile")
    ]
    
    all_exist = True
    for file_path, description in components:
        if not check_file_exists(file_path, description):
            all_exist = False
    
    return all_exist

def validate_application_config() -> bool:
    """Validate application.yml configuration"""
    config_path = "/Users/subhajgh/Documents/bits/final-project/ai-loadbalancer/load-balancer/src/main/resources/application.yml"
    
    try:
        with open(config_path, 'r') as f:
            content = f.read()
        
        # Check for RL API URL configuration
        if "RL_API_URL:" in content and "decision:" in content and "api:" in content:
            print("✅ Application config: RL API URL configured")
        else:
            print("❌ Application config: RL API URL missing")
            return False
        
        # Check for disabled static RL model loading
        if "enabled: false" in content:
            print("✅ Application config: Static RL model loading disabled")
        else:
            print("❌ Application config: Static RL model loading not disabled")
            return False
        
        return True
        
    except Exception as e:
        print(f"❌ Application config validation: ERROR - {str(e)}")
        return False

def main():
    """Main validation function"""
    print("🔍 RL Load Balancer Deployment Validation")
    print("=" * 50)
    
    validations = [
        ("Architecture Components", validate_architecture_components),
        ("Java Compilation", check_java_compilation),
        ("Application Configuration", validate_application_config),
        ("Kubernetes Configuration", validate_kubernetes_config)
    ]
    
    passed = 0
    total = len(validations)
    
    for name, validation_func in validations:
        print(f"\n📋 {name}:")
        if validation_func():
            passed += 1
        else:
            print(f"❌ {name} validation failed")
    
    print("\n" + "=" * 50)
    print(f"🎯 Validation Results: {passed}/{total} checks passed")
    
    if passed == total:
        print("✅ All validations passed! Deployment is ready.")
        print("\n🚀 Next Steps:")
        print("1. Build images: ./kubernetes-stack/build.sh")
        print("2. Deploy to Kubernetes: ./kubernetes-stack/startup.sh")
        print("3. Test integration: python test_rl_integration.py")
        return True
    else:
        print("❌ Some validations failed. Please fix the issues above.")
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
