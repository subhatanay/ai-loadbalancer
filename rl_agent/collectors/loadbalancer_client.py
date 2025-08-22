import requests
from typing import List, Optional
from models.metrics_model import ServiceInstance
from utils.logger import logger
from config.settings import settings
import json

class LoadBalancerClient:
    def __init__(self):
        self.base_url = f"http://{settings.loadbalancer_host}:{settings.loadbalancer_port}"
        self.timeout = 10

    def get_registered_services(self) -> List[ServiceInstance]:
        """Fetch registered services from the load balancer"""
        try:
            url = f"{self.base_url}{settings.loadbalancer_api_path}"
            logger.info("Fetching registered services", url=url)

            response = requests.get(url, timeout=self.timeout)
            response.raise_for_status()

            services_data = response.json()
            services = []

            # Updated to handle list of service dicts
            if isinstance(services_data, list):
                for service_entry in services_data:
                    service_name = service_entry.get("name", "unknown-service")
                    instances = service_entry.get("instances", [])

                    for instance in instances:
                        service = ServiceInstance(
                            service_name=service_name,
                            instance_id=instance.get("instanceName", instance.get("url", "unknown")),
                            url=instance.get("url", ""),
                            health_url=instance.get("healthUrl", ""),
                            status="healthy" if instance.get("healthy") else "unhealthy"
                        )
                        services.append(service)

            logger.info("Successfully fetched services",
                        total_services=len(services),
                        services=[s.instance_id for s in services])

            return services

        except requests.RequestException as e:
            logger.error("Error fetching services", error=str(e))
            return []

    def health_check(self) -> bool:
        """Check if load balancer is accessible"""
        try:
            url = f"{self.base_url}/actuator/health"
            response = requests.get(url, timeout=5)
            return response.status_code == 200
        except:
            return False