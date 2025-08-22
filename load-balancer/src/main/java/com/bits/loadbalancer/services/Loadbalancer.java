package com.bits.loadbalancer.services;

import com.bits.commomutil.models.ServiceInfo;

public interface Loadbalancer {

    ServiceInfo geNextServiceInstance(String serviceName);
    
}
