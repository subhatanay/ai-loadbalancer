"""
RL Decision API - FastAPI service for real-time routing decisions
Provides intelligent load balancing decisions based on trained RL models
"""

from fastapi import FastAPI, HTTPException, Response, Header
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import uvicorn
import asyncio
from datetime import datetime
import logging
import time
import hashlib
import random
import secrets
from performance_metrics import performance_collector
from prometheus_exporter import prometheus_exporter

try:
    from cpu.q_learning_agent import QLearningAgent
    from collectors.prometheus_client import PrometheusClient
    from collectors.loadbalancer_client import LoadBalancerClient
    from models.metrics_model import ServiceMetrics
    from utils.rl_logger import rl_logger, set_trace_id, clear_trace_id
    from config.rl_settings import RLConfig
except ImportError as e:
    # Configure logging first
    logging.basicConfig(level=logging.INFO)
    logger = logging.getLogger(__name__)
    logger.warning(f"Import error: {e}. Using mock implementations.")
    QLearningAgent = None
    PrometheusClient = None
    LoadBalancerClient = None
    RLConfig = None

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

# Decision caching for latency optimization with lightweight load balancing
decision_cache = {}
service_discovery_cache = {}
metrics_cache = {}
cache_rotation_counter = {}  # Simple counter for cache rotation
load_balancing_tracker = {}  # Track recent pod selections for load balancing

CACHE_TTL_DECISION = 0.3  # Increased to 300ms for better performance
CACHE_TTL_SERVICES = 5.0  # 5 seconds for service discovery
CACHE_TTL_METRICS = 1.0   # 1 second for metrics
ROTATION_INTERVAL = 2  # Rotate cache every 2 requests

def get_cache_key(service_name: str, metrics_hash: str, instance_count: int) -> str:
    """Generate cache key for decision caching with rotation"""
    # Add rotation counter to cache key for simple load balancing
    rotation_key = f"rotation_counter:{service_name}"
    if rotation_key not in cache_rotation_counter:
        cache_rotation_counter[rotation_key] = 0
    
    counter = cache_rotation_counter[rotation_key]
    rotation_suffix = counter // ROTATION_INTERVAL  # Change every 2 requests
    
    return f"{service_name}:{metrics_hash}:{instance_count}:r{rotation_suffix}"

def increment_rotation_counter(service_name: str):
    """Increment rotation counter for service"""
    rotation_key = f"rotation_counter:{service_name}"
    if rotation_key not in cache_rotation_counter:
        cache_rotation_counter[rotation_key] = 0
    cache_rotation_counter[rotation_key] += 1

def apply_load_balancing_override(service_name: str, selected_pod: str, available_pods: List[str]) -> str:
    """Apply load balancing override to prevent pod overuse"""
    if len(available_pods) <= 1:
        return selected_pod
    
    # Track recent selections for this service
    tracker_key = f"lb_tracker:{service_name}"
    if tracker_key not in load_balancing_tracker:
        load_balancing_tracker[tracker_key] = []
    
    recent_selections = load_balancing_tracker[tracker_key]
    
    # Keep only last 10 selections
    recent_selections = recent_selections[-9:]  # Keep 9, add 1 = 10 total
    
    # Check if selected pod has been used too frequently
    if len(recent_selections) >= 6:  # Need at least 6 decisions to check
        selected_count = recent_selections.count(selected_pod)
        if selected_count >= 4:  # Used 4+ times in last 6-9 decisions
            # Find least used pod
            pod_counts = {pod: recent_selections.count(pod) for pod in available_pods}
            min_count = min(pod_counts.values())
            least_used_pods = [pod for pod, count in pod_counts.items() if count == min_count]
            
            if least_used_pods and selected_pod not in least_used_pods:
                override_pod = random.choice(least_used_pods)
                logger.info(f"Load balancing override: {selected_pod} -> {override_pod} (usage: {selected_count}/6)")
                selected_pod = override_pod
    
    # Update tracking
    recent_selections.append(selected_pod)
    load_balancing_tracker[tracker_key] = recent_selections
    
    return selected_pod

def get_metrics_hash(metrics: List) -> str:
    """Generate hash of current metrics for caching"""
    try:
        if not metrics:
            return "empty"
        # Simplified hash for better performance
        key_values = []
        for metric in metrics[:1]:  # Only use first metric for speed
            if hasattr(metric, 'cpu_usage_percent') and metric.cpu_usage_percent is not None:
                key_values.append(f"cpu:{int(metric.cpu_usage_percent/5)*5}")  # Back to 5% for fewer cache misses
            if hasattr(metric, 'avg_response_time_ms') and metric.avg_response_time_ms is not None:
                key_values.append(f"rt:{int(metric.avg_response_time_ms/100)*100}")  # 100ms buckets
        return hashlib.md5("|".join(key_values).encode()).hexdigest()[:6]  # Shorter hash
    except Exception:
        import time
        return f"fallback:{int(time.time())}"

