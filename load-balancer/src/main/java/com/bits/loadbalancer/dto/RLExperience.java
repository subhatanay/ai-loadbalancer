package com.bits.loadbalancer.dto;

import java.util.Map;

import com.bits.loadbalancer.model.StateSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RLExperience {
    private StateSnapshot state;
    private String action;          // instance ID chosen
    private double reward;
    @JsonProperty("next_state")
    private StateSnapshot nextState;
    private Map<String, Object> metadata;
}
