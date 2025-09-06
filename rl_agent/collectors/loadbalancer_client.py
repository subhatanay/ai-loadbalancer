import requests
import logging
from typing import List, Dict, Any, Optional
from config.settings import LOAD_BALANCER_HOST, LOAD_BALANCER_PORT
from utils.simple_cache import SERVICE_CACHE

logger = logging.getLogger(__name__)

class LoadBalancerClient:
    def __init__(self):
        self.base_url = f"http://{LOAD_BALANCER_HOST}:{LOAD_BALANCER_PORT}"
        self.timeout = 10  # seconds
        logger.info(f"LoadBalancerClient initialized with base_url: {self.base_url}")
    
    def get_registered_services(self) -> List[Dict[str, Any]]:
        """
        Fetch all registered services from the load balancer with caching
        Returns list of service dictionaries with instance information
        Cache TTL: 30 seconds
        """
        cache_key = "all_services"
        
        # Try cache first
        cached_services = SERVICE_CACHE.get(cache_key)
        if cached_services is not None:
            logger.debug(f"Using cached services ({len(cached_services)} services)")
            return cached_services
        
        # Cache miss - fetch from API
        try:
            url = f"{self.base_url}/api/services"
            logger.debug(f"Fetching registered services from: {url}")
            
            response = requests.get(url, timeout=self.timeout)
            response.raise_for_status()
            
            services = response.json()
            logger.debug(f"Retrieved {len(services)} registered services from API")
            
            # Cache the result
            SERVICE_CACHE.set(cache_key, services)
            
            return services
            
        except requests.exceptions.Timeout:
            logger.error(f"Timeout while fetching services from {url}")
            return []
        except requests.exceptions.RequestException as e:
            logger.error(f"Error fetching services: {e}")
            return []
        except Exception as e:
            logger.error(f"Unexpected error in get_registered_services: {e}")
            return []
    
    def get_service_instances(self, service_name: str) -> List[Dict[str, Any]]:
        """
        Get instances for a specific service with caching
        Cache TTL: 30 seconds per service
        """
        cache_key = f"service_instances_{service_name}"
        
        # Try cache first
        cached_instances = SERVICE_CACHE.get(cache_key)
        if cached_instances is not None:
            logger.debug(f"Using cached instances for {service_name} ({len(cached_instances)} instances)")
            return cached_instances
        
        # Cache miss - fetch from API
        try:
            url = f"{self.base_url}/api/services/{service_name}/instances"
            logger.debug(f"Fetching instances for service {service_name} from: {url}")
            
            response = requests.get(url, timeout=self.timeout)
            response.raise_for_status()
            
            instances = response.json()
            logger.debug(f"Retrieved {len(instances)} instances for service {service_name} from API")
            
            # Cache the result
            SERVICE_CACHE.set(cache_key, instances)
            
            return instances
            
        except requests.exceptions.Timeout:
            logger.error(f"Timeout while fetching instances for {service_name}")
            return []
        except requests.exceptions.RequestException as e:
            logger.error(f"Error fetching instances for {service_name}: {e}")
            return []
        except Exception as e:
            logger.error(f"Unexpected error in get_service_instances: {e}")
            return []
    
    def invalidate_cache(self, service_name: Optional[str] = None) -> None:
        """
        Invalidate cache entries for debugging/testing
        If service_name is provided, only invalidate that service's cache
        """
        if service_name:
            cache_key = f"service_instances_{service_name}"
            SERVICE_CACHE.delete(cache_key)
            logger.debug(f"Invalidated cache for service: {service_name}")
        else:
            SERVICE_CACHE.clear()
            logger.debug("Invalidated all service discovery cache")
    
    def get_cache_stats(self) -> Dict[str, Any]:
        """Get service discovery cache statistics"""
        return SERVICE_CACHE.get_stats()
    
    def health_check(self) -> bool:
        """Check if load balancer is accessible"""
        try:
            url = f"{self.base_url}/actuator/health"
            response = requests.get(url, timeout=5)
            return response.status_code == 200
        except:
            return False