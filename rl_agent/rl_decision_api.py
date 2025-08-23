"""
RL Decision API - FastAPI service for real-time routing decisions
Provides intelligent load balancing decisions based on trained RL models
"""

from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uvicorn
import asyncio
from datetime import datetime
import logging
from performance_metrics import performance_collector
from prometheus_exporter import prometheus_exporter

try:
    from cpu.q_learning_agent import QLearningAgent
    from collectors.prometheus_client import PrometheusClient
    from collectors.loadbalancer_client import LoadBalancerClient
    from models.metrics_model import ServiceMetrics
    from utils.rl_logger import rl_logger
except ImportError as e:
    # Configure logging first
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)
    logger.warning(f"Import error: {e}. Using mock implementations.")
    QLearningAgent = None
    PrometheusClient = None
    LoadBalancerClient = None

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="RL Decision API",
    description="Intelligent load balancing decisions using reinforcement learning",
    version="1.0.0"
)

# Global components
rl_agent: Optional[QLearningAgent] = None
prometheus_client: Optional[PrometheusClient] = None
lb_client: Optional[LoadBalancerClient] = None

class RoutingRequest(BaseModel):
    """Request for routing decision"""
    service_name: str
    request_path: Optional[str] = None
    request_method: Optional[str] = "GET"
    client_ip: Optional[str] = None
    user_agent: Optional[str] = None

class RoutingResponse(BaseModel):
    """Response with routing decision"""
    selected_pod: str
    confidence: float
    decision_type: str  # "exploitation" or "exploration"
    state_encoded: str
    available_pods: List[str]
    decision_time_ms: float
    timestamp: str

class HealthResponse(BaseModel):
    """Health check response"""
    status: str
    rl_agent_ready: bool
    prometheus_connected: bool
    loadbalancer_connected: bool
    q_table_size: int
    last_training_update: Optional[str]

@app.on_event("startup")
async def startup_event():
    """Initialize RL components on startup"""
    global rl_agent, prometheus_client, lb_client
    
    try:
        logger.info("Initializing RL Decision API...")
        
        # Initialize components
        rl_agent = QLearningAgent()
        prometheus_client = PrometheusClient()
        lb_client = LoadBalancerClient()
        
        # Load existing models if available
        rl_agent.load_model()
        
        logger.info("RL Decision API initialized successfully")
        
    except Exception as e:
        logger.error(f"Failed to initialize RL Decision API: {e}")
        raise

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint"""
    try:
        prometheus_healthy = prometheus_client.health_check() if prometheus_client else False
        lb_healthy = lb_client.health_check() if lb_client else False
        
        return HealthResponse(
            status="healthy" if rl_agent and prometheus_healthy else "degraded",
            rl_agent_ready=rl_agent is not None,
            prometheus_connected=prometheus_healthy,
            loadbalancer_connected=lb_healthy,
            q_table_size=len(rl_agent.q_table) if rl_agent else 0,
            last_training_update=None  # TODO: Track last update
        )
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/decide", response_model=RoutingResponse)
async def make_routing_decision(request: RoutingRequest):
    """
    Make intelligent routing decision for a service request
    
    This endpoint:
    1. Gets current system metrics from Prometheus
    2. Encodes current state using real-time data
    3. Uses Q-learning agent to select best pod
    4. Balances exploration vs exploitation
    5. Updates learning based on previous decisions
    """
    start_time = datetime.now()
    
    try:
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        # Step 1: Get current service instances from load balancer
        services = lb_client.get_registered_services()
        target_service_instances = []
        
        logger.info(f"Raw services from load balancer: {services}")
        logger.info(f"Type of services: {type(services)}")
        
        for service in services:
            logger.info(f"Service object: {service}, type: {type(service)}")
            if hasattr(service, 'service_name') and service.service_name == request.service_name:
                target_service_instances.append(service)
        
        if not target_service_instances:
            raise HTTPException(
                status_code=404, 
                detail=f"No instances found for service: {request.service_name}. Available services: {[getattr(s, 'service_name', str(s)) for s in services]}"
            )
        
        # Step 2: Get real-time metrics from Prometheus
        service_metrics = prometheus_client.get_service_metrics(target_service_instances)
        
        # Step 3: Create available pods list - extract instance IDs from ServiceInstance objects
        available_pods = []
        for instance in target_service_instances:
            logger.info(f"Processing instance: {instance}, type: {type(instance)}")
            if hasattr(instance, 'instance_id'):
                available_pods.append(instance.instance_id)
                logger.info(f"Added instance_id: {instance.instance_id}")
            elif hasattr(instance, 'instanceName'):
                available_pods.append(instance.instanceName)
                logger.info(f"Added instanceName: {instance.instanceName}")
            else:
                # Fallback: convert to string
                instance_str = str(instance)
                available_pods.append(instance_str)
                logger.info(f"Added string representation: {instance_str}")
        
        logger.info(f"Available pods list: {available_pods}")
        
        if not available_pods:
            raise HTTPException(
                status_code=404,
                detail=f"No valid instance IDs found for service: {request.service_name}"
            )
        
        # Step 4: Use RL agent to make decision (returns instance ID string)
        try:
            logger.info(f"Calling RL agent with {len(target_service_instances)} instances")
            for i, inst in enumerate(target_service_instances):
                logger.info(f"Instance {i}: {inst}, type: {type(inst)}")
            
            # Call RL agent with proper parameters: metrics and service instances
            selected_pod = rl_agent.select_action(service_metrics, target_service_instances)
            logger.info(f"RL agent selected pod: {selected_pod}")
        except Exception as e:
            logger.error(f"RL agent selection failed with error: {e}")
            import traceback
            logger.error(f"Full traceback: {traceback.format_exc()}")
            # Fallback to first available pod
            selected_pod = available_pods[0]
            logger.info(f"Using fallback pod: {selected_pod}")
        
        # Step 5: Validate selected pod is in available pods
        if selected_pod not in available_pods:
            logger.warning(f"RL agent selected unavailable pod {selected_pod}, using fallback")
            selected_pod = available_pods[0] if available_pods else "unknown"
        
        # Step 5: Determine decision type
        decision_type = "exploration" if rl_agent.current_epsilon > 0.1 else "exploitation"
        
        # Step 6: Calculate confidence based on Q-values
        state_key = rl_agent.current_state
        confidence = 0.8  # TODO: Calculate based on Q-value spread
        
        # Get Q-value for logging
        q_value = rl_agent.q_table.get((state_key, selected_pod), 0.0) if state_key else 0.0
        
        # Calculate response time
        decision_time = (datetime.now() - start_time).total_seconds() * 1000
        
        # Record decision metrics
        performance_collector.record_decision(
            service_name=request.service_name,
            selected_pod=selected_pod,
            available_pods=available_pods,
            decision_time_ms=decision_time,
            confidence=confidence,
            decision_type=decision_type,
            q_value=q_value,
            epsilon=rl_agent.current_epsilon,
            state_encoded=str(state_key) if state_key else "unknown"
        )
        
        # Record Prometheus metrics
        prometheus_exporter.record_decision_metric(
            service_name=request.service_name,
            decision_type=decision_type,
            decision_time_ms=decision_time
        )
        
        logger.info(f"Routing decision made: {request.service_name} -> {selected_pod} "
                   f"({decision_type}, {decision_time:.2f}ms)")
        
        return RoutingResponse(
            selected_pod=selected_pod,
            confidence=confidence,
            decision_type=decision_type,
            state_encoded=str(state_key) if state_key else "unknown",
            available_pods=available_pods,
            decision_time_ms=decision_time,
            timestamp=datetime.now().isoformat()
        )
        
    except Exception as e:
        logger.error(f"Routing decision failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

class FeedbackRequest(BaseModel):
    """Feedback request model"""
    service_name: str
    selected_pod: str
    response_time_ms: float
    status_code: int
    error_occurred: bool = False

@app.post("/feedback")
async def provide_feedback(request: FeedbackRequest):
    """
    Provide feedback for continuous learning and Q-table updates
    
    This endpoint allows the load balancer to report the outcome
    of routing decisions so the RL agent can learn and improve.
    """
    try:
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        logger.info(f"Processing feedback: {request.service_name} -> {request.selected_pod} "
                   f"(response_time: {request.response_time_ms}ms, status: {request.status_code}, error: {request.error_occurred})")
        
        # Get current metrics after the action was executed
        # First get service instances for the service
        services = lb_client.get_registered_services()
        target_service_instances = [
            service for service in services 
            if hasattr(service, 'service_name') and service.service_name == request.service_name
        ]
        
        if target_service_instances:
            current_metrics = prometheus_client.get_service_metrics(target_service_instances)
        else:
            logger.warning(f"No instances found for service {request.service_name} in feedback")
            current_metrics = []
        
        # Check if we have previous state information for Q-learning update
        if (rl_agent.previous_state is not None and 
            rl_agent.last_action is not None and
            rl_agent.current_state is not None):
            
            # Create a comprehensive reward based on multiple factors
            reward = calculate_comprehensive_reward(
                response_time_ms=request.response_time_ms,
                status_code=request.status_code,
                error_occurred=request.error_occurred,
                current_metrics=current_metrics,
                selected_pod=request.selected_pod
            )
            
            # Get previous metrics from cache or estimate
            previous_metrics = getattr(rl_agent, 'previous_metrics', current_metrics)
            
            # Update Q-table with the actual learning algorithm
            rl_agent.update_q_table(current_metrics, previous_metrics)
            
            # Store current metrics for next iteration
            rl_agent.previous_metrics = current_metrics
            
            # Increment step counter for episode tracking
            rl_agent.increment_step()
            
            logger.info(f"Q-table updated: {request.service_name} -> {request.selected_pod} "
                       f"(reward: {reward:.3f}, Q-table size: {len(rl_agent.q_table)})")
            
            # Get updated Q-value for the state-action pair
            state_action_key = (rl_agent.previous_state, rl_agent.last_action)
            updated_q_value = rl_agent.q_table.get(state_action_key, 0.0)
            
            # Record feedback metrics
            performance_collector.record_feedback(
                service_name=request.service_name,
                selected_pod=request.selected_pod,
                response_time_ms=request.response_time_ms,
                status_code=request.status_code,
                error_occurred=request.error_occurred,
                reward=reward,
                q_value_updated=updated_q_value
            )
            
            # Record Prometheus feedback metrics
            prometheus_exporter.record_feedback_metric(
                service_name=request.service_name,
                status_code=request.status_code,
                response_time_ms=request.response_time_ms,
                reward=reward
            )
            
            return {
                "status": "feedback_processed_and_learned",
                "reward": reward,
                "q_value_updated": updated_q_value,
                "q_table_size": len(rl_agent.q_table),
                "epsilon": rl_agent.current_epsilon,
                "episode": rl_agent.episode_count
            }
        else:
            # No previous state information - this is likely the first request
            # Calculate reward but can't update Q-table yet
            reward = calculate_comprehensive_reward(
                response_time_ms=request.response_time_ms,
                status_code=request.status_code,
                error_occurred=request.error_occurred,
                current_metrics=current_metrics,
                selected_pod=request.selected_pod
            )
            
            logger.info(f"Feedback received but no Q-table update possible (missing previous state): "
                       f"{request.service_name} -> {request.selected_pod} (reward: {reward:.3f})")
            
            return {
                "status": "feedback_received_no_update",
                "reward": reward,
                "reason": "No previous state information available"
            }
        
    except Exception as e:
        logger.error(f"Feedback processing failed: {e}")
        import traceback
        logger.error(f"Full traceback: {traceback.format_exc()}")
        raise HTTPException(status_code=500, detail=str(e))

def calculate_comprehensive_reward(
    response_time_ms: float,
    status_code: int,
    error_occurred: bool,
    current_metrics: List,
    selected_pod: str
) -> float:
    """
    Calculate a comprehensive reward based on multiple factors
    """
    reward = 0.0
    
    # 1. Response time component (normalized to 0-1 scale)
    if response_time_ms <= 100:
        time_reward = 1.0
    elif response_time_ms <= 500:
        time_reward = 0.8
    elif response_time_ms <= 1000:
        time_reward = 0.5
    elif response_time_ms <= 2000:
        time_reward = 0.2
    else:
        time_reward = -0.5
    
    reward += time_reward * 0.4  # 40% weight
    
    # 2. Status code component
    if 200 <= status_code < 300:
        status_reward = 1.0
    elif 300 <= status_code < 400:
        status_reward = 0.5
    elif 400 <= status_code < 500:
        status_reward = -0.5
    else:  # 500+ errors
        status_reward = -1.0
    
    reward += status_reward * 0.4  # 40% weight
    
    # 3. Error occurrence penalty
    if error_occurred:
        reward -= 0.3
    
    # 4. System load consideration (if metrics available)
    if current_metrics and len(current_metrics) > 0:
        try:
            # Find metrics for the selected pod
            pod_metrics = None
            for metric in current_metrics:
                if hasattr(metric, 'instances'):
                    for instance in metric.instances:
                        if hasattr(instance, 'instance_id') and instance.instance_id == selected_pod:
                            pod_metrics = instance
                            break
            
            if pod_metrics:
                # Reward lower CPU usage (encourage load balancing)
                if hasattr(pod_metrics, 'cpu_usage') and pod_metrics.cpu_usage is not None:
                    if pod_metrics.cpu_usage < 0.5:  # Low CPU usage
                        reward += 0.1
                    elif pod_metrics.cpu_usage > 0.8:  # High CPU usage
                        reward -= 0.1
                
                # Reward lower memory usage
                if hasattr(pod_metrics, 'memory_usage') and pod_metrics.memory_usage is not None:
                    if pod_metrics.memory_usage < 0.7:  # Low memory usage
                        reward += 0.1
                    elif pod_metrics.memory_usage > 0.9:  # High memory usage
                        reward -= 0.1
        except Exception as e:
            logger.debug(f"Could not process metrics for reward calculation: {e}")
    
    # Ensure reward is in reasonable range
    reward = max(-2.0, min(2.0, reward))
    
    return reward

@app.get("/stats")
async def get_rl_stats():
    """Get RL agent statistics"""
    try:
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        # Create performance snapshot
        snapshot = performance_collector.create_performance_snapshot(rl_agent)
        
        return {
            "q_table_size": len(rl_agent.q_table),
            "current_epsilon": rl_agent.current_epsilon,
            "episode_count": rl_agent.episode_count,
            "total_decisions": len(rl_agent.action_history) if hasattr(rl_agent, 'action_history') else 0,
            "average_reward": sum(rl_agent.episode_rewards[-100:]) / min(100, len(rl_agent.episode_rewards)) if rl_agent.episode_rewards else 0,
            "performance_snapshot": snapshot.__dict__,
            "service_performance": performance_collector.get_service_performance_summary(),
            "model_health": performance_collector.get_model_health_indicators()
        }
        
    except Exception as e:
        logger.error(f"Stats retrieval failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/performance")
async def get_performance_metrics():
    """Get comprehensive performance metrics for dashboard"""
    try:
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        # Create fresh performance snapshot
        snapshot = performance_collector.create_performance_snapshot(rl_agent)
        
        return {
            "model_health": performance_collector.get_model_health_indicators(),
            "service_performance": performance_collector.get_service_performance_summary(),
            "prometheus_metrics": performance_collector.export_metrics_for_prometheus(),
            "recent_decisions": len([d for d in performance_collector.decision_history 
                                   if (datetime.now() - d.timestamp).total_seconds() < 300]),
            "recent_feedback": len([f for f in performance_collector.feedback_history 
                                  if (datetime.now() - f.timestamp).total_seconds() < 300]),
            "performance_trends": {
                "reward_trend": list(performance_collector.reward_trends)[-50:],
                "snapshots": [s.__dict__ for s in list(performance_collector.performance_snapshots)[-20:]]
            }
        }
        
    except Exception as e:
        logger.error(f"Performance metrics retrieval failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/metrics")
async def prometheus_metrics():
    """Prometheus metrics endpoint"""
    try:
        # Update metrics with current RL agent state
        prometheus_exporter.update_metrics(rl_agent)
        
        # Return metrics in Prometheus format
        metrics_data = prometheus_exporter.generate_metrics()
        return Response(
            content=metrics_data,
            media_type=prometheus_exporter.get_content_type()
        )
        
    except Exception as e:
        logger.error(f"Prometheus metrics generation failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(
        "rl_decision_api:app",
        host="0.0.0.0",
        port=8088,
        log_level="info",
        reload=False
    )
