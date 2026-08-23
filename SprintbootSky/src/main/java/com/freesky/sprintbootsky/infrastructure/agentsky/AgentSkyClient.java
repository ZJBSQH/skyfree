package com.freesky.sprintbootsky.infrastructure.agentsky;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * AgentSky 原始 HTTP 客户端（Infrastructure 内部实现，只负责通信）。
 * 统一以原始 JSON 字符串返回响应体，由 AgentSkyGatewayAdapter 防腐层用 Jackson 解析；
 * Application 不得直接依赖本类。
 */
@Component
public class AgentSkyClient {

    private final RestClient restClient;
    private final String apiToken;

    public AgentSkyClient(RestClient agentSkyRestClient, AgentSkyProperties properties) {
        this.restClient = agentSkyRestClient;
        this.apiToken = properties.apiToken();
    }

    public String health() {
        return restClient.get()
                .uri("/api/health")
                .retrieve()
                .body(String.class);
    }

    public String create(String idea) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException("AGENTSKY_API_TOKEN is not configured");
        }
        return restClient.post()
                .uri("/api/create")
                .header("X-AgentSky-Token", apiToken)
                .body(Map.of("idea", idea))
                .retrieve()
                .body(String.class);
    }
}
