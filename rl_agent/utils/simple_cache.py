"""
Lightweight caching utility for RL Agent performance optimization
Thread-safe, TTL-based caching with automatic cleanup
"""
import time
import threading
from typing import Any, Optional, Dict
from utils.logger import logger

class SimpleCache:
    """
    Thread-safe TTL-based cache implementation
    
    Features:
    - TTL (Time To Live) expiration
    - Thread-safe operations
    - Automatic cleanup of expired entries
    - Memory-efficient storage
    """
    
    def __init__(self, ttl_seconds: int, name: str = "cache"):
        self.ttl = ttl_seconds
        self.name = name
        self.cache: Dict[str, Dict] = {}
        self.lock = threading.RLock()
        
        # Statistics
        self.hits = 0
        self.misses = 0
        
        logger.debug(f"SimpleCache '{name}' initialized with TTL={ttl_seconds}s")
    
    def get(self, key: str) -> Optional[Any]:
        """Get value from cache if not expired"""
        with self.lock:
            if key not in self.cache:
                self.misses += 1
                logger.debug(f"Cache MISS: {self.name}[{key}]")
                return None
            
            entry = self.cache[key]
            
            # Check if expired
            if self._is_expired(entry):
                del self.cache[key]
                self.misses += 1
                logger.debug(f"Cache EXPIRED: {self.name}[{key}]")
                return None
            
            self.hits += 1
            logger.debug(f"Cache HIT: {self.name}[{key}]")
            return entry['value']
    
    def set(self, key: str, value: Any) -> None:
        """Store value in cache with current timestamp"""
        with self.lock:
            self.cache[key] = {
                'value': value,
                'timestamp': time.time()
            }
            logger.debug(f"Cache SET: {self.name}[{key}]")
    
    def delete(self, key: str) -> bool:
        """Remove specific key from cache"""
        with self.lock:
            if key in self.cache:
                del self.cache[key]
                logger.debug(f"Cache DELETE: {self.name}[{key}]")
                return True
            return False
    
    def clear(self) -> None:
        """Clear all cache entries"""
        with self.lock:
            count = len(self.cache)
            self.cache.clear()
            self.hits = 0
            self.misses = 0
            logger.debug(f"Cache CLEAR: {self.name} ({count} entries removed)")
    
    def cleanup_expired(self) -> int:
        """Remove expired entries and return count of removed items"""
        with self.lock:
            current_time = time.time()
            expired_keys = []
            
            for key, entry in self.cache.items():
                if (current_time - entry['timestamp']) > self.ttl:
                    expired_keys.append(key)
            
            for key in expired_keys:
                del self.cache[key]
            
            if expired_keys:
                logger.debug(f"Cache CLEANUP: {self.name} ({len(expired_keys)} expired entries removed)")
            
            return len(expired_keys)
    
    def get_stats(self) -> Dict[str, Any]:
        """Get cache statistics"""
        with self.lock:
            total_requests = self.hits + self.misses
            hit_rate = self.hits / total_requests if total_requests > 0 else 0.0
            
            return {
                'name': self.name,
                'size': len(self.cache),
                'hits': self.hits,
                'misses': self.misses,
                'hit_rate': hit_rate,
                'ttl_seconds': self.ttl
            }
    
    def _is_expired(self, entry: Dict) -> bool:
        """Check if cache entry is expired"""
        return (time.time() - entry['timestamp']) > self.ttl
    
    def __len__(self) -> int:
        """Return number of cached items"""
        with self.lock:
            return len(self.cache)
    
    def __contains__(self, key: str) -> bool:
        """Check if key exists and is not expired"""
        return self.get(key) is not None


class CacheManager:
    """
    Global cache manager for coordinating multiple caches
    """
    
    def __init__(self):
        self.caches: Dict[str, SimpleCache] = {}
        self.cleanup_thread = None
        self._start_cleanup_thread()
    
    def create_cache(self, name: str, ttl_seconds: int) -> SimpleCache:
        """Create and register a new cache"""
        cache = SimpleCache(ttl_seconds, name)
        self.caches[name] = cache
        logger.info(f"Cache created: {name} (TTL={ttl_seconds}s)")
        return cache
    
    def get_cache(self, name: str) -> Optional[SimpleCache]:
        """Get existing cache by name"""
        return self.caches.get(name)
    
    def cleanup_all_caches(self) -> Dict[str, int]:
        """Cleanup expired entries in all caches"""
        results = {}
        for name, cache in self.caches.items():
            results[name] = cache.cleanup_expired()
        return results
    
    def get_all_stats(self) -> Dict[str, Dict]:
        """Get statistics for all caches"""
        return {name: cache.get_stats() for name, cache in self.caches.items()}
    
    def _start_cleanup_thread(self):
        """Start background cleanup thread"""
        def cleanup_worker():
            while True:
                try:
                    time.sleep(30)  # Cleanup every 30 seconds
                    self.cleanup_all_caches()
                except Exception as e:
                    logger.error(f"Cache cleanup error: {e}")
        
        self.cleanup_thread = threading.Thread(target=cleanup_worker, daemon=True)
        self.cleanup_thread.start()
        logger.info("Cache cleanup thread started")


# Global cache manager instance
cache_manager = CacheManager()

# Pre-configured caches for RL agent components
SERVICE_CACHE = cache_manager.create_cache("services", 30)  # 30s TTL
METRICS_CACHE = cache_manager.create_cache("metrics", 10)   # 10s TTL
STATE_CACHE = cache_manager.create_cache("states", 15)      # 15s TTL
