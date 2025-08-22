package com.bits.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddressResponse {
    
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String phone;
}
