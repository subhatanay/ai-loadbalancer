package com.bits.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddressRequest {
    
    @NotBlank(message = "Street address cannot be blank")
    private String street;
    
    @NotBlank(message = "City cannot be blank")
    private String city;
    
    @NotBlank(message = "State cannot be blank")
    private String state;
    
    @NotBlank(message = "ZIP code cannot be blank")
    @Pattern(regexp = "\\d{5}(-\\d{4})?", message = "Invalid ZIP code format")
    private String zipCode;
    
    @NotBlank(message = "Country cannot be blank")
    private String country;
    
    @Pattern(regexp = "\\+?[1-9]\\d{1,14}", message = "Invalid phone number format")
    private String phone;
}
