package com.bits.userservice.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic userCreatedTopic() {
        return new NewTopic("user-created", 1, (short) 1);
    }
}
