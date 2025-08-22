

import asyncio
import aiohttp
import argparse
import json
import random
import time
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, asdict
from concurrent.futures import ThreadPoolExecutor
import logging
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('traffic_generator.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

@dataclass
class TrafficConfig:
    """Configuration for traffic generation"""
    load_balancer_url: str = "http://localhost:8080"
    duration_minutes: int = 30
    base_users: int = 10
    max_users: int = 100
    ramp_up_minutes: int = 5
    request_delay_range: tuple = (0.5, 3.0)  # seconds between requests
    burst_probability: float = 0.1  # 10% chance of traffic bursts
    burst_multiplier: float = 3.0

@dataclass
class UserSession:
    """Represents a user session with behavior patterns"""
    user_id: str
    session_start: datetime
    requests_made: int = 0
    last_request_time: Optional[datetime] = None
    user_type: str = "normal"  # normal, heavy, light
    preferred_service: Optional[str] = None

class MicroserviceAPI:
    """Define your microservice endpoints and realistic payloads"""

    # Customize these based on your actual microservices
    INVENTORY_ENDPOINTS = {
        "get_products": {
            "method": "GET",
            "path": "/api/inventory/products",
            "weight": 40,  # Frequency weight
            "params": lambda: {"category": random.choice(["electronics", "books", "clothing"])}
        },
        "get_product_detail": {
            "method": "GET",
            "path": "/api/inventory/products/{product_id}",
            "weight": 30,
            "path_params": lambda: {"product_id": random.randint(1, 100)}
        },
        "check_stock": {
            "method": "GET",
            "path": "/api/inventory/stock/{product_id}",
            "weight": 20,
            "path_params": lambda: {"product_id": random.randint(1, 100)}
        },
        "update_inventory": {
            "method": "POST",
            "path": "/api/inventory/update",
            "weight": 10,
            "json": lambda: {
                "product_id": random.randint(1, 100),
                "quantity": random.randint(1, 50)
            }
        }
    }

    ORDER_ENDPOINTS = {
        "create_order": {
            "method": "POST",
            "path": "/api/orders",
            "weight": 25,
            "json": lambda: {
                "user_id": random.randint(1, 1000),
                "items": [
                    {
                        "product_id": random.randint(1, 100),
                        "quantity": random.randint(1, 5)
                    } for _ in range(random.randint(1, 3))
                ],
                "total_amount": round(random.uniform(10, 500), 2)
            }
        },
        "get_orders": {
            "method": "GET",
            "path": "/api/orders",
            "weight": 35,
            "params": lambda: {"user_id": random.randint(1, 1000)}
        },
        "get_order_status": {
            "method": "GET",
            "path": "/api/orders/{order_id}/status",
            "weight": 25,
            "path_params": lambda: {"order_id": random.randint(1, 500)}
        },
        "cancel_order": {
            "method": "DELETE",
            "path": "/api/orders/{order_id}",
            "weight": 10,
            "path_params": lambda: {"order_id": random.randint(1, 500)}
        },
        "process_payment": {
            "method": "POST",
            "path": "/api/orders/payment",
            "weight": 15,
            "json": lambda: {
                "order_id": random.randint(1, 500),
                "payment_method": random.choice(["credit_card", "paypal", "bank_transfer"]),
                "amount": round(random.uniform(10, 500), 2)
            }
        }
    }

    @classmethod
    def get_random_endpoint(cls, service_type: str = None) -> Dict[str, Any]:
        """Get a random endpoint based on weights"""
        if service_type == "inventory":
            endpoints = cls.INVENTORY_ENDPOINTS
        elif service_type == "order":
            endpoints = cls.ORDER_ENDPOINTS
        else:
            # Random service selection
            all_endpoints = {**cls.INVENTORY_ENDPOINTS, **cls.ORDER_ENDPOINTS}
            endpoints = all_endpoints

        # Weighted random selection
        weights = [config["weight"] for config in endpoints.values()]
        endpoint_name = random.choices(list(endpoints.keys()), weights=weights)[0]
        return {"name": endpoint_name, **endpoints[endpoint_name]}

class TrafficGenerator:
    """Main traffic generator class"""

    def __init__(self, config: TrafficConfig):
        self.config = config
        self.active_sessions: Dict[str, UserSession] = {}
        self.stats = {
            "total_requests": 0,
            "successful_requests": 0,
            "failed_requests": 0,
            "response_times": [],
            "start_time": None,
            "service_distribution": {"inventory": 0, "order": 0}
        }

    async def generate_traffic(self):
        """Main traffic generation orchestrator"""
        logger.info(f"Starting traffic generation for {self.config.duration_minutes} minutes")
        logger.info(f"Load balancer: {self.config.load_balancer_url}")

        self.stats["start_time"] = datetime.now()

        async with aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=30),
                connector=aiohttp.TCPConnector(limit=200)
        ) as session:

            # Create task for user management
            user_manager_task = asyncio.create_task(self._manage_users())

            # Create task for statistics reporting
            stats_task = asyncio.create_task(self._report_stats())

            # Wait for the specified duration
            await asyncio.sleep(self.config.duration_minutes * 60)

            # Cancel background tasks
            user_manager_task.cancel()
            stats_task.cancel()

            # Wait for active sessions to complete
            await self._cleanup_sessions(session)

        logger.info("Traffic generation completed")
        self._print_final_stats()

    async def _manage_users(self):
        """Manage user sessions - add/remove users over time"""
        try:
            while True:
                current_time = datetime.now()
                elapsed_minutes = (current_time - self.stats["start_time"]).total_seconds() / 60

                # Calculate target user count with ramp-up
                if elapsed_minutes < self.config.ramp_up_minutes:
                    progress = elapsed_minutes / self.config.ramp_up_minutes
                    target_users = int(self.config.base_users +
                                       (self.config.max_users - self.config.base_users) * progress)
                else:
                    target_users = self.config.max_users

                # Apply burst traffic occasionally
                if random.random() < self.config.burst_probability:
                    target_users = int(target_users * self.config.burst_multiplier)
                    logger.info(f"Traffic burst! Target users: {target_users}")

                # Adjust user count
                current_users = len(self.active_sessions)

                if current_users < target_users:
                    # Add users
                    for _ in range(target_users - current_users):
                        await self._add_user()
                elif current_users > target_users:
                    # Remove users
                    for _ in range(current_users - target_users):
                        self._remove_user()

                await asyncio.sleep(10)  # Check every 10 seconds

        except asyncio.CancelledError:
            pass

    async def _add_user(self):
        """Add a new user session"""
        user_id = f"user_{random.randint(10000, 99999)}"
        user_type = random.choices(
            ["light", "normal", "heavy"],
            weights=[20, 60, 20]
        )[0]

        session = UserSession(
            user_id=user_id,
            session_start=datetime.now(),
            user_type=user_type,
            preferred_service=random.choice(["inventory", "order", None])
        )

        self.active_sessions[user_id] = session

        # Start user activity
        asyncio.create_task(self._simulate_user_activity(session))

    def _remove_user(self):
        """Remove a random user session"""
        if self.active_sessions:
            user_id = random.choice(list(self.active_sessions.keys()))
            del self.active_sessions[user_id]

    async def _simulate_user_activity(self, session: UserSession):
        """Simulate individual user activity"""
        async with aiohttp.ClientSession() as http_session:
            try:
                while session.user_id in self.active_sessions:
                    # Determine request delay based on user type
                    if session.user_type == "light":
                        delay = random.uniform(3.0, 8.0)
                    elif session.user_type == "heavy":
                        delay = random.uniform(0.2, 1.0)
                    else:  # normal
                        delay = random.uniform(*self.config.request_delay_range)

                    await asyncio.sleep(delay)

                    # Make a request
                    await self._make_request(http_session, session)

            except asyncio.CancelledError:
                pass
            except Exception as e:
                logger.error(f"Error in user {session.user_id} activity: {e}")

    async def _make_request(self, session: aiohttp.ClientSession, user_session: UserSession):
        """Make a single HTTP request"""
        try:
            # Get endpoint configuration
            endpoint = MicroserviceAPI.get_random_endpoint(user_session.preferred_service)

            # Build URL
            url = self.config.load_balancer_url + endpoint["path"]

            # Handle path parameters
            if "path_params" in endpoint:
                params = endpoint["path_params"]()
                url = url.format(**params)

            # Prepare request arguments
            request_args = {
                "headers": {
                    "User-Agent": f"TrafficGen-{user_session.user_id}",
                    "X-User-ID": user_session.user_id,
                    "X-Session-ID": f"session_{user_session.session_start.timestamp()}"
                }
            }

            # Add query parameters if specified
            if "params" in endpoint:
                request_args["params"] = endpoint["params"]()

            # Add JSON body if specified
            if "json" in endpoint:
                request_args["json"] = endpoint["json"]()
                request_args["headers"]["Content-Type"] = "application/json"

            # Make the request
            start_time = time.time()

            async with session.request(endpoint["method"], url, **request_args) as response:
                await response.text()  # Consume response body

                response_time = time.time() - start_time

                # Update statistics
                self.stats["total_requests"] += 1
                self.stats["response_times"].append(response_time)

                if response.status < 400:
                    self.stats["successful_requests"] += 1
                else:
                    self.stats["failed_requests"] += 1
                    logger.warning(f"Request failed: {response.status} - {url}")

                # Track service distribution
                if "inventory" in url:
                    self.stats["service_distribution"]["inventory"] += 1
                elif "order" in url:
                    self.stats["service_distribution"]["order"] += 1

                # Update user session
                user_session.requests_made += 1
                user_session.last_request_time = datetime.now()

                logger.debug(f"Request completed: {endpoint['method']} {url} - "
                             f"{response.status} - {response_time:.3f}s")

        except Exception as e:
            self.stats["failed_requests"] += 1
            logger.error(f"Request error: {e}")

    async def _report_stats(self):
        """Periodically report statistics"""
        try:
            while True:
                await asyncio.sleep(60)  # Report every minute
                self._print_current_stats()
        except asyncio.CancelledError:
            pass

    def _print_current_stats(self):
        """Print current statistics"""
        elapsed = (datetime.now() - self.stats["start_time"]).total_seconds()
        rps = self.stats["total_requests"] / elapsed if elapsed > 0 else 0

        avg_response_time = (sum(self.stats["response_times"]) /
                             len(self.stats["response_times"])
                             if self.stats["response_times"] else 0)

        logger.info(f"STATS - Active Users: {len(self.active_sessions)}, "
                    f"Total Requests: {self.stats['total_requests']}, "
                    f"RPS: {rps:.2f}, "
                    f"Success Rate: {self.stats['successful_requests'] / max(1, self.stats['total_requests']) * 100:.1f}%, "
                    f"Avg Response Time: {avg_response_time:.3f}s")

    def _print_final_stats(self):
        """Print final comprehensive statistics"""
        elapsed = (datetime.now() - self.stats["start_time"]).total_seconds()

        print("\n" + "="*60)
        print("TRAFFIC GENERATION SUMMARY")
        print("="*60)
        print(f"Duration: {elapsed:.1f} seconds")
        print(f"Total Requests: {self.stats['total_requests']}")
        print(f"Successful Requests: {self.stats['successful_requests']}")
        print(f"Failed Requests: {self.stats['failed_requests']}")
        print(f"Success Rate: {self.stats['successful_requests'] / max(1, self.stats['total_requests']) * 100:.1f}%")
        print(f"Average RPS: {self.stats['total_requests'] / elapsed:.2f}")

        if self.stats["response_times"]:
            response_times = sorted(self.stats["response_times"])
            print(f"Response Time Stats:")
            print(f"  Average: {sum(response_times) / len(response_times):.3f}s")
            print(f"  Median: {response_times[len(response_times)//2]:.3f}s")
            print(f"  95th Percentile: {response_times[int(len(response_times)*0.95)]:.3f}s")
            print(f"  99th Percentile: {response_times[int(len(response_times)*0.99)]:.3f}s")

        print(f"Service Distribution:")
        total_service_requests = sum(self.stats["service_distribution"].values())
        for service, count in self.stats["service_distribution"].items():
            percentage = count / max(1, total_service_requests) * 100
            print(f"  {service}: {count} ({percentage:.1f}%)")

        print("="*60)

    async def _cleanup_sessions(self, session: aiohttp.ClientSession):
        """Clean up remaining sessions"""
        logger.info("Cleaning up active sessions...")
        # Give active requests time to complete
        await asyncio.sleep(5)

