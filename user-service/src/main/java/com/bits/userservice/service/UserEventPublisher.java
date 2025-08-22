package com.bits.userservice.service;

import com.bits.userservice.event.UserCreatedEvent;
import com.bits.userservice.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserCreatedEvent(User user) {
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .timestamp(user.getCreatedAt())
                .build();

        kafkaTemplate.send("user-created", user.getId().toString(), event);
        log.info("Published user created event for user ID: {}", user.getId());
    }
}