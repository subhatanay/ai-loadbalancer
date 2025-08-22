package com.bits.cartservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeCartRequest {

    @NotBlank(message = "Anonymous cart ID cannot be blank")
    private String anonymousCartId;

    private String targetUserId;
}
