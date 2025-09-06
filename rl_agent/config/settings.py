from pydantic_settings import BaseSettings
from typing import List, Dict

class Settings(BaseSettings):
    # Load Balancer Configuration
    loadbalancer_host: str = "localhost"
    loadbalancer_port: int = 8080
    loadbalancer_api_path: str = "/api/services"

    # Prometheus Configuration
    prometheus_host: str = "localhost"
    prometheus_port: int = 9090

    # Metrics Collection
    collection_interval_seconds: int = 60
    metrics_time_range: str = "1m"

    # Logging
    log_level: str = "INFO"
    log_format: str = "json"

    # Key Metrics to Collect
    target_metrics: List[str] = [
        "system_cpu_usage",
        "jvm_memory_used_bytes",
        "jvm_memory_max_bytes",
        "process_uptime_seconds",
        "http_server_requests_seconds_count",
        "http_server_requests_seconds_sum",
        "http_server_requests_seconds_bucket"
    ]

    class Config:
        env_prefix = "RL_AGENT_"

settings = Settings()

# Legacy compatibility exports
LOAD_BALANCER_HOST = settings.loadbalancer_host
LOAD_BALANCER_PORT = settings.loadbalancer_port
PROMETHEUS_HOST = settings.prometheus_host
PROMETHEUS_PORT = settings.prometheus_port