package com.bits.loadbalancer.services;

import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.loadbalancer.model.Service;
import org.springframework.beans.factory.annotation.Autowired;


import java.util.List;

@org.springframework.stereotype.Service
public class RoundRobinBasedLoadbalancer implements Loadbalancer {
    @Autowired
    private ServiceRegistry serviceRegistry;
    
    public ServiceInfo geNextServiceInstance(String serviceName) {
        Service service = serviceRegistry.getService(serviceName);
        if (service == null) { return null; }
        List<ServiceInfo> healthyInstances = service.getHealthyInstances();

        if (healthyInstances.isEmpty()) {
            return null;
        }

        int index = service.getInstanceIndex().getAndIncrement() % healthyInstances.size();
        return healthyInstances.get(index);
    }

}
