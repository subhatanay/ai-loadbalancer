package com.bits.userservice.event;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCreatedEvent implements Serializable {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private LocalDateTime timestamp;
}
