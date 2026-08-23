package com.freesky.sprintbootsky.infrastructure.agentsky;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AgentSky 连接配置（类型安全绑定 agentsky.*）。
 * 连接超时与读取超时分开配置：读取超时允许长任务但必须有限，不能无限等待。
 */
@ConfigurationProperties(prefix = "agentsky")
public record AgentSkyProperties(
        String baseUrl,
        String apiToken,
        Duration connectTimeout,
        Duration readTimeout,
        String defaultModel) {

    public AgentSkyProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "http://127.0.0.1:8765" : baseUrl;
        apiToken = apiToken == null ? "" : apiToken;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(10) : readTimeout;
        defaultModel = (defaultModel == null || defaultModel.isBlank()) ? "deepseek-chat" : defaultModel;
    }
}
