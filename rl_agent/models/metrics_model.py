from pydantic import BaseModel
from typing import Dict, List, Optional
from datetime import datetime

class ServiceInstance(BaseModel):
    service_name: str
    instance_id: str
    url: str
    health_url: str
    status: str = "unknown"

class MetricValue(BaseModel):
    metric_name: str
    value: float
    timestamp: datetime
    labels: Dict[str, str] = {}

class ServiceMetrics(BaseModel):
    service_name: str
    instance_id: str
    pod_name: str
    timestamp: datetime
    cpu_usage_percent: Optional[float] = None
    jvm_memory_usage_percent: Optional[float] = None
    uptime_seconds: Optional[float] = None
    request_rate_per_second: Optional[float] = None
    avg_response_time_ms: Optional[float] = None
    error_rate_percent: Optional[float] = None
    total_requests: Optional[int] = None

class SystemSnapshot(BaseModel):
    timestamp: datetime
    services: List[ServiceMetrics]
    total_services: int
    total_instances: int