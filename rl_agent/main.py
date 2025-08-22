import schedule
import time
import signal
import sys
from datetime import datetime
from collectors.loadbalancer_client import LoadBalancerClient
from collectors.prometheus_client import PrometheusClient
from models.metrics_model import SystemSnapshot
from utils.logger import logger
from config.settings import settings

class RLMetricsCollector:
    def __init__(self):
        self.lb_client = LoadBalancerClient()
        self.prometheus_client = PrometheusClient()
        self.running = True

        # Setup signal handlers for graceful shutdown
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)

    def signal_handler(self, signum, frame):
        """Handle shutdown signals gracefully"""
        logger.info("Received shutdown signal", signal=signum)
        self.running = False
        sys.exit(0)

    def collect_and_log_metrics(self):
        """Main collection method called by scheduler"""
        logger.info("="*80)
        logger.info("Starting metrics collection cycle",
                    timestamp=datetime.now().isoformat())

        try:
            # Step 1: Get registered services from load balancer
            services = self.lb_client.get_registered_services()
            if not services:
                logger.warning("No services found from load balancer")
                return

            # Step 2: Collect Prometheus metrics for each service
            service_metrics = self.prometheus_client.get_service_metrics(services)

            # Step 3: Create system snapshot
            snapshot = SystemSnapshot(
                timestamp=datetime.now(),
                services=service_metrics,
                total_services=len(set(s.service_name for s in service_metrics)),
                total_instances=len(service_metrics)
            )

            # Step 4: Log structured metrics
            self.log_system_snapshot(snapshot)

        except Exception as e:
            logger.error("Error in metrics collection cycle", error=str(e))

    def log_system_snapshot(self, snapshot: SystemSnapshot):
        """Log the collected metrics in a structured format"""
        logger.info("System Snapshot Summary",
                    timestamp=snapshot.timestamp.isoformat(),
                    total_services=snapshot.total_services,
                    total_instances=snapshot.total_instances)

        # Group by service
        services_grouped = {}
        for metric in snapshot.services:
            if metric.service_name not in services_grouped:
                services_grouped[metric.service_name] = []
            services_grouped[metric.service_name].append(metric)

        # Log each service's instances
        for service_name, instances in services_grouped.items():
            logger.info(f"📊 SERVICE: {service_name}",
                        instance_count=len(instances))

            for instance in instances:
                logger.info(f"  └── INSTANCE: {instance.pod_name}",
                            cpu_usage=f"{instance.cpu_usage_percent:.2f}%" if instance.cpu_usage_percent else "N/A",
                            jvm_memory=f"{instance.jvm_memory_usage_percent:.2f}%" if instance.jvm_memory_usage_percent else "N/A",
                            uptime=f"{instance.uptime_seconds:.0f}s" if instance.uptime_seconds else "N/A",
                            request_rate=f"{instance.request_rate_per_second:.2f}/s" if instance.request_rate_per_second else "0/s",
                            avg_latency=f"{instance.avg_response_time_ms:.2f}ms" if instance.avg_response_time_ms else "N/A",
                            error_rate=f"{instance.error_rate_percent:.2f}%" if instance.error_rate_percent else "0%",
                            total_requests=instance.total_requests or 0)

        logger.info("="*80)

    def health_check(self):
        """Perform health checks on dependencies"""
        lb_healthy = self.lb_client.health_check()
        prometheus_healthy = self.prometheus_client.health_check()

        logger.info("Health Check Results",
                    load_balancer=lb_healthy,
                    prometheus=prometheus_healthy)

        return lb_healthy and prometheus_healthy

    def run(self):
        """Main run loop with scheduler"""
        logger.info("Starting RL Metrics Collector",
                    collection_interval=f"{settings.collection_interval_seconds}s",
                    prometheus_host=f"{settings.prometheus_host}:{settings.prometheus_port}",
                    loadbalancer_host=f"{settings.loadbalancer_host}:{settings.loadbalancer_port}")

        # Initial health check
        if not self.health_check():
            logger.error("Health check failed. Check Prometheus and Load Balancer connectivity.")
            return

        # Schedule the collection job
        schedule.every(settings.collection_interval_seconds).seconds.do(self.collect_and_log_metrics)

        # Run initial collection
        self.collect_and_log_metrics()

        # Main scheduler loop
        while self.running:
            schedule.run_pending()
            time.sleep(1)

if __name__ == "__main__":
    collector = RLMetricsCollector()
    collector.run()