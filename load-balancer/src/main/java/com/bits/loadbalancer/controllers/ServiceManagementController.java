package com.bits.loadbalancer.controllers;

import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.loadbalancer.model.Service;
import com.bits.loadbalancer.services.HealthCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceManagementController {

    @Autowired
    private ServiceRegistry serviceRegistry;

    @Autowired
    private HealthCheckService healthCheckService;

    @PostMapping("/{serviceName}")
    public ResponseEntity<Map<String, String>> registerService(@PathVariable("serviceName") String serviceName) {
        serviceRegistry.registerService(serviceName);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Service registered successfully");
        response.put("serviceName", serviceName);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{serviceName}/instances")
    public ResponseEntity<Map<String, String>> addInstance(
            @PathVariable("serviceName") String serviceName,
            @RequestBody Map<String, String> instanceData) {

        String url = instanceData.get("url");
        String healthUrl = instanceData.get("healthUrl");

        if (url == null || healthUrl == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Both 'url' and 'healthUrl' are required");
            return ResponseEntity.badRequest().body(error);
        }

        if (!serviceRegistry.serviceExists(serviceName)) {
            serviceRegistry.registerService(serviceName);
        }

        serviceRegistry.addServiceInstance(serviceName, url, healthUrl, serviceName);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Instance added successfully");
        response.put("serviceName", serviceName);
        response.put("url", url);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{serviceName}/instances")
    public ResponseEntity<Map<String, String>> removeInstance(
            @PathVariable("serviceName") String serviceName,
            @RequestParam String url) {

        serviceRegistry.removeServiceInstance(serviceName, url);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Instance removed successfully");
        response.put("serviceName", serviceName);
        response.put("url", url);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Service>> getAllServices() {
        return ResponseEntity.ok(serviceRegistry.getAllServices());
    }

    @GetMapping("/{serviceName}")
    public ResponseEntity<Service> getService(@PathVariable("serviceName") String serviceName) {
        Service service = serviceRegistry.getService(serviceName);
        if (service == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(service);
    }

    @PostMapping("/{serviceName}/instances/{instanceUrl}/health-check")
    public ResponseEntity<Map<String, String>> forceHealthCheck(
            @PathVariable("serviceName") String serviceName,
            @PathVariable("instanceUrl") String instanceUrl) {

        // Decode URL parameter
        String decodedUrl = instanceUrl.replace("_", "/").replace("-", ":");

        healthCheckService.forceHealthCheck(serviceName, decodedUrl);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Health check triggered");
        response.put("serviceName", serviceName);
        response.put("instanceUrl", decodedUrl);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOverallStatus() {
        Map<String, Object> status = new HashMap<>();
        List<Service> services = serviceRegistry.getAllServices();

        int totalServices = services.size();
        int totalInstances = services.stream().mapToInt(Service::getTotalInstances).sum();
        int healthyInstances = services.stream().mapToInt(Service::getHealthyInstanceCount).sum();

        status.put("totalServices", totalServices);
        status.put("totalInstances", totalInstances);
        status.put("healthyInstances", healthyInstances);
        status.put("services", services);

        return ResponseEntity.ok(status);
    }
}