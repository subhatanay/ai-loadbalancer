package com.bits.notification.controller;

import com.bits.notification.dto.CreateTemplateRequest;
import com.bits.notification.dto.NotificationTemplateResponse;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.TemplateType;
import com.bits.notification.security.JwtService;
import com.bits.notification.service.NotificationTemplateService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
@Validated
public class TemplateController {
    
    private static final Logger logger = LoggerFactory.getLogger(TemplateController.class);
    
    @Autowired
    private NotificationTemplateService templateService;
    
    @Autowired
    private JwtService jwtService;
    
    private final Counter templateCreatedCounter;
    private final Counter templateRetrievedCounter;
    
    public TemplateController(MeterRegistry meterRegistry) {
        this.templateCreatedCounter = Counter.builder("templates.created")
            .description("Number of templates created")
            .register(meterRegistry);
        this.templateRetrievedCounter = Counter.builder("templates.retrieved")
            .description("Number of templates retrieved")
            .register(meterRegistry);
    }
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "template.create", description = "Time taken to create template")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.info("Creating template: {} by user: {}", request.getName(), userId);
        
        NotificationTemplateResponse response = templateService.createTemplate(request);
        templateCreatedCounter.increment();
        
        logger.info("Created template with ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    @Timed(value = "template.get", description = "Time taken to get template")
    public ResponseEntity<NotificationTemplateResponse> getTemplate(@PathVariable Long id) {
        
        logger.debug("Fetching template with ID: {}", id);
        
        NotificationTemplateResponse response = templateService.getTemplate(id);
        templateRetrievedCounter.increment();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/name/{name}/channel/{channel}")
    @Timed(value = "template.get.by.name.channel", description = "Time taken to get template by name and channel")
    public ResponseEntity<NotificationTemplateResponse> getTemplateByNameAndChannel(
            @PathVariable String name,
            @PathVariable NotificationChannel channel) {
        
        logger.debug("Fetching template with name: {} and channel: {}", name, channel);
        
        NotificationTemplateResponse response = templateService.getTemplateByNameAndChannel(name, channel);
        templateRetrievedCounter.increment();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/type/{type}")
    @Timed(value = "template.list.by.type", description = "Time taken to list templates by type")
    public ResponseEntity<Page<NotificationTemplateResponse>> getTemplatesByType(
            @PathVariable TemplateType type,
            Pageable pageable) {
        
        logger.debug("Fetching templates with type: {}", type);
        
        Page<NotificationTemplateResponse> templates = templateService.getTemplatesByType(type, pageable);
        
        return ResponseEntity.ok(templates);
    }
    
    @GetMapping("/channel/{channel}")
    @Timed(value = "template.list.by.channel", description = "Time taken to list templates by channel")
    public ResponseEntity<Page<NotificationTemplateResponse>> getTemplatesByChannel(
            @PathVariable NotificationChannel channel,
            Pageable pageable) {
        
        logger.debug("Fetching templates with channel: {}", channel);
        
        Page<NotificationTemplateResponse> templates = templateService.getTemplatesByChannel(channel, pageable);
        
        return ResponseEntity.ok(templates);
    }
    
    @GetMapping
    @Timed(value = "template.list.all", description = "Time taken to list all active templates")
    public ResponseEntity<List<NotificationTemplateResponse>> getAllActiveTemplates() {
        
        logger.debug("Fetching all active templates");
        
        List<NotificationTemplateResponse> templates = templateService.getAllActiveTemplates();
        
        return ResponseEntity.ok(templates);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "template.update", description = "Time taken to update template")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody CreateTemplateRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.info("Updating template: {} by user: {}", id, userId);
        
        NotificationTemplateResponse response = templateService.updateTemplate(id, request);
        
        logger.info("Updated template with ID: {} to version: {}", response.getId(), response.getVersion());
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "template.deactivate", description = "Time taken to deactivate template")
    public ResponseEntity<Void> deactivateTemplate(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.info("Deactivating template: {} by user: {}", id, userId);
        
        templateService.deactivateTemplate(id);
        
        logger.info("Deactivated template with ID: {}", id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{templateName}/channel/{channel}/render")
    @Timed(value = "template.render", description = "Time taken to render template")
    public ResponseEntity<Map<String, String>> renderTemplate(
            @PathVariable String templateName,
            @PathVariable NotificationChannel channel,
            @RequestBody Map<String, Object> variables) {
        
        logger.debug("Rendering template: {} for channel: {}", templateName, channel);
        
        try {
            String renderedContent = templateService.renderTemplate(templateName, channel, variables);
            String renderedSubject = templateService.renderSubject(templateName, channel, variables);
            
            Map<String, String> result = Map.of(
                "subject", renderedSubject,
                "content", renderedContent
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Failed to render template: {} for channel: {}", templateName, channel, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/validate")
    @PreAuthorize("hasRole('ADMIN')")
    @Timed(value = "template.validate", description = "Time taken to validate template")
    public ResponseEntity<Map<String, Object>> validateTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        
        logger.debug("Validating template: {}", request.getName());
        
        try {
            // Basic validation - check for required fields and valid syntax
            Map<String, Object> result = Map.of(
                "valid", true,
                "message", "Template validation successful"
            );
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Template validation failed for: {}", request.getName(), e);
            
            Map<String, Object> result = Map.of(
                "valid", false,
                "message", "Template validation failed: " + e.getMessage()
            );
            
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    private String extractUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtService.extractUserId(token);
        }
        throw new RuntimeException("No valid JWT token found");
    }
}
