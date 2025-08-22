package com.bits.notification.dto;

import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationType;
import com.bits.notification.enums.TemplateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequest {
    
    @NotBlank(message = "Template name cannot be blank")
    @Size(max = 255, message = "Template name cannot exceed 255 characters")
    private String name;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @NotNull(message = "Notification type cannot be null")
    private NotificationType notificationType;
    
    @NotNull(message = "Channel cannot be null")
    private NotificationChannel channel;
    
    @NotNull(message = "Template type cannot be null")
    private TemplateType templateType;
    
    @Size(max = 255, message = "Subject template cannot exceed 255 characters")
    private String subjectTemplate;
    
    @NotBlank(message = "Body template cannot be blank")
    @Size(max = 10000, message = "Body template cannot exceed 10000 characters")
    private String bodyTemplate;
    
    private Map<String, String> variables;
    
    @Builder.Default
    private Boolean active = true;
    
    // Manual getters and setters for compatibility
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public NotificationType getNotificationType() { return notificationType; }
    public void setNotificationType(NotificationType notificationType) { this.notificationType = notificationType; }
    
    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }
    
    public TemplateType getTemplateType() { return templateType; }
    public void setTemplateType(TemplateType templateType) { this.templateType = templateType; }
    
    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String subjectTemplate) { this.subjectTemplate = subjectTemplate; }
    
    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }
    
    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }
    
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    
    // Alias methods for service compatibility
    public String getSubject() { return subjectTemplate; }
    public String getBody() { return bodyTemplate; }
    public TemplateType getType() { return templateType; }
}
