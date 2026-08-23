package com.freesky.sprintbootsky.application.port.out;

import java.util.List;

import com.freesky.sprintbootsky.domain.novel.TokenUsage;

/**
 * AgentSky 创作调用结果（类型化端口模型）。
 * 网关对所有可预期失败（HTTP 429/5xx、连接失败、超时、业务失败、配置缺失）
 * 都以本结果的 failure 形式返回，绝不向 Application 抛底层异常；
 * logs 已清洗，tokenUsage 永远字段完整。
 */
public record AgentSkyCreateResult(
        boolean success,
        List<String> logs,
        AgentSkyNovelResult result,
        String error,
        String errorCode,
        TokenUsage tokenUsage) {

    public AgentSkyCreateResult {
        logs = logs == null ? List.of() : List.copyOf(logs);
        error = error == null ? "" : error;
        errorCode = errorCode == null ? "" : errorCode;
        tokenUsage = tokenUsage == null ? TokenUsage.zero() : tokenUsage;
    }

    public static AgentSkyCreateResult success(List<String> logs, AgentSkyNovelResult result, TokenUsage tokenUsage) {
        return new AgentSkyCreateResult(true, logs, result, "", "", tokenUsage);
    }

    public static AgentSkyCreateResult failure(String error, String errorCode, TokenUsage tokenUsage) {
        return new AgentSkyCreateResult(false, List.of(), null, error, errorCode, tokenUsage);
    }

    public static AgentSkyCreateResult failure(
            List<String> logs,
            AgentSkyNovelResult result,
            String error,
            String errorCode,
            TokenUsage tokenUsage) {
        return new AgentSkyCreateResult(false, logs, result, error, errorCode, tokenUsage);
    }
}
