"""
RL Decision API - FastAPI service for real-time routing decisions
Provides intelligent load balancing decisions based on trained RL models
"""

from fastapi import FastAPI, HTTPException, Response, Header
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
from contextlib import asynccontextmanager
import uvicorn
import asyncio
from datetime import datetime
import logging
import time
import hashlib
import random
import secrets
from concurrent.futures import ThreadPoolExecutor
import threading
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

# Global components
rl_agent: Optional[QLearningAgent] = None
prometheus_client: Optional[PrometheusClient] = None
lb_client: Optional[LoadBalancerClient] = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Modern FastAPI lifespan event handler"""
    global rl_agent, prometheus_client, lb_client
    
    try:
        logger.info("Initializing RL Decision API...")
        
        # Initialize components with proper error handling
        if QLearningAgent is not None:
            rl_agent = QLearningAgent()
            # Load existing models if available
            rl_agent.load_model()
        else:
            logger.warning("QLearningAgent not available, using mock")
            rl_agent = None
            
        if PrometheusClient is not None:
            prometheus_client = PrometheusClient()
        else:
            logger.warning("PrometheusClient not available, using mock")
            prometheus_client = None
            
        if LoadBalancerClient is not None:
            lb_client = LoadBalancerClient()
        else:
            logger.warning("LoadBalancerClient not available, using mock")
            lb_client = None
        
        logger.info("RL Decision API initialized successfully")
        yield
        
    except Exception as e:
        logger.error(f"Failed to initialize RL Decision API: {e}")
        raise

app = FastAPI(
    title="RL Decision API",
    description="Intelligent load balancing decisions using reinforcement learning",
    version="1.0.0",
    lifespan=lifespan
)

# Global variables for caching and performance tracking
service_discovery_cache = {}
metrics_cache = {}
decision_cache = {}
load_balancing_tracker = {}
decision_context_cache = {}  # Store decision context for feedback processing
cache_rotation_counter = {}  # Track rotation counters for cache keys
# performance_collector = performance_collector  # Already imported above
# prometheus_exporter = prometheus_exporter      # Already imported above

# Cache configuration - optimized for real-time learning
SERVICE_DISCOVERY_TTL = 5000   # 5 seconds (reduced from 10s)
METRICS_TTL = 500              # 500ms (reduced from 2s for real-time metrics)
DECISION_TTL = 50              # 50ms (reduced from 200ms for minimal caching)
DECISION_CONTEXT_TTL = 30000   # 30 seconds for decision context (unchanged)
CACHE_ROTATION_INTERVAL = 2000 # 2 seconds (reduced from 5s)

# Performance optimization flags - optimized for real-time learning
ENABLE_FAST_DECISION_CACHE = False  # Disabled for real-time learning
ENABLE_METRICS_SKIP_FOR_CACHE = False  # Disabled to ensure fresh metrics

# Circuit breaker for RL agent failures
class CircuitBreaker:
    def __init__(self, failure_threshold=5, recovery_timeout=30):
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.failure_count = 0
        self.last_failure_time = None
        self.state = "CLOSED"  # CLOSED, OPEN, HALF_OPEN
        self.lock = threading.Lock()
    
    def call(self, func, *args, **kwargs):
        with self.lock:
            if self.state == "OPEN":
                if time.time() - self.last_failure_time > self.recovery_timeout:
                    self.state = "HALF_OPEN"
                    logger.info("Circuit breaker transitioning to HALF_OPEN")
                else:
                    raise Exception("Circuit breaker OPEN - RL agent unavailable")
            
            try:
                result = func(*args, **kwargs)
                if self.state == "HALF_OPEN":
                    self.state = "CLOSED"
                    self.failure_count = 0
                    logger.info("Circuit breaker CLOSED - RL agent recovered")
                return result
            except Exception as e:
                self.failure_count += 1
                self.last_failure_time = time.time()
                
                if self.failure_count >= self.failure_threshold:
                    self.state = "OPEN"
                    logger.error(f"Circuit breaker OPEN - RL agent failed {self.failure_count} times")
                
                raise e
    
    def get_state(self):
        return self.state

# Thread pool for async cache cleanup
cache_cleanup_executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="cache-cleanup")
cache_lock = threading.RLock()  # Thread-safe cache operations

# Global circuit breaker instance
rl_circuit_breaker = CircuitBreaker(failure_threshold=3, recovery_timeout=20)

CACHE_TTL_DECISION = 0.05  # Reduced to 50ms for real-time learning
CACHE_TTL_SERVICES = 5.0   # Reduced to 5 seconds for service discovery
CACHE_TTL_METRICS = 0.5    # Reduced to 500ms for real-time metrics
ROTATION_INTERVAL = 3  # Rotate cache every 3 requests for better load distribution

def get_cache_key(service_name: str, metrics_hash: str, instance_count: int) -> str:
    """Generate optimized cache key for decision caching with smart rotation"""
    # Use simplified rotation for better cache hit rates
    rotation_key = f"rotation_counter:{service_name}"
    if rotation_key not in cache_rotation_counter:
        cache_rotation_counter[rotation_key] = 0
    
    counter = cache_rotation_counter[rotation_key]
    # Use modulo for cyclic rotation instead of division for better distribution
    rotation_suffix = counter % (ROTATION_INTERVAL * instance_count) if instance_count > 0 else 0
    
    # Simplified cache key - remove metrics_hash for higher hit rates when metrics are similar
    return f"{service_name}:{instance_count}:r{rotation_suffix}"

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
        if selected_count >= 8:  # Used 8+ times in last 6-9 decisions
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
    """Generate optimized hash of current metrics for caching"""
    try:
        if not metrics:
            return "empty"
        # Ultra-simplified hash for maximum cache hit rates
        key_values = []
        for metric in metrics[:1]:  # Only use first metric for speed
            if hasattr(metric, 'cpu_usage_percent') and metric.cpu_usage_percent is not None:
                # Larger buckets for better cache hit rates
                key_values.append(f"cpu:{int(metric.cpu_usage_percent/10)*10}")  # 10% buckets
            if hasattr(metric, 'avg_response_time_ms') and metric.avg_response_time_ms is not None:
                # Larger buckets for response time
                key_values.append(f"rt:{int(metric.avg_response_time_ms/200)*200}")  # 200ms buckets
        # Use shorter hash for better performance
        return hashlib.md5("|".join(key_values).encode()).hexdigest()[:4]  # Even shorter hash
    except Exception:
        # Static fallback for consistent caching during errors
        return "stable"

def cleanup_cache(cache_dict: dict, ttl: float):
    """Clean expired entries from cache with thread safety"""
    with cache_lock:
        current_time = time.time()
        expired_keys = [k for k, (_, cache_time) in cache_dict.items() if current_time - cache_time > ttl * 2]
        for k in expired_keys[:50]:  # Remove up to 50 expired entries
            cache_dict.pop(k, None)

def async_cache_cleanup():
    """Background cache cleanup task"""
    def cleanup_task():
        cleanup_cache(decision_cache, CACHE_TTL_DECISION)
        cleanup_cache(service_discovery_cache, CACHE_TTL_SERVICES)
        cleanup_cache(metrics_cache, CACHE_TTL_METRICS)
    
    executor.submit(cleanup_task)

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

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Modern FastAPI lifespan event handler"""
    global rl_agent, prometheus_client, lb_client
    
    try:
        logger.info("Initializing RL Decision API...")
        
        # Initialize components with proper error handling
        if QLearningAgent is not None:
            rl_agent = QLearningAgent()
            # Load existing models if available
            rl_agent.load_model()
        else:
            logger.warning("QLearningAgent not available, using mock")
            rl_agent = None
            
        if PrometheusClient is not None:
            prometheus_client = PrometheusClient()
        else:
            logger.warning("PrometheusClient not available, using mock")
            prometheus_client = None
            
        if LoadBalancerClient is not None:
            lb_client = LoadBalancerClient()
        else:
            logger.warning("LoadBalancerClient not available, using mock")
            lb_client = None
        
        logger.info("RL Decision API initialized successfully")
        yield
        
    except Exception as e:
        logger.error(f"Failed to initialize RL Decision API: {e}")
        raise

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat(),
        "rl_agent_initialized": rl_agent is not None,
        "rl_agent_ready": rl_agent is not None,  # Add field expected by load balancer
        "q_table_size": len(rl_agent.q_table) if rl_agent else 0
    }

