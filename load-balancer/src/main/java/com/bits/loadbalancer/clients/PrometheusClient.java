package com.bits.loadbalancer.clients;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PrometheusClient {
    private static final Logger log = LoggerFactory.getLogger(PrometheusClient.class);

    private final String prometheusBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PrometheusClient(@Value("${prometheus.base-url}") String prometheusBaseUrl) {
        this.prometheusBaseUrl = prometheusBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
    }

    public Optional<Double> queryInstantValue(String promQl) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(prometheusBaseUrl + "/api/v1/query")
                    .queryParam("query", promQl)
                    .toUriString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = mapper.readTree(response.body());
            if (!"success".equals(root.path("status").asText())) {
                log.warn("Prometheus query failed: {}", response.body());
                return Optional.empty();
            }

            JsonNode result = root.path("data").path("result");
            if (result.isArray() && result.size() > 0) {
                double value = result.get(0).path("value").get(1).asDouble();
                return Optional.of(value);
            }
        } catch (Exception e) {
            log.error("Prometheus query failed for query: {}", promQl, e);
        }
        return Optional.empty();
    }
}
