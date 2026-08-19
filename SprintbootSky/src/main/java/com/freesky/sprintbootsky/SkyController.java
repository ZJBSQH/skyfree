package com.freesky.sprintbootsky;

import java.util.Map;
import java.util.LinkedHashMap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api")
public class SkyController {

    private final AgentSkyClient agentSkyClient;

    public SkyController(AgentSkyClient agentSkyClient) {
        this.agentSkyClient = agentSkyClient;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "SprintbootSky");
    }

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Freesky backend connected",
                "service", "SprintbootSky",
                "agentService", "AgentSky");
    }

    @GetMapping("/agent/health")
    public ResponseEntity<Map<String, Object>> agentHealth() {
        try {
            return ResponseEntity.ok(agentSkyClient.health());
        } catch (RestClientException exception) {
            return agentUnavailable(exception);
        }
    }

    @PostMapping("/novels")
    public ResponseEntity<Map<String, Object>> createNovel(@Valid @RequestBody CreateNovelRequest request) {
        try {
            Map<String, Object> response = agentSkyClient.create(request.idea());
            if (Boolean.FALSE.equals(response.get("success"))) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("logs", java.util.List.of());
                body.put("result", Map.of());
                body.put("error", publicFailureMessage(response.get("error_code")));
                body.put("token_usage", sanitizeTokenUsage(response.get("token_usage")));
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
            }
            return ResponseEntity.ok(response);
        } catch (RestClientException | IllegalStateException exception) {
            return agentUnavailable(exception);
        }
    }

    private ResponseEntity<Map<String, Object>> agentUnavailable(Exception exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "unavailable",
                "service", "agentsky",
                "error", "AgentSky is unavailable"));
    }

    private String publicFailureMessage(Object errorCode) {
        return switch (String.valueOf(errorCode)) {
            case "REVIEW_NOT_APPROVED" -> "正文在最大审核轮次内未通过，请调整创作灵感后重试";
            case "MODEL_INIT_FAILED" -> "模型服务初始化失败，请检查配置后重试";
            case "WORKFLOW_INIT_FAILED" -> "创作工作流初始化失败，请稍后重试";
            default -> "创作流程执行失败，请稍后重试";
        };
    }

    private Map<String, Object> sanitizeTokenUsage(Object rawUsage) {
        if (!(rawUsage instanceof Map<?, ?> usage)) {
            return Map.of();
        }

        Map<String, Object> safeUsage = new LinkedHashMap<>();
        for (String key : java.util.List.of(
                "input_tokens", "output_tokens", "total_tokens", "call_count", "cost_yuan")) {
            Object value = usage.get(key);
            if (value instanceof Number) {
                safeUsage.put(key, value);
            }
        }
        Object model = usage.get("model");
        if (model instanceof String modelName && modelName.length() <= 80) {
            safeUsage.put("model", modelName);
        }
        return safeUsage;
    }

    public record CreateNovelRequest(
            @NotBlank(message = "idea must not be blank")
            @Size(max = 2000, message = "idea must not exceed 2000 characters")
            String idea) {
    }
}
