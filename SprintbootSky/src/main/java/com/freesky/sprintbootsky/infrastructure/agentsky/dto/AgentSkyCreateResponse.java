package com.freesky.sprintbootsky.infrastructure.agentsky.dto;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * AgentSky /api/create 原始响应 DTO。仅限 infrastructure.agentsky 内部使用；
 * 由 {@link #from(JsonNode)} 从防腐层解析出的响应树手工构造，
 * result / token_usage 等动态结构保留 JsonNode，由防腐层进一步解析为类型化模型。
 */
public record AgentSkyCreateResponse(
        Boolean success,
        List<String> logs,
        JsonNode result,
        String error,
        String errorCode,
        JsonNode tokenUsage) {

    public static AgentSkyCreateResponse from(JsonNode root) {
        if (root == null || !root.isObject()) {
            return new AgentSkyCreateResponse(false, List.of(), null, "", "", null);
        }
        Boolean success = root.path("success").isBoolean() ? root.path("success").booleanValue() : false;
        List<String> logs = new ArrayList<>();
        JsonNode logsNode = root.get("logs");
        if (logsNode != null && logsNode.isArray()) {
            for (JsonNode line : logsNode) {
                if (line.isTextual()) {
                    logs.add(line.textValue());
                }
            }
        }
        JsonNode tokenUsage = root.get("token_usage");
        return new AgentSkyCreateResponse(
                success,
                logs,
                root.get("result"),
                root.path("error").asText(""),
                root.path("error_code").asText(""),
                tokenUsage != null && tokenUsage.isObject() ? tokenUsage : null);
    }
}
