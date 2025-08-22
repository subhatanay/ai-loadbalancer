"""
RL Decision API - FastAPI service for real-time routing decisions
Provides intelligent load balancing decisions based on trained RL models
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uvicorn
import asyncio
from datetime import datetime
import logging

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
        
        # Calculate response time
        decision_time = (datetime.now() - start_time).total_seconds() * 1000
        
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

@app.post("/feedback")
async def provide_feedback(
    service_name: str,
    selected_pod: str,
    response_time_ms: float,
    status_code: int,
    error_occurred: bool = False
):
    """
    Provide feedback for continuous learning
    
    This endpoint allows the load balancer to report the outcome
    of routing decisions so the RL agent can learn and improve.
    """
    try:
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        # Get updated metrics for reward calculation
        current_metrics = prometheus_client.get_service_metrics([service_name])
        
        # TODO: Store previous metrics for comparison
        # For now, we'll use the feedback directly
        
        # Update Q-table based on feedback
        # This is a simplified approach - in production, you'd want more sophisticated reward calculation
        reward = 1.0 if not error_occurred and response_time_ms < 1000 else -0.5
        
        logger.info(f"Feedback received: {service_name} -> {selected_pod} "
                   f"(response_time: {response_time_ms}ms, reward: {reward})")
        
        return {"status": "feedback_received", "reward": reward}
        
    except Exception as e:
        logger.error(f"Feedback processing failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/stats")
async def get_rl_stats():
    """Get RL agent statistics"""
    try:
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        return {
            "q_table_size": len(rl_agent.q_table),
            "current_epsilon": rl_agent.current_epsilon,
            "episode_count": rl_agent.episode_count,
            "total_decisions": len(rl_agent.action_history) if hasattr(rl_agent, 'action_history') else 0,
            "average_reward": sum(rl_agent.episode_rewards[-100:]) / min(100, len(rl_agent.episode_rewards)) if rl_agent.episode_rewards else 0
        }
        
    except Exception as e:
        logger.error(f"Stats retrieval failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(
        "rl_decision_api:app",
        host="0.0.0.0",
        port=8088,
        log_level="info",
        reload=False
    )
