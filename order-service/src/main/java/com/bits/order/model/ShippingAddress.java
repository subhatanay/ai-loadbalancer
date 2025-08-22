package com.bits.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingAddress {
    
    @Column(name = "shipping_street")
    private String street;
    
    @Column(name = "shipping_city")
    private String city;
    
    @Column(name = "shipping_state")
    private String state;
    
    @Column(name = "shipping_zip_code")
    private String zipCode;
    
    @Column(name = "shipping_country")
    private String country;
    
    @Column(name = "shipping_phone")
    private String phone;
}
