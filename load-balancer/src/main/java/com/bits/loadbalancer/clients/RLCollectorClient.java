package com.bits.loadbalancer.clients;

import com.bits.loadbalancer.configuration.CollectorProperties;
import com.bits.loadbalancer.dto.RLExperience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class RLCollectorClient {
    private final WebClient webClient;
    private final CollectorProperties props;
    private static final Logger log = LoggerFactory.getLogger(RLCollectorClient.class);

    @Autowired
    public RLCollectorClient(CollectorProperties props) {
        this.props = props;
        this.webClient = WebClient.builder().baseUrl(props.getEndpointUrl()).build();
    }

    public void sendExperience(RLExperience exp) {
        if (!props.isEnabled()) {
            log.debug("RL collector disabled, skipping experience send");
            return;
        }
        
        webClient.post()
                .uri("/experience")  // Fixed: Don't double-append base URL
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(exp)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(result -> log.debug("Successfully sent RL experience to collector"))
                .doOnError(e -> log.error("Failed to send RL experience to {}: {}", 
                    props.getEndpointUrl(), e.getMessage(), e))
                .subscribe();
    }
}
