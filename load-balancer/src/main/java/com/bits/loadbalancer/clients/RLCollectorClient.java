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
        webClient.post()
                .uri(props.getEndpointUrl() + "/experience")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(exp)
                .retrieve()
                .toBodilessEntity()
                .doOnError(e -> log.error("Failed to send RL experience", e))
                .subscribe();
    }
}
