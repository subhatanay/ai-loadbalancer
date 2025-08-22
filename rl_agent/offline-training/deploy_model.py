#!/usr/bin/env python3
"""
Deploy trained RL model to the load balancer for AI-powered routing decisions.
"""

import pickle
import json
import sys
import os
from pathlib import Path
from typing import Dict, List, Any
from datetime import datetime

class ModelDeployer:
    """Deploy trained RL model for load balancer integration"""
    
    def __init__(self, model_path: str):
        self.model_path = Path(model_path)
        self.model_data = None
        self.q_table = None
        
    def load_model(self) -> bool:
        """Load the trained RL model"""
        try:
            with open(self.model_path, 'rb') as f:
                self.model_data = pickle.load(f)
            
            self.q_table = self.model_data.get('q_table', {})
            
            print(f"✅ Model loaded successfully from {self.model_path}")
            print(f"📊 Model info:")
            print(f"   - Q-table size: {len(self.q_table)}")
            print(f"   - Training results: {self.model_data.get('training_results', {})}")
            print(f"   - Timestamp: {self.model_data.get('timestamp', 'Unknown')}")
            
            return True
            
        except Exception as e:
            print(f"❌ Failed to load model: {e}")
            return False
    
    def create_routing_service(self) -> str:
        """Create a routing service that uses the trained model"""
        
        service_code = '''
import pickle
import json
from typing import Dict, List, Optional
from pathlib import Path

class AIRoutingService:
    """AI-powered routing service using trained RL model"""
    
    def __init__(self, model_path: str):
        self.model_path = Path(model_path)
        self.q_table = {}
        self.load_model()
    
    def load_model(self):
        """Load the trained Q-table"""
        try:
            with open(self.model_path, 'rb') as f:
                model_data = pickle.load(f)
            self.q_table = model_data.get('q_table', {})
            print(f"AI Routing Service: Loaded model with {len(self.q_table)} states")
        except Exception as e:
            print(f"Warning: Failed to load AI model: {e}")
            self.q_table = {}
    
    def encode_state(self, metrics: Dict) -> str:
        """Encode current metrics into state representation"""
        running_pods = []
        for pod_name, pod_metrics in metrics.items():
            if pod_metrics.get('uptimeSeconds', 0) > 0:
                cpu = pod_metrics.get('cpuUsagePercent', 0)
                latency = pod_metrics.get('avgResponseTimeMs', 0)
                errors = pod_metrics.get('errorRatePercent', 0)
                
                # Discretize values into bins (same as training)
                cpu_bin = min(int(cpu / 20), 4)
                latency_bin = min(int(latency / 50), 4)
                error_bin = min(int(errors / 5), 4)
                
                running_pods.append(f"{pod_name[:8]}_{cpu_bin}_{latency_bin}_{error_bin}")
        
        return "|".join(sorted(running_pods)[:5])
    
    def get_best_pod(self, current_metrics: Dict, available_pods: List[str]) -> Optional[str]:
        """Get the best pod to route to based on trained model"""
        if not available_pods:
            return None
        
        # Encode current state
        state = self.encode_state(current_metrics)
        
        # If we have learned Q-values for this state, use them
        if state in self.q_table and self.q_table[state]:
            # Get available actions (pods) that we have Q-values for
            available_actions = [pod for pod in available_pods if pod in self.q_table[state]]
            
            if available_actions:
                # Choose action with highest Q-value
                best_pod = max(available_actions, key=lambda pod: self.q_table[state][pod])
                print(f"AI Routing: Selected {best_pod} based on learned Q-values")
                return best_pod
        
        # Fallback: choose pod with best current metrics
        best_pod = self._fallback_selection(current_metrics, available_pods)
        print(f"AI Routing: Fallback selection {best_pod} (state not learned)")
        return best_pod
    
    def _fallback_selection(self, metrics: Dict, available_pods: List[str]) -> str:
        """Fallback pod selection based on current metrics"""
        best_pod = available_pods[0]
        best_score = float('inf')
        
        for pod in available_pods:
            if pod in metrics:
                pod_metrics = metrics[pod]
                cpu = pod_metrics.get('cpuUsagePercent', 100)
                latency = pod_metrics.get('avgResponseTimeMs', 1000)
                errors = pod_metrics.get('errorRatePercent', 100)
                
                # Simple scoring: lower is better
                score = cpu + latency + (errors * 10)
                
                if score < best_score:
                    best_score = score
                    best_pod = pod
        
        return best_pod

# Example usage:
# routing_service = AIRoutingService('models/rl_model_20250819_002415.pkl')
# best_pod = routing_service.get_best_pod(current_metrics, available_pods)
'''
        
        return service_code
    
    def create_integration_guide(self) -> str:
        """Create integration guide for the load balancer"""
        
        guide = f'''
# AI Load Balancer Integration Guide

## Model Information
- **Model File**: {self.model_path.name}
- **Training Date**: {self.model_data.get('timestamp', 'Unknown')}
- **Q-table Size**: {len(self.q_table)} learned states
- **Success Rate**: {self.model_data.get('training_results', {}).get('success_rate', 0):.2%}

## Integration Steps

### 1. Copy Model File
Copy the trained model to your load balancer:
```bash
cp {self.model_path} /path/to/load-balancer/models/
```

### 2. Add AI Routing Service
Add the AIRoutingService class to your load balancer project.

### 3. Modify LoadBalancerController
Update your LoadBalancerController.java to use AI routing:

```java
@Autowired
private AIRoutingService aiRoutingService;

private ServiceInfo selectTargetInstance(List<ServiceInfo> instances, String serviceName) {{
    // Get current metrics for all instances
    Map<String, InstanceMetrics> currentMetrics = metricsService.fetchMetricsForPods(
        instances.stream().map(ServiceInfo::getInstanceName).collect(Collectors.toList())
    );
    
    // Get available pod names
    List<String> availablePods = instances.stream()
        .map(ServiceInfo::getInstanceName)
        .collect(Collectors.toList());
    
    // Use AI to select best pod
    String bestPod = aiRoutingService.getBestPod(currentMetrics, availablePods);
    
    // Find and return the corresponding ServiceInfo
    return instances.stream()
        .filter(instance -> instance.getInstanceName().equals(bestPod))
        .findFirst()
        .orElse(instances.get(0)); // Fallback to first instance
}}
```

### 4. Test AI Routing
1. Deploy the updated load balancer
2. Send test traffic
3. Monitor routing decisions in logs
4. Verify AI-based pod selection is working

## Expected Behavior
- **Learned States**: AI will make optimal decisions for {len(self.q_table)} different system states
- **Fallback**: For unknown states, falls back to metric-based selection
- **Performance**: Should improve latency and reduce errors based on training data

## Monitoring
- Check logs for "AI Routing: Selected..." messages
- Monitor overall system performance improvements
- Track routing decision accuracy
'''
        
        return guide
    
    def deploy(self):
        """Deploy the model and create integration files"""
        if not self.load_model():
            return False
        
        # Create deployment directory
        deploy_dir = Path("deployment")
        deploy_dir.mkdir(exist_ok=True)
        
        # Create AI routing service
        service_code = self.create_routing_service()
        with open(deploy_dir / "ai_routing_service.py", 'w') as f:
            f.write(service_code)
        
        # Create integration guide
        guide = self.create_integration_guide()
        with open(deploy_dir / "INTEGRATION_GUIDE.md", 'w') as f:
            f.write(guide)
        
        # Copy model file
        import shutil
        shutil.copy2(self.model_path, deploy_dir / self.model_path.name)
        
        print(f"🚀 Deployment package created in {deploy_dir}/")
        print(f"📁 Files created:")
        print(f"   - ai_routing_service.py (AI routing logic)")
        print(f"   - INTEGRATION_GUIDE.md (integration instructions)")
        print(f"   - {self.model_path.name} (trained model)")
        print(f"")
        print(f"✅ Ready for load balancer integration!")
        
        return True

if __name__ == "__main__":
    # Find the latest model file
    models_dir = Path("models")
    if not models_dir.exists():
        print("❌ No models directory found. Run training first.")
        sys.exit(1)
    
    model_files = list(models_dir.glob("rl_model_*.pkl"))
    if not model_files:
        print("❌ No trained models found. Run training first.")
        sys.exit(1)
    
    # Use the latest model
    latest_model = max(model_files, key=lambda p: p.stat().st_mtime)
    
    print(f"🎯 Deploying latest model: {latest_model}")
    
    deployer = ModelDeployer(latest_model)
    success = deployer.deploy()
    
    if success:
        print(f"🎉 Model deployment successful!")
        print(f"📋 Next: Follow the integration guide to update your load balancer")
    else:
        print(f"❌ Model deployment failed")
