package com.freesky.sprintbootsky.infrastructure.agentsky;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.freesky.sprintbootsky.application.port.out.AgentSkyCreateResult;
import com.freesky.sprintbootsky.application.port.out.AgentSkyGateway;
import com.freesky.sprintbootsky.application.port.out.AgentSkyHealth;
import com.freesky.sprintbootsky.application.port.out.AgentSkyNovelResult;
import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.CharacterRelationship;
import com.freesky.sprintbootsky.domain.novel.PlotOutlineItem;
import com.freesky.sprintbootsky.domain.novel.ReviewIssue;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;
import com.freesky.sprintbootsky.domain.novel.WorldSetting;
import com.freesky.sprintbootsky.infrastructure.agentsky.dto.AgentSkyCreateResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AgentSkyGateway 的防腐层实现。
 * 负责把 AgentSky 原始响应（原始 JSON / JsonNode / HTTP 状态 / 底层异常）全部挡在内部，
 * 转换为类型明确、已清洗的 Application Port 模型；429、连接失败、超时、5xx 都收敛为
 * 稳定的失败结果，RestClient 异常绝不越过本边界。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSkyGatewayAdapter implements AgentSkyGateway {

    private final AgentSkyClient agentSkyClient;
    private final AgentSkyResponseSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    @Override
    public AgentSkyHealth checkHealth() {
        try {
            JsonNode raw = parse(agentSkyClient.health());
            String status = "ok".equalsIgnoreCase(raw.path("status").asText("")) ? "ok" : "unavailable";
            return new AgentSkyHealth(status, "agentsky");
        } catch (RestClientException | JacksonException exception) {
            log.warn("AgentSky 健康检查失败", exception);
            return new AgentSkyHealth("unavailable", "agentsky");
        }
    }

    @Override
    public AgentSkyCreateResult create(String idea) {
        String rawBody;
        try {
            rawBody = agentSkyClient.create(idea);
        } catch (RestClientResponseException exception) {
            log.warn("AgentSky 返回 HTTP 错误: {}", exception.getStatusCode().value());
            return AgentSkyCreateResult.failure(
                    httpErrorMessage(exception), httpErrorCode(exception), zeroUsage());
        } catch (ResourceAccessException exception) {
            log.warn("AgentSky 连接失败或读取超时", exception);
            return AgentSkyCreateResult.failure(
                    "创作服务连接失败，请稍后重试", "AGENTSKY_UNAVAILABLE", zeroUsage());
        } catch (RuntimeException exception) {
            log.warn("AgentSky 调用失败（配置或系统错误）", exception);
            return AgentSkyCreateResult.failure(
                    "AgentSky 服务未正确配置，请联系管理员", "AGENTSKY_CONFIG_ERROR", zeroUsage());
        }

        AgentSkyCreateResponse raw;
        try {
            raw = AgentSkyCreateResponse.from(parse(rawBody));
        } catch (JacksonException exception) {
            log.warn("AgentSky 响应体无法解析", exception);
            return AgentSkyCreateResult.failure(
                    "创作流程执行失败，请稍后重试", "AGENTSKY_INVALID_RESPONSE", zeroUsage());
        }

        if (Boolean.TRUE.equals(raw.success())) {
            return AgentSkyCreateResult.success(
                    sanitizer.sanitizeLogs(raw.logs()),
                    parseResult(raw.result()),
                    sanitizer.sanitizeTokenUsage(raw.tokenUsage()));
        }
        // 业务失败：只用错误码映射安全文案，原始 error 文本直接丢弃
        return AgentSkyCreateResult.failure(
                sanitizer.sanitizeLogs(raw.logs()),
                parseResult(raw.result()),
                sanitizer.publicErrorMessage(raw.errorCode()),
                raw.errorCode() == null ? "" : raw.errorCode(),
                sanitizer.sanitizeTokenUsage(raw.tokenUsage()));
    }

    /** 空响应体按空对象处理（from(null) 会收敛为安全失败），解析异常由调用方捕获。 */
    private JsonNode parse(String rawBody) throws JacksonException {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        return objectMapper.readTree(rawBody);
    }

    private TokenUsage zeroUsage() {
        return sanitizer.sanitizeTokenUsage(null);
    }

    private String httpErrorMessage(RestClientResponseException exception) {
        return switch (exception.getStatusCode().value()) {
            case 429 -> "创作服务繁忙，请稍后重试";
            case 401, 403 -> "AgentSky 服务鉴权失败，请联系管理员";
            case 503 -> "创作服务暂时不可用，请稍后重试";
            default -> "创作流程执行失败，请稍后重试";
        };
    }

    private String httpErrorCode(RestClientResponseException exception) {
        return "AGENTSKY_HTTP_" + exception.getStatusCode().value();
    }

    private AgentSkyNovelResult parseResult(JsonNode result) {
        if (result == null || !result.isObject()) {
            return new AgentSkyNovelResult(List.of(), List.of(), List.of(), List.of(), 0);
        }
        return new AgentSkyNovelResult(
                textArray(result.get("completed_chapters")),
                parseCharacters(result.get("characters")),
                parseWorldSettings(result.get("world_settings")),
                parsePlotOutline(result.get("plot_outline")),
                intField(result.get("review_round")),
                text(result.get("current_draft")),
                parseReviewIssues(result.get("review_issues")));
    }

    private List<CharacterProfile> parseCharacters(JsonNode characters) {
        List<CharacterProfile> profiles = new ArrayList<>();
        if (characters == null || !characters.isArray()) {
            return profiles;
        }
        for (JsonNode node : characters) {
            if (!node.isObject()) {
                continue;
            }
            List<CharacterRelationship> relationships = new ArrayList<>();
            JsonNode relationshipsNode = node.get("relationships");
            if (relationshipsNode != null && relationshipsNode.isArray()) {
                for (JsonNode relationship : relationshipsNode) {
                    if (!relationship.isObject()) {
                        continue;
                    }
                    relationships.add(new CharacterRelationship(
                            text(relationship.get("name")),
                            text(relationship.get("relation")),
                            text(relationship.get("dynamic"))));
                }
            }
            profiles.add(new CharacterProfile(
                    text(node.get("name")),
                    text(node.get("role_type")),
                    text(node.get("appearance")),
                    text(node.get("personality")),
                    text(node.get("background")),
                    text(node.get("ability")),
                    text(node.get("motivation")),
                    relationships));
        }
        return profiles;
    }

    private List<WorldSetting> parseWorldSettings(JsonNode settings) {
        List<WorldSetting> result = new ArrayList<>();
        if (settings == null || !settings.isArray()) {
            return result;
        }
        for (JsonNode node : settings) {
            if (!node.isObject()) {
                continue;
            }
            JsonNode version = node.get("version");
            result.add(new WorldSetting(
                    text(node.get("category")),
                    text(node.get("key")),
                    text(node.get("content")),
                    version != null && version.isNumber() ? version.intValue() : null));
        }
        return result;
    }

    private List<PlotOutlineItem> parsePlotOutline(JsonNode outline) {
        List<PlotOutlineItem> result = new ArrayList<>();
        if (outline == null || !outline.isArray()) {
            return result;
        }
        for (JsonNode node : outline) {
            if (!node.isObject()) {
                continue;
            }
            result.add(new PlotOutlineItem(
                    text(node.get("id")),
                    text(node.get("title")),
                    text(node.get("summary")),
                    textArray(node.get("foreshadowing")),
                    textArray(node.get("characters_involved")),
                    textArray(node.get("settings_revealed")),
                    text(node.get("type")),
                    text(node.get("tension_level"))));
        }
        return result;
    }

    private List<ReviewIssue> parseReviewIssues(JsonNode issues) {
        List<ReviewIssue> result = new ArrayList<>();
        if (issues == null || !issues.isArray()) {
            return result;
        }
        for (JsonNode node : issues) {
            if (!node.isObject()) {
                continue;
            }
            result.add(new ReviewIssue(
                    text(node.get("severity")),
                    text(node.get("category")),
                    text(node.get("description")),
                    text(node.get("target_agent")),
                    text(node.get("suggestion"))));
        }
        return result;
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : "";
    }

    private int intField(JsonNode node) {
        return node != null && node.isNumber() ? node.intValue() : 0;
    }

    private List<String> textArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode element : node) {
            if (element.isTextual()) {
                values.add(element.textValue());
            }
        }
        return values;
    }
}
