package com.freesky.sprintbootsky.infrastructure.agentsky;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;

/**
 * AgentSky 响应清洗器（仅允许 AgentSkyGatewayAdapter 使用，Application 不得直接注入）。
 * 负责保证：AgentSky 原始异常、traceback、密钥、请求头相关信息绝不返回前端，
 * 也不原样写入业务数据库；logs 与 error 落库前同样经过清洗。
 */
@Component
public class AgentSkyResponseSanitizer {

    /** 日志清洗上限：条数、单条长度、总长度。 */
    static final int MAX_LOG_ENTRIES = 200;
    static final int MAX_LOG_LINE_LENGTH = 500;
    static final int MAX_LOG_TOTAL_LENGTH = 10_000;
    static final int MAX_MODEL_LENGTH = 80;

    /** 命中的日志行整行丢弃：traceback/堆栈/密钥/请求头特征。 */
    private static final List<String> UNSAFE_MARKERS = List.of(
            "traceback", "stacktrace", "file \"", " at ", "exception in thread",
            "sk-", "api_key", "apikey", "secret", "credential", "password",
            "authorization", "bearer ", "x-agentsky-token");

    private final String defaultModel;

    public AgentSkyResponseSanitizer(AgentSkyProperties properties) {
        this.defaultModel = properties.defaultModel();
    }

    /** 过滤敏感行、截断超长行，并限制条数与总长度。 */
    public List<String> sanitizeLogs(List<String> rawLogs) {
        if (rawLogs == null || rawLogs.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        int totalLength = 0;
        for (String raw : rawLogs) {
            if (cleaned.size() >= MAX_LOG_ENTRIES) {
                break;
            }
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || isUnsafe(line)) {
                continue;
            }
            if (line.length() > MAX_LOG_LINE_LENGTH) {
                line = line.substring(0, MAX_LOG_LINE_LENGTH);
            }
            if (totalLength + line.length() > MAX_LOG_TOTAL_LENGTH) {
                break;
            }
            totalLength += line.length();
            cleaned.add(line);
        }
        return List.copyOf(cleaned);
    }

    /**
     * 业务错误码 → 安全中文文案。故意忽略 AgentSky 原始 error 文本，
     * 避免 traceback / 密钥随错误信息返回前端或写入数据库。
     */
    public String publicErrorMessage(String errorCode) {
        if (errorCode == null) {
            return "创作流程执行失败，请稍后重试";
        }
        return switch (errorCode) {
            case "REVIEW_NOT_APPROVED" -> "正文在最大审核轮次内未通过，请调整创作灵感后重试";
            case "MODEL_INIT_FAILED" -> "模型服务初始化失败，请检查配置后重试";
            case "WORKFLOW_INIT_FAILED" -> "创作工作流初始化失败，请稍后重试";
            default -> "创作流程执行失败，请稍后重试";
        };
    }

    /** 解析原始 token_usage；缺失/非法字段用 0 或默认模型名兜底，保证响应字段永远完整。 */
    public TokenUsage sanitizeTokenUsage(JsonNode raw) {
        if (raw == null || !raw.isObject()) {
            return TokenUsage.zero(defaultModel);
        }
        return new TokenUsage(
                nonNegativeLong(raw, "input_tokens"),
                nonNegativeLong(raw, "output_tokens"),
                nonNegativeLong(raw, "total_tokens"),
                nonNegativeLong(raw, "call_count"),
                safeCost(raw),
                safeModel(raw));
    }

    private boolean isUnsafe(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String marker : UNSAFE_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private long nonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            return 0;
        }
        long number = value.longValue();
        return number < 0 ? 0 : number;
    }

    private BigDecimal safeCost(JsonNode node) {
        JsonNode value = node.get("cost_yuan");
        if (value == null || !value.isNumber()) {
            return BigDecimal.ZERO;
        }
        double cost = value.doubleValue();
        if (!Double.isFinite(cost) || cost < 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(cost).setScale(4, RoundingMode.HALF_UP);
    }

    private String safeModel(JsonNode node) {
        JsonNode value = node.get("model");
        if (value == null || !value.isTextual()) {
            return defaultModel;
        }
        String model = value.textValue().trim();
        if (model.isBlank() || model.length() > MAX_MODEL_LENGTH) {
            return defaultModel;
        }
        return model;
    }
}
