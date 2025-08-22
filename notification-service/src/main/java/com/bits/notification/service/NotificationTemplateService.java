package com.bits.notification.service;

import com.bits.notification.dto.CreateTemplateRequest;
import com.bits.notification.dto.NotificationTemplateResponse;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.TemplateType;
import com.bits.notification.exception.*;
import com.bits.notification.model.NotificationTemplate;
import com.bits.notification.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationTemplateService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationTemplateService.class);
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    @Autowired
    private NotificationTemplateRepository templateRepository;
    
    @Transactional
    public NotificationTemplateResponse createTemplate(CreateTemplateRequest request) {
        logger.info("Creating notification template: {}", request.getName());
        
        // Check if template already exists
        Optional<NotificationTemplate> existing = templateRepository.findByNameAndChannelAndActiveTrue(
            request.getName(), request.getChannel());
        
        if (existing.isPresent()) {
            throw new TemplateAlreadyExistsException(
                "Template already exists with name: " + request.getName() + " for channel: " + request.getChannel());
        }
        
        // Create new template
        NotificationTemplate template = new NotificationTemplate();
        template.setName(request.getName());
        template.setSubject(request.getSubject());
        template.setBody(request.getBody());
        template.setType(request.getType());
        template.setChannel(request.getChannel());
        template.setVariables(extractVariables(request.getSubject(), request.getBody()));
        template.setActive(true);
        template.setVersion(1);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        
        template = templateRepository.save(template);
        
        logger.info("Created notification template with ID: {}", template.getId());
        return mapToResponse(template);
    }
    
    @Cacheable(value = "templates", key = "#id")
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getTemplate(Long id) {
        logger.debug("Fetching template with ID: {}", id);
        
        NotificationTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new TemplateNotFoundException("Template not found with ID: " + id));
        
        return mapToResponse(template);
    }
    
    @Cacheable(value = "templates", key = "#name + '_' + #channel")
    @Transactional(readOnly = true)
    public NotificationTemplateResponse getTemplateByNameAndChannel(String name, NotificationChannel channel) {
        logger.debug("Fetching template with name: {} and channel: {}", name, channel);
        
        NotificationTemplate template = templateRepository.findByNameAndChannelAndActiveTrue(name, channel)
            .orElseThrow(() -> new TemplateNotFoundException(
                "Active template not found with name: " + name + " and channel: " + channel));
        
        return mapToResponse(template);
    }
    
    @Transactional(readOnly = true)
    public Page<NotificationTemplateResponse> getTemplatesByType(TemplateType type, Pageable pageable) {
        logger.debug("Fetching templates with type: {}", type);
        
        Page<NotificationTemplate> templates = templateRepository.findByTypeAndActiveTrueOrderByCreatedAtDesc(type, pageable);
        return templates.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public Page<NotificationTemplateResponse> getTemplatesByChannel(NotificationChannel channel, Pageable pageable) {
        logger.debug("Fetching templates with channel: {}", channel);
        
        Page<NotificationTemplate> templates = templateRepository.findByChannelAndActiveTrueOrderByCreatedAtDesc(channel, pageable);
        return templates.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> getAllActiveTemplates() {
        logger.debug("Fetching all active templates");
        
        List<NotificationTemplate> templates = templateRepository.findByActiveTrueOrderByCreatedAtDesc();
        return templates.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @CacheEvict(value = "templates", allEntries = true)
    @Transactional
    public NotificationTemplateResponse updateTemplate(Long id, CreateTemplateRequest request) {
        logger.info("Updating template with ID: {}", id);
        
        NotificationTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new TemplateNotFoundException("Template not found with ID: " + id));
        
        // Create new version
        template.setSubject(request.getSubject());
        template.setBody(request.getBody());
        template.setType(request.getType());
        template.setVariables(extractVariables(request.getSubject(), request.getBody()));
        template.setVersion(template.getVersion() + 1);
        template.setUpdatedAt(LocalDateTime.now());
        
        template = templateRepository.save(template);
        
        logger.info("Updated template with ID: {} to version: {}", id, template.getVersion());
        return mapToResponse(template);
    }
    
    @CacheEvict(value = "templates", key = "#id")
    @Transactional
    public void deactivateTemplate(Long id) {
        logger.info("Deactivating template with ID: {}", id);
        
        NotificationTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new TemplateNotFoundException("Template not found with ID: " + id));
        
        template.setActive(false);
        template.setUpdatedAt(LocalDateTime.now());
        
        templateRepository.save(template);
        
        logger.info("Deactivated template with ID: {}", id);
    }
    
    public String renderTemplate(String templateName, NotificationChannel channel, Map<String, Object> variables) {
        logger.debug("Rendering template: {} for channel: {}", templateName, channel);
        
        NotificationTemplate template = templateRepository.findByNameAndChannelAndActiveTrue(templateName, channel)
            .orElseThrow(() -> new TemplateNotFoundException(
                "Active template not found with name: " + templateName + " and channel: " + channel));
        
        if (!template.getActive()) {
            throw new TemplateInactiveException("Template is not active: " + templateName);
        }
        
        try {
            String renderedContent = template.getBody();
            
            // Replace variables in the template
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                renderedContent = renderedContent.replace(placeholder, value);
            }
            
            // Check for unresolved variables
            Matcher matcher = VARIABLE_PATTERN.matcher(renderedContent);
            if (matcher.find()) {
                throw new TemplateRenderingException("Unresolved variables found in template: " + templateName);
            }
            
            logger.debug("Successfully rendered template: {}", templateName);
            return renderedContent;
            
        } catch (Exception e) {
            logger.error("Failed to render template: {}", templateName, e);
            throw new TemplateRenderingException("Failed to render template: " + templateName, e);
        }
    }
    
    public String renderSubject(String templateName, NotificationChannel channel, Map<String, Object> variables) {
        logger.debug("Rendering subject for template: {} and channel: {}", templateName, channel);
        
        NotificationTemplate template = templateRepository.findByNameAndChannelAndActiveTrue(templateName, channel)
            .orElseThrow(() -> new TemplateNotFoundException(
                "Active template not found with name: " + templateName + " and channel: " + channel));
        
        if (!template.getActive()) {
            throw new TemplateInactiveException("Template is not active: " + templateName);
        }
        
        try {
            String renderedSubject = template.getSubject();
            
            // Replace variables in the subject
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                renderedSubject = renderedSubject.replace(placeholder, value);
            }
            
            logger.debug("Successfully rendered subject for template: {}", templateName);
            return renderedSubject;
            
        } catch (Exception e) {
            logger.error("Failed to render subject for template: {}", templateName, e);
            throw new TemplateRenderingException("Failed to render subject for template: " + templateName, e);
        }
    }
    
    private Map<String, String> extractVariables(String subject, String body) {
        logger.debug("Extracting variables from template");
        
        String combinedContent = (subject != null ? subject : "") + " " + (body != null ? body : "");
        Matcher matcher = VARIABLE_PATTERN.matcher(combinedContent);
        
        return matcher.results()
            .map(matchResult -> matchResult.group(1))
            .distinct()
            .collect(Collectors.toMap(
                variable -> variable,
                variable -> "Variable: " + variable
            ));
    }
    
    private NotificationTemplateResponse mapToResponse(NotificationTemplate template) {
        NotificationTemplateResponse response = new NotificationTemplateResponse();
        response.setId(template.getId());
        response.setName(template.getName());
        response.setSubject(template.getSubject());
        response.setBody(template.getBody());
        response.setType(template.getType());
        response.setChannel(template.getChannel());
        response.setVariables(template.getVariables());
        response.setActive(template.getActive());
        response.setVersion(template.getVersion());
        response.setCreatedAt(template.getCreatedAt());
        response.setUpdatedAt(template.getUpdatedAt());
        
        return response;
    }
}
