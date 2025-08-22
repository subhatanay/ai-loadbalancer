package com.bits.loadbalancer.services;

import java.util.Comparator;
import java.util.List;

import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.model.ServiceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

//@Service
public class ConnectionAwareLoadBalancer implements Loadbalancer {
//    @Autowired
    private ServiceRegistry serviceRegistry;

//    @Autowired
    private PrometheusMetricsService prometheusMetricsService;

    @Override
    public ServiceInfo geNextServiceInstance(String serviceName) {
        com.bits.loadbalancer.model.Service service = serviceRegistry.getService(serviceName);
        if (service == null) return null;

        List<ServiceInfo> healthyInstances = service.getInstances().stream()
                .filter(ServiceInfo::isHealthy)
                .toList();

        if (healthyInstances.isEmpty()) {
            return null;
        }

        // Get metrics for all healthy instances and select least loaded
        return healthyInstances.stream()
                .filter(instance -> this.isInstanceAvailable(instance, serviceName))
                .min(Comparator.comparing(instance -> this.getInstanceConnectionLoad(instance, serviceName)))
                .orElse(healthyInstances.get(0));
    }

    private boolean isInstanceAvailable(ServiceInfo instance, String serviceName) {
        return true;
//        try {
//            ServiceMetrics metrics = prometheusMetricsService.getServiceMetrics(
//                    instance.getUrl(), serviceName);
//            return metrics.isAvailableForConnections();
//        } catch (Exception e) {
//            return true; // If we can't get metrics, assume available
//        }
    }

    private double getInstanceConnectionLoad(ServiceInfo instance, String serviceNam) {
//        try {
//            ServiceMetrics metrics = prometheusMetricsService.getServiceMetrics(
//                    instance.getUrl(), serviceNam);
//            return metrics.getConnectionLoadScore();
//        } catch (Exception e) {
//            return 50.0; // Default medium load if metrics unavailable
//        }
        return 0.0;
    }
}
