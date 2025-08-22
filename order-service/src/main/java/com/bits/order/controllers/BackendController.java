package com.bits.order.controllers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BackendController {
    private final Random random = new Random();

    @Value("${POD_NAME:unknown-pod}")
    private String podName;


    @GetMapping("/process")
    public ResponseEntity<Map<String, Object>> processRequest(
            @RequestHeader Map<String, String> headers) {
        
        // Simulate processing time variation
        int processingTime = 50 + random.nextInt(200);
        try {
            Thread.sleep(processingTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("service", "backend-service-1" + "-" + podName);
        response.put("timestamp", LocalDateTime.now());
        response.put("processingTime", processingTime + "ms");
        response.put("instance", "instance-1");
        response.put("status", "success");
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "backend-service-1");
        return ResponseEntity.ok(health);
    }
}