@app.post("/benchmark-mode")
async def toggle_benchmark_mode(enable: bool = True):
    """Enable/disable benchmark mode for optimized performance"""
    global rl_agent
    
    if not rl_agent:
        raise HTTPException(status_code=503, detail="RL agent not initialized")
    
    try:
        from config.rl_settings import rl_settings
        
        if enable:
            rl_settings.enable_benchmark_mode()
            # Update the RL agent's configuration
            rl_agent.config.benchmark_mode = True
            rl_agent.config.production_epsilon = 0.02
            rl_agent.current_epsilon = 0.02  # Set current epsilon to production value
            logger.info("Benchmark mode ENABLED - using production-optimized settings")
        else:
            rl_settings.disable_benchmark_mode()
            # Restore learning configuration
            rl_agent.config.benchmark_mode = False
            rl_agent.current_epsilon = rl_agent.config.epsilon_start
            logger.info("Benchmark mode DISABLED - restored learning settings")
        
        return {
            "benchmark_mode": enable,
            "current_epsilon": rl_agent.current_epsilon,
            "production_epsilon": getattr(rl_agent.config, 'production_epsilon', 0.02),
            "exploration_episodes": rl_agent.config.exploration_episodes,
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"Failed to toggle benchmark mode: {e}")
        raise HTTPException(status_code=500, detail=str(e))

def generate_trace_id() -> str:
    """Generate OpenTelemetry-compatible 32-digit hex trace ID"""
    return secrets.token_hex(16)  # 16 bytes = 32 hex characters

def validate_trace_id(trace_id: str) -> bool:
    """Validate if trace ID follows OpenTelemetry 32-digit hex format"""
    return trace_id is not None and len(trace_id) == 32 and all(c in '0123456789abcdef' for c in trace_id.lower())

def calculate_pod_specific_reward(
    response_time_ms: float,
    status_code: int,
    error_occurred: bool,
    selected_pod: str,
    current_metrics: List
) -> float:
    """
    Calculate reward based on individual pod performance, not aggregate metrics
    This fixes the issue where good pods get negative rewards due to bad pods
    """
    reward = 0.0
    
    # 1. Response time component (pod-specific)
    if response_time_ms <= 50:
        time_reward = 1.0
    elif response_time_ms <= 100:
        time_reward = 0.8
    elif response_time_ms <= 200:
        time_reward = 0.5
    elif response_time_ms <= 500:
        time_reward = 0.2
    else:
        time_reward = -0.5
    
    reward += time_reward * 0.5  # 50% weight
    
    # 2. Status code component
    if 200 <= status_code < 300:
        status_reward = 0.5
    elif 300 <= status_code < 400:
        status_reward = 0.2
    elif 400 <= status_code < 500:
        status_reward = -0.3
    else:  # 500+ errors
        status_reward = -0.8
    
    reward += status_reward * 0.3  # 30% weight
    
    # 3. Error occurrence penalty
    if error_occurred:
        reward -= 0.5
    
    # 4. Pod-specific metrics bonus/penalty
    if current_metrics and len(current_metrics) > 0:
        try:
            # Find metrics for the selected pod
            for metric in current_metrics:
                if hasattr(metric, 'instance_id') and metric.instance_id == selected_pod:
                    # CPU usage bonus/penalty (more balanced)
                    if hasattr(metric, 'cpu_usage_percent') and metric.cpu_usage_percent is not None:
                        cpu_usage = metric.cpu_usage_percent
                        if cpu_usage < 30:  # Low CPU usage
                            reward += 0.15
                        elif cpu_usage > 90:  # Very high CPU usage
                            reward -= 0.4  # Strong penalty for 90%+ CPU
                        elif cpu_usage > 70:  # High CPU usage
                            reward -= 0.2  # Moderate penalty for 70-90% CPU
                    
                    # Memory usage bonus/penalty
                    if hasattr(metric, 'jvm_memory_usage_percent') and metric.jvm_memory_usage_percent is not None:
                        memory_usage = metric.jvm_memory_usage_percent
                        if memory_usage < 70:  # Low memory usage
                            reward += 0.1
                        elif memory_usage > 90:  # High memory usage
                            reward -= 0.2
                    break
        except Exception as e:
            logger.debug(f"Error processing pod-specific metrics for reward: {e}")
    
    return reward

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
        
        # Check service discovery cache with thread safety
        with cache_lock:
            if service_cache_key in service_discovery_cache:
                cached_services, cache_time = service_discovery_cache[service_cache_key]
                if current_time - cache_time < CACHE_TTL_SERVICES:
                    target_service_instances = cached_services
                    rl_logger.logger.debug(f"Using cached service instances for {request.service_name}")
                else:
                    # Cache expired, fetch fresh
                    services = lb_client.get_registered_services()
                    # Find the service by name and extract its instances
                    target_service_instances = []
                    for service in services:
                        if service.get('name') == request.service_name:
                            target_service_instances = service.get('instances', [])
                            break
                    service_discovery_cache[service_cache_key] = (target_service_instances, current_time)
                    rl_logger.logger.info(f"Fetching registered services | url: http://localhost:8080/api/services")
            else:
                # No cache entry, fetch fresh
                services = lb_client.get_registered_services()
                # Find the service by name and extract its instances
                target_service_instances = []
                for service in services:
                    if service.get('name') == request.service_name:
                        target_service_instances = service.get('instances', [])
                        break
                service_discovery_cache[service_cache_key] = (target_service_instances, current_time)
                rl_logger.logger.info(f"Fetching registered services | url: http://localhost:8080/api/services") 
        
        step_times['service_discovery'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        if not target_service_instances:
            raise HTTPException(
                status_code=404,
                detail=f"No instances found for service: {request.service_name}"
            )
        
        # Step 2: Fast decision cache check (before expensive metrics collection)
        if ENABLE_FAST_DECISION_CACHE:
            fast_cache_key = f"fast_decision:{request.service_name}:{len(target_service_instances)}"
            with cache_lock:
                if fast_cache_key in decision_cache:
                    cached_decision, cache_time = decision_cache[fast_cache_key]
                    if (current_time - cache_time) * 1000 < DECISION_TTL:
                        # Fast path: return cached decision without metrics collection
                        selected_pod = cached_decision
                        if selected_pod in [str(inst.get('instanceName', inst)) for inst in target_service_instances]:
                            step_times['fast_cache_hit'] = (time.time() - step_start) * 1000
                            total_time = (time.time() - timing_start) * 1000
                            
                            response = RoutingResponse(
                                selected_pod=selected_pod,
                                confidence=0.9,  # High confidence for cached decisions
                                decision_type="fast_cached",
                                state_encoded="cached",
                                available_pods=[str(inst.get('instanceName', inst)) for inst in target_service_instances],
                                decision_time_ms=total_time,
                                timestamp=datetime.now().isoformat()
                            )
                            
                            rl_logger.logger.info(f"Fast cache hit: {request.service_name} -> {selected_pod} ({total_time:.2f}ms)")
                            return response
        
        # Step 2.1: Get real-time metrics from Prometheus (with caching)
        metrics_cache_key = f"metrics:{request.service_name}:{len(target_service_instances)}"
        
        with cache_lock:
            if metrics_cache_key in metrics_cache:
                cached_metrics, cache_time = metrics_cache[metrics_cache_key]
                if (current_time - cache_time) * 1000 < METRICS_TTL:
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
            if isinstance(instance, dict):
                # Handle dictionary format from load balancer
                pod_name = instance.get('instanceName') or instance.get('url', str(instance))
                available_pods.append(pod_name)
            elif hasattr(instance, 'instance_id'):
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
        
        # Step 3: Check decision cache with optimized rotation logic
        metrics_hash = get_metrics_hash(service_metrics)
        cache_key = get_cache_key(request.service_name, metrics_hash, len(target_service_instances))
        
        # Pre-increment counter for better load distribution
        increment_rotation_counter(request.service_name)
        
        with cache_lock:
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
                        timestamp=datetime.now().isoformat(),
                        trace_id=trace_id
                    )
                    step_times['cache_hit_balanced'] = (time.time() - step_start) * 1000
                    total_time = (time.time() - timing_start) * 1000
                    logger.info(f"TIMING - Cache hit (balanced): {total_time:.2f}ms total, steps: {step_times}")
                    return balanced_response
                else:
                    # Update trace_id in cached response
                    cached_decision.trace_id = trace_id
                    step_times['cache_hit'] = (time.time() - step_start) * 1000
                    total_time = (time.time() - timing_start) * 1000
                    logger.info(f"TIMING - Cache hit: {total_time:.2f}ms total, steps: {step_times}")
                    return cached_decision
        
        step_times['cache_check'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Step 4: Use RL agent to make decision with circuit breaker
        try:
            selected_pod = rl_circuit_breaker.call(
                rl_agent.select_action, service_metrics, target_service_instances
            )
            logger.debug(f"RL agent selected pod: {selected_pod} (from {len(target_service_instances)} instances)")
            decision_type = "rl_agent"
        except Exception as e:
            logger.warning(f"RL agent selection failed (circuit breaker: {rl_circuit_breaker.get_state()}): {e}")
            # Intelligent fallback based on load balancing tracker
            if len(available_pods) > 1:
                tracker_key = f"lb_tracker:{request.service_name}"
                recent_selections = load_balancing_tracker.get(tracker_key, [])
                if recent_selections:
                    # Find least recently used pod
                    pod_counts = {pod: recent_selections.count(pod) for pod in available_pods}
                    min_count = min(pod_counts.values())
                    least_used_pods = [pod for pod, count in pod_counts.items() if count == min_count]
                    selected_pod = random.choice(least_used_pods)
                else:
                    selected_pod = random.choice(available_pods)
            else:
                selected_pod = available_pods[0]
            logger.info(f"Using intelligent fallback pod: {selected_pod}")
            decision_type = "fallback_intelligent"
        
        step_times['rl_decision'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Step 5: Validate selected pod is in available pods
        if selected_pod not in available_pods:
            logger.warning(f"RL agent selected unavailable pod {selected_pod}, available: {available_pods}")
            # Try to find a matching pod by partial name match
            matching_pods = [pod for pod in available_pods if selected_pod in pod or pod in selected_pod]
            if matching_pods:
                selected_pod = matching_pods[0]
                logger.info(f"Found matching pod: {selected_pod}")
            else:
                # Use intelligent fallback based on load balancing
                tracker_key = f"lb_tracker:{request.service_name}"
                recent_selections = load_balancing_tracker.get(tracker_key, [])
                if recent_selections and len(available_pods) > 1:
                    # Find least used pod
                    pod_counts = {pod: recent_selections.count(pod) for pod in available_pods}
                    min_count = min(pod_counts.values())
                    least_used_pods = [pod for pod, count in pod_counts.items() if count == min_count]
                    selected_pod = random.choice(least_used_pods)
                else:
                    selected_pod = available_pods[0]
                logger.info(f"Using intelligent fallback pod: {selected_pod}")
        
        # Step 5.5: Apply load balancing override to prevent overuse
        selected_pod = apply_load_balancing_override(request.service_name, selected_pod, available_pods)
        
        step_times['validation_balancing'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Step 5: Determine decision type (if not already set by fallback)
        if decision_type == "rl_agent":
            decision_type = "exploration" if rl_agent.current_epsilon > 0.1 else "exploitation"
        
        # Fast-path: Skip state initialization if using cached decision
        if decision_type != "cached":
            # Ensure we have valid state information for Q-table updates
            if rl_agent.current_state is None:
                # Initialize current state for first-time use
                rl_agent.current_state = rl_agent.state_encoder.encode_state(service_metrics)
                logger.debug(f"Initialized RL agent state: {rl_agent.current_state}")
        
        # Step 6: Calculate confidence based on Q-values
        state_key = rl_agent.current_state
        confidence = 0.8  # TODO: Calculate based on Q-value spread
        
        # Get Q-value for logging - use the actual selected pod name
        q_value = rl_agent.q_table.get((state_key, selected_pod), 0.0) if state_key else 0.0
        step_times['metrics_recording'] = (time.time() - step_start) * 1000
        step_start = time.time()
        
        # Calculate total decision time
        decision_time = (time.time() - timing_start) * 1000
        
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
        
        # Store decision context for feedback processing (only for RL decisions)
        if decision_type not in ['cached', 'fast_cached'] and rl_agent:
            # Encode current metrics into state for learning
            current_state = rl_agent.state_encoder.encode_state(service_metrics)
            
            decision_context_key = f"{request.service_name}:{selected_pod}:{int(current_time * 1000)}"
            decision_context_cache[decision_context_key] = {
                'previous_state': rl_agent.current_state,  # Use agent's current state as previous
                'current_state': current_state,            # Newly encoded state
                'last_action': selected_pod,
                'service_metrics': service_metrics,
                'timestamp': current_time
            }
            
            # Update agent's state for next decision
            rl_agent.previous_state = rl_agent.current_state
            rl_agent.current_state = current_state
            rl_agent.last_action = selected_pod
            
            logger.info(f"State transition for {request.service_name}: {rl_agent.previous_state} -> {current_state}, action: {selected_pod}")

        # Create the main response object
        response = RoutingResponse(
            selected_pod=selected_pod,
            confidence=confidence,
            decision_type=decision_type,
            state_encoded=str(rl_agent.current_state) if rl_agent and rl_agent.current_state else "unknown",
            available_pods=available_pods,
            decision_time_ms=decision_time,
            timestamp=datetime.now().isoformat(),
            trace_id=trace_id
        )
        
        # Cache the decision for future requests
        with cache_lock:
            # Store the complete response object in cache instead of just the pod name
            if 'cache_key' in locals():
                decision_cache[cache_key] = (response, current_time)
            
            # Fast cache for next request (simplified key)
            if ENABLE_FAST_DECISION_CACHE:
                fast_cache_key = f"fast_decision:{request.service_name}:{len(target_service_instances)}"
                decision_cache[fast_cache_key] = (selected_pod, current_time)  # Keep simple for fast cache
        
        step_times['response_creation'] = (time.time() - step_start) * 1000
        
        # Log comprehensive timing information
        total_time = (time.time() - timing_start) * 1000
        
        # Detailed logging only for non-cached decisions to reduce overhead
        if decision_type in ['fast_cached', 'cached']:
            rl_logger.logger.debug(f"Cached decision for {request.service_name}: {selected_pod} ({total_time:.1f}ms)")
        else:
            rl_logger.logger.info(f"RL decision for {request.service_name}: selected {selected_pod} "
                       f"(confidence: {confidence:.3f}, type: {decision_type}, total_time: {total_time:.3f}ms) "
                       f"total, steps: {step_times}")
        
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
        # Find the service by name and extract its instances
        target_service_instances = []
        for service in services:
            if service.get('name') == request.service_name:
                target_service_instances = service.get('instances', [])
                break
        
        if target_service_instances:
            current_metrics = prometheus_client.get_service_metrics(target_service_instances)
        else:
            logger.warning(f"No instances found for service {request.service_name} in feedback")
            current_metrics = []
        
        # Try to find stored decision context for this feedback
        decision_context = None
        current_time = time.time()
        
        # Look for matching decision context (within last 30 seconds)
        for context_key, context in list(decision_context_cache.items()):
            if (context['last_action'] == request.selected_pod and 
                context_key.startswith(request.service_name) and
                (current_time - context['timestamp']) < 30):  # 30 second window
                decision_context = context
                # Remove used context to prevent reuse
                del decision_context_cache[context_key]
                break
        
        # Check if we have state information for Q-learning update
        has_state_info = False
        if decision_context:
            # Use stored decision context
            previous_state = decision_context['previous_state']
            current_state = decision_context['current_state']
            last_action = decision_context['last_action']
            previous_metrics = decision_context['service_metrics']
            has_state_info = (previous_state is not None and last_action is not None and current_state is not None)
            logger.info(f"Using stored decision context for feedback: {request.service_name} -> {request.selected_pod}")
        else:
            # Fallback to current RL agent state
            previous_state = rl_agent.previous_state
            current_state = rl_agent.current_state
            last_action = rl_agent.last_action
            previous_metrics = getattr(rl_agent, 'previous_metrics', current_metrics)
            has_state_info = (previous_state is not None and last_action is not None and current_state is not None)
        
        if has_state_info:
            # Create a pod-specific reward calculation
            reward = calculate_pod_specific_reward(
                response_time_ms=request.response_time_ms,
                status_code=request.status_code,
                error_occurred=request.error_occurred,
                selected_pod=request.selected_pod,
                current_metrics=current_metrics
            )
            
            # Temporarily set RL agent state for Q-table update
            original_previous_state = rl_agent.previous_state
            original_current_state = rl_agent.current_state
            original_last_action = rl_agent.last_action
            
            try:
                # Set the state from decision context
                rl_agent.previous_state = previous_state
                rl_agent.current_state = current_state
                rl_agent.last_action = last_action
                
                # Update Q-table with the actual learning algorithm
                rl_agent.update_q_table(current_metrics, previous_metrics)
                
            finally:
                # Restore original state (in case of concurrent requests)
                rl_agent.previous_state = original_previous_state
                rl_agent.current_state = original_current_state
                rl_agent.last_action = original_last_action
            
            # Store current metrics for next iteration
            rl_agent.previous_metrics = current_metrics
            
            # Increment step counter for episode tracking
            rl_agent.increment_step()
            
            logger.info(f"Q-table updated: {request.service_name} -> {request.selected_pod} "
                       f"(reward: {reward:.3f}, Q-table size: {len(rl_agent.q_table)})")
            
            # Get updated Q-value for the state-action pair
            state_action_key = (previous_state, last_action)
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
            # Calculate reward but can't update Q-table yet using pod-specific calculation
            reward = calculate_pod_specific_reward(
                response_time_ms=request.response_time_ms,
                status_code=request.status_code,
                error_occurred=request.error_occurred,
                selected_pod=request.selected_pod,
                current_metrics=current_metrics
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
        raise HTTPException(status_code=500, detail=str(e))

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
