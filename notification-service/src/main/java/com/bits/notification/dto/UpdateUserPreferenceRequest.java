package com.bits.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPreferenceRequest {
    
    @NotNull(message = "Enabled status cannot be null")
    private Boolean enabled;
    
    private LocalTime quietHoursStart;
    
    private LocalTime quietHoursEnd;
    
    @Pattern(regexp = "^[A-Za-z_/]+$", message = "Invalid timezone format")
    private String timezone;
    
    @Min(value = 0, message = "Frequency limit cannot be negative")
    @Max(value = 100, message = "Frequency limit cannot exceed 100")
    private Integer frequencyLimitPerDay;
    
    @Min(value = 0, message = "Frequency limit per hour cannot be negative")
    @Max(value = 50, message = "Frequency limit per hour cannot exceed 50")
    private Integer frequencyLimitPerHour;
    
    // Manual getters and setters for compatibility
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    
    public Integer getFrequencyLimitPerDay() { return frequencyLimitPerDay; }
    public void setFrequencyLimitPerDay(Integer frequencyLimitPerDay) { this.frequencyLimitPerDay = frequencyLimitPerDay; }
    
    public Integer getFrequencyLimitPerHour() { return frequencyLimitPerHour; }
    public void setFrequencyLimitPerHour(Integer frequencyLimitPerHour) { this.frequencyLimitPerHour = frequencyLimitPerHour; }
}