def main():
    """Main entry point"""
    parser = argparse.ArgumentParser(description="Professional Traffic Generator for Microservices")
    parser.add_argument("--url", default="http://localhost:8080",
                        help="Load balancer URL")
    parser.add_argument("--duration", type=int, default=30,
                        help="Duration in minutes")
    parser.add_argument("--base-users", type=int, default=10,
                        help="Base number of concurrent users")
    parser.add_argument("--max-users", type=int, default=100,
                        help="Maximum number of concurrent users")
    parser.add_argument("--ramp-up", type=int, default=5,
                        help="Ramp-up time in minutes")
    parser.add_argument("--config-file", type=str,
                        help="JSON configuration file")

    args = parser.parse_args()

    # Load configuration
    if args.config_file and Path(args.config_file).exists():
        with open(args.config_file) as f:
            config_data = json.load(f)
        config = TrafficConfig(**config_data)
    else:
        config = TrafficConfig(
            load_balancer_url=args.url,
            duration_minutes=args.duration,
            base_users=args.base_users,
            max_users=args.max_users,
            ramp_up_minutes=args.ramp_up
        )

    # Create and run traffic generator
    generator = TrafficGenerator(config)

    try:
        asyncio.run(generator.generate_traffic())
    except KeyboardInterrupt:
        logger.info("Traffic generation interrupted by user")
    except Exception as e:
        logger.error(f"Traffic generation failed: {e}")
        return 1

    return 0

if __name__ == "__main__":
    exit(main())
