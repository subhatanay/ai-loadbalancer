package com.bits.loadbalancer.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.bits.commomutil.models.ServiceInfo;

import lombok.Getter;
import lombok.Setter;

public class Service {
    // Getters and Setters
    @Getter
    private String name;
    @Setter
    @Getter
    private List<ServiceInfo> instances;
    private final AtomicInteger currentIndex;

    public Service(String name) {
        this.name = name;
        this.instances = new CopyOnWriteArrayList<>();
        this.currentIndex = new AtomicInteger(0);
    }

    public void addInstance(String url, String healthUrl, String podName) {
        if (instances.stream().noneMatch(i -> i.getUrl().equals(url))) {
            instances.add(new ServiceInfo(this.name, url, healthUrl, podName));
        }
    }

    public void removeInstance(String url) {
        instances.removeIf(instance -> instance.getUrl().equals(url));
    }

    public void updateInstanceHealth(String url, boolean healthy) {
        instances.stream()
                .filter(instance -> instance.getUrl().equals(url))
                .findFirst()
                .ifPresent(instance -> instance.setHealthy(healthy));
    }

    public List<ServiceInfo> getHealthyInstances() {
        return instances.stream().filter(ServiceInfo::isHealthy).toList();
    }

    public int getTotalInstances() { return instances.size(); }
    public int getHealthyInstanceCount() { return getHealthyInstances().size(); }
    public AtomicInteger getInstanceIndex() { return currentIndex; }
}