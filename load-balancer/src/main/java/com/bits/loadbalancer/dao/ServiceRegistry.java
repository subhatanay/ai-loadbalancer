package com.bits.loadbalancer.dao;

import com.bits.loadbalancer.model.Service;
import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.services.ServiceDiscoveryClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

@Getter
@Component
public class ServiceRegistry {

    private final Map<String, Service> servicesMap = new ConcurrentHashMap<>();
    private final ServiceDiscoveryClient serviceDiscoveryClient;

    @Autowired
    public ServiceRegistry(ServiceDiscoveryClient serviceDiscoveryClient) {
        this.serviceDiscoveryClient = serviceDiscoveryClient;
    }

    public void registerService(String serviceName) {
        servicesMap.putIfAbsent(serviceName, new Service(serviceName));
    }

    public void addServiceInstance(String serviceName, String url, String healthUrl, String instanceId) {
        Service service = servicesMap.get(serviceName);
        if (service != null) {
            service.addInstance(url, healthUrl, instanceId);
        }
    }

    public void removeServiceInstance(String serviceName, String url) {
        Service service = servicesMap.get(serviceName);
        if (service != null) {
            service.removeInstance(url);
        }
    }

    public void updateInstanceHealth(String serviceName, String url, boolean healthy) {
        Service service = servicesMap.get(serviceName);
        if (service != null) {
            service.updateInstanceHealth(url, healthy);
        }
    }

    public Service getService(String serviceName) {
        return servicesMap.get(serviceName);
    }

    public List<Service> getAllServices() {
        return new ArrayList<>(servicesMap.values());
    }

    public void removeService(String serviceName) {
        servicesMap.remove(serviceName);
    }

    public boolean serviceExists(String serviceName) {
        return servicesMap.containsKey(serviceName);
    }


    @Scheduled(fixedRate = 15000) // Sync every 15 seconds
    public void refreshServices() {
        Map<String, List<ServiceInfo>> discoveredServices =
                serviceDiscoveryClient.getAllServices();

        for (Map.Entry<String, List<ServiceInfo>> entry : discoveredServices.entrySet()) {
            String serviceName = entry.getKey();
            Service service = servicesMap.computeIfAbsent(serviceName,
                    k -> new Service(serviceName));

            // Add new instances
            for (ServiceInfo info : entry.getValue()) {
                if (service
                        .getInstances()
                        .stream()
                        .filter(
                                srv -> srv.getUrl()
                                        .equalsIgnoreCase(info.getUrl())).toList()
                        .isEmpty()) {
                    service.addInstance(info.getUrl(), info.getHealthUrl(), info.getInstanceName());

                }
            }

            // Remove stale instances
            List<String> activeUrls = entry.getValue().stream()
                    .filter(ServiceInfo::isHealthy)
                    .map(ServiceInfo::getUrl)
                    .toList();

            service.getInstances().removeIf(instance ->
                    !activeUrls.contains(instance.getUrl())
            );
        }
    }
}