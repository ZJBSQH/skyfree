package com.freesky.sprintbootsky.domain.novel;

import java.math.BigDecimal;

/**
 * Token 用量（NovelProject 聚合内部值对象）。
 * 未知值统一使用 0 或默认模型名，保证响应中 token_usage 永远字段完整。
 */
public record TokenUsage(
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long callCount,
        BigDecimal costYuan,
        String model) {

    public static final String DEFAULT_MODEL = "deepseek-chat";

    public TokenUsage {
        costYuan = costYuan == null ? BigDecimal.ZERO : costYuan;
        model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    }

    /** 全零用量（AgentSky 失败/未返回时的安全默认值）。 */
    public static TokenUsage zero() {
        return zero(DEFAULT_MODEL);
    }

    public static TokenUsage zero(String model) {
        return new TokenUsage(0, 0, 0, 0, BigDecimal.ZERO, model);
    }
}
