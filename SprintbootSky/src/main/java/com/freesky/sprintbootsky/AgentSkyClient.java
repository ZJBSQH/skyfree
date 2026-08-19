package com.freesky.sprintbootsky;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AgentSkyClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;
    private final String apiToken;

    public AgentSkyClient(
            RestClient.Builder builder,
            @Value("${agentsky.base-url:http://127.0.0.1:8765}") String baseUrl,
            @Value("${agentsky.api-token:}") String apiToken) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiToken = apiToken;
    }

    public Map<String, Object> health() {
        return restClient.get()
                .uri("/api/health")
                .retrieve()
                .body(MAP_RESPONSE);
    }

    public Map<String, Object> create(String idea) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException("AGENTSKY_API_TOKEN is not configured");
        }

        return restClient.post()
                .uri("/api/create")
                .header("X-AgentSky-Token", apiToken)
                .body(Map.of("idea", idea))
                .retrieve()
                .body(MAP_RESPONSE);
    }
}