def cleanup_cache(cache_dict: dict, ttl: float):
    """Clean expired entries from cache"""
    import time
    current_time = time.time()
    expired_keys = [k for k, (_, cache_time) in cache_dict.items() if current_time - cache_time > ttl * 2]
    for k in expired_keys[:50]:  # Remove up to 50 expired entries
        cache_dict.pop(k, None)

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
    trace_id: Optional[str] = None

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

def generate_trace_id() -> str:
    """Generate OpenTelemetry-compatible 32-digit hex trace ID"""
    return secrets.token_hex(16)  # 16 bytes = 32 hex characters

def validate_trace_id(trace_id: str) -> bool:
    """Validate if trace ID follows OpenTelemetry 32-digit hex format"""
    return trace_id is not None and len(trace_id) == 32 and all(c in '0123456789abcdef' for c in trace_id.lower())

@app.post("/decide")
async def decide_routing(request: RoutingRequest, x_trace_id: str = Header(None)) -> RoutingResponse:
    """
    Main endpoint for routing decisions with comprehensive metrics and caching
    
    Features:
    1. Service discovery with caching (5s TTL)
    2. Metrics collection with caching (3s TTL) 
    3. Q-learning decision with exploration/exploitation
    4. Comprehensive performance tracking
    5. Updates learning based on previous decisions
    """
    start_time = datetime.now()
    
    # Handle trace ID - validate existing or generate new one
    if x_trace_id and validate_trace_id(x_trace_id):
        trace_id = x_trace_id.lower()
    else:
        trace_id = generate_trace_id()
        if x_trace_id:
            logger.warning(f"Invalid trace ID format received: {x_trace_id}, generated new: {trace_id}")
    
    # Set trace ID in context for logging
    set_trace_id(trace_id)
    trace_context = f"[traceId={trace_id}]"
    
    # Detailed timing measurements
    timing_start = time.time()
    step_times = {}
    
    try:
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        # Step 1: Get current service instances from load balancer (with caching)
        current_time = time.time()
        service_cache_key = f"services:{request.service_name}"
        
        step_start = time.time()
        
        # Check service discovery cache
        if service_cache_key in service_discovery_cache:
            cached_services, cache_time = service_discovery_cache[service_cache_key]
            if current_time - cache_time < CACHE_TTL_SERVICES:
                target_service_instances = cached_services
                rl_logger.logger.debug(f"Using cached service instances for {request.service_name}")
            else:
                # Cache expired, fetch fresh
                services = lb_client.get_registered_services()
                target_service_instances = [
                    service for service in services 
                    if service.service_name == request.service_name
                ]
                service_discovery_cache[service_cache_key] = (target_service_instances, current_time)
                rl_logger.logger.info(f"Fetching registered services | url: http://localhost:8080/api/services")
        else:
            # No cache entry, fetch fresh
            services = lb_client.get_registered_services()
            target_service_instances = [
                service for service in services 
                if service.service_name == request.service_name
            ]
            service_discovery_cache[service_cache_key] = (target_service_instances, current_time)
            rl_logger.logger.info(f"Fetching registered services | url: http://localhost:8080/api/services") 
        
        step_times['service_discovery'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        if not target_service_instances:
            raise HTTPException(
                status_code=404,
                detail=f"No instances found for service: {request.service_name}"
            )
        
        # Step 2: Get real-time metrics from Prometheus (with caching)
        metrics_cache_key = f"metrics:{request.service_name}:{len(target_service_instances)}"
        
        if metrics_cache_key in metrics_cache:
            cached_metrics, cache_time = metrics_cache[metrics_cache_key]
            if current_time - cache_time < CACHE_TTL_METRICS:
                service_metrics = cached_metrics
                rl_logger.logger.debug(f"Using cached metrics for {request.service_name}")
            else:
                service_metrics = prometheus_client.get_service_metrics(target_service_instances)
                metrics_cache[metrics_cache_key] = (service_metrics, current_time)
                rl_logger.logger.info(f"Starting metrics collection | total_instances: {len(target_service_instances)}")
        else:
            service_metrics = prometheus_client.get_service_metrics(target_service_instances)
            metrics_cache[metrics_cache_key] = (service_metrics, current_time)
            rl_logger.logger.info(f"Starting metrics collection | total_instances: {len(target_service_instances)}")
        
        step_times['metrics_collection'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Step 2.5: Create available pods list first (needed for both cached and fresh decisions)
        available_pods = []
        for instance in target_service_instances:
            if hasattr(instance, 'instance_id'):
                available_pods.append(instance.instance_id)
            elif hasattr(instance, 'instanceName'):
                available_pods.append(instance.instanceName)
            else:
                available_pods.append(str(instance))
        
        if not available_pods:
            raise HTTPException(
                status_code=404,
                detail=f"No valid instance IDs found for service: {request.service_name}"
            )
        
        # Step 3: Check decision cache with rotation logic
        metrics_hash = get_metrics_hash(service_metrics)
        cache_key = get_cache_key(request.service_name, metrics_hash, len(target_service_instances))
        
        if cache_key in decision_cache:
            cached_decision, cache_time = decision_cache[cache_key]
            if current_time - cache_time < CACHE_TTL_DECISION:
                logger.debug(f"Cache hit for {request.service_name} - applying load balancing to cached decision")
                
                # Apply load balancing override even to cached decisions
                original_pod = cached_decision.selected_pod
                balanced_pod = apply_load_balancing_override(request.service_name, original_pod, available_pods)
                
                # If load balancing changed the pod, update the cached response
                if balanced_pod != original_pod:
                    logger.info(f"Cache load balancing: {original_pod} -> {balanced_pod}")
                    # Create new response with balanced pod
                    balanced_response = RoutingResponse(
                        selected_pod=balanced_pod,
                        confidence=cached_decision.confidence,
                        decision_type=cached_decision.decision_type + "_balanced",
                        state_encoded=cached_decision.state_encoded,
                        available_pods=available_pods,
                        decision_time_ms=cached_decision.decision_time_ms,
                        timestamp=datetime.now().isoformat()
                    )
                    increment_rotation_counter(request.service_name)
                    
                    step_times['cache_hit_balanced'] = (time.time() - step_start) * 1000
                    total_time = (time.time() - timing_start) * 1000
                    logger.info(f"TIMING - Cache hit (balanced): {total_time:.2f}ms total, steps: {step_times}")
                    return balanced_response
                else:
                    increment_rotation_counter(request.service_name)
                    
                    step_times['cache_hit'] = (time.time() - step_start) * 1000
                    total_time = (time.time() - timing_start) * 1000
                    logger.info(f"TIMING - Cache hit: {total_time:.2f}ms total, steps: {step_times}")
                    return cached_decision
        
        step_times['cache_check'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Step 4: Use RL agent to make decision
        try:
            selected_pod = rl_agent.select_action(service_metrics, target_service_instances)
            logger.debug(f"RL agent selected pod: {selected_pod} (from {len(target_service_instances)} instances)")
        except Exception as e:
            logger.error(f"RL agent selection failed: {e}")
            # Fallback to least recently used pod
            selected_pod = available_pods[0]
            logger.debug(f"Using fallback pod: {selected_pod}")
        
        step_times['rl_decision'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Step 5: Validate selected pod is in available pods
        if selected_pod not in available_pods:
            logger.warning(f"RL agent selected unavailable pod {selected_pod}, using rotation-aware fallback")
            # Use rotation-aware fallback
            selected_pod = available_pods[0]
        
        # Step 5.5: Apply load balancing override to prevent overuse
        selected_pod = apply_load_balancing_override(request.service_name, selected_pod, available_pods)
        
        step_times['validation_balancing'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Step 5: Determine decision type
        decision_type = "exploration" if rl_agent.current_epsilon > 0.1 else "exploitation"
        
        # Step 6: Calculate confidence based on Q-values
        state_key = rl_agent.current_state
        confidence = 0.8  # TODO: Calculate based on Q-value spread
        
        # Get Q-value for logging
        q_value = rl_agent.q_table.get((state_key, selected_pod), 0.0) if state_key else 0.0
        
        # Calculate response time
        decision_time = (time.time() - start_time.timestamp()) * 1000
        
        step_times['metrics_recording'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
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
        
        step_times['response_creation'] = (time.time() - step_start) * 1000
        total_time = (time.time() - timing_start) * 1000
        
        rl_logger.logger.info(f"RL decision for {request.service_name}: selected {selected_pod} "
                   f"(confidence: {confidence:.3f}, type: {decision_type}, "
                   f"total_time: {total_time:.3f}ms) total, steps: {step_times}")
        
        response = RoutingResponse(
            selected_pod=selected_pod,
            confidence=confidence,
            decision_type=decision_type,
            state_encoded=str(state_key) if state_key else "unknown",
            available_pods=available_pods,
            decision_time_ms=decision_time,
            timestamp=datetime.now().isoformat(),
            trace_id=trace_id
        )
        
        # Cache the decision for future requests
        decision_cache[cache_key] = (response, current_time)
        
        # Increment rotation counter
        increment_rotation_counter(request.service_name)
        
        # Periodic cache cleanup
        if len(decision_cache) > 200:
            cleanup_cache(decision_cache, CACHE_TTL_DECISION)
        if len(service_discovery_cache) > 50:
            cleanup_cache(service_discovery_cache, CACHE_TTL_SERVICES)
        if len(metrics_cache) > 100:
            cleanup_cache(metrics_cache, CACHE_TTL_METRICS)
        
        return response
        
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
async def provide_feedback(request: FeedbackRequest, x_trace_id: str = Header(None)):
    """
    Provide feedback for continuous learning and Q-table updates
    
    This endpoint allows the load balancer to report the outcome
    of routing decisions so the RL agent can learn and improve.
    """
    try:
        # Validate or generate trace ID
        if x_trace_id and validate_trace_id(x_trace_id):
            trace_id = x_trace_id.lower()
        else:
            trace_id = generate_trace_id()
        
        # Set trace ID in context for logging
        set_trace_id(trace_id)
            
        if not rl_agent:
            raise HTTPException(status_code=503, detail="RL agent not initialized")
        
        rl_logger.logger.info(f"Processing feedback: {request.service_name} -> {request.selected_pod} "
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
