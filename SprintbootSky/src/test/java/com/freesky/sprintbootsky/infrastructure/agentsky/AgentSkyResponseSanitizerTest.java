package com.freesky.sprintbootsky.infrastructure.agentsky;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSkyResponseSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentSkyResponseSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new AgentSkyResponseSanitizer(new AgentSkyProperties(
                "http://127.0.0.1:8765", "token",
                Duration.ofSeconds(5), Duration.ofMinutes(10), "deepseek-chat"));
    }

    @Test
    void tracebackAndSecretLinesAreRemoved() {
        List<String> logs = List.of(
                "[WriterAgent] Drafted chapter",
                "[ERROR] Traceback (most recent call last):",
                "  File \"/app/graph/workflow.py\", line 12, in invoke",
                "[ERROR] api_key=sk-1234567890 leaked",
                "[SupervisorAgent] 分配任务完成");

        assertThat(sanitizer.sanitizeLogs(logs)).containsExactly(
                "[WriterAgent] Drafted chapter",
                "[SupervisorAgent] 分配任务完成");
    }

    @Test
    void logsAreCappedByCountLineAndTotalLength() {
        List<String> logs = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            logs.add("[WriterAgent] line " + i + " " + "x".repeat(900));
        }

        List<String> cleaned = sanitizer.sanitizeLogs(logs);

        assertThat(cleaned).hasSizeLessThanOrEqualTo(AgentSkyResponseSanitizer.MAX_LOG_ENTRIES);
        for (String line : cleaned) {
            assertThat(line.length()).isLessThanOrEqualTo(AgentSkyResponseSanitizer.MAX_LOG_LINE_LENGTH);
        }
        int total = cleaned.stream().mapToInt(String::length).sum();
        assertThat(total).isLessThanOrEqualTo(AgentSkyResponseSanitizer.MAX_LOG_TOTAL_LENGTH);
    }

    @Test
    void errorCodesMapToSafeChineseMessages() {
        assertThat(sanitizer.publicErrorMessage("REVIEW_NOT_APPROVED"))
                .isEqualTo("正文在最大审核轮次内未通过，请调整创作灵感后重试");
        assertThat(sanitizer.publicErrorMessage("MODEL_INIT_FAILED"))
                .isEqualTo("模型服务初始化失败，请检查配置后重试");
        assertThat(sanitizer.publicErrorMessage("WORKFLOW_INIT_FAILED"))
                .isEqualTo("创作工作流初始化失败，请稍后重试");
        assertThat(sanitizer.publicErrorMessage("SOMETHING_ELSE"))
                .isEqualTo("创作流程执行失败，请稍后重试");
        assertThat(sanitizer.publicErrorMessage(null))
                .isEqualTo("创作流程执行失败，请稍后重试");
    }

    @Test
    void tokenUsageIsParsedCompletely() throws Exception {
        JsonNode raw = objectMapper.readTree(
                "{\"input_tokens\":8000,\"output_tokens\":4450,\"total_tokens\":12450,"
                        + "\"call_count\":8,\"cost_yuan\":0.0169,\"model\":\"deepseek-chat\"}");

        TokenUsage usage = sanitizer.sanitizeTokenUsage(raw);

        assertThat(usage.inputTokens()).isEqualTo(8000);
        assertThat(usage.outputTokens()).isEqualTo(4450);
        assertThat(usage.totalTokens()).isEqualTo(12450);
        assertThat(usage.callCount()).isEqualTo(8);
        assertThat(usage.costYuan()).isEqualByComparingTo("0.0169");
        assertThat(usage.model()).isEqualTo("deepseek-chat");
    }

    @Test
    void missingTokenUsageFallsBackToZerosAndDefaultModel() {
        TokenUsage usage = sanitizer.sanitizeTokenUsage(null);

        assertThat(usage.inputTokens()).isZero();
        assertThat(usage.outputTokens()).isZero();
        assertThat(usage.totalTokens()).isZero();
        assertThat(usage.callCount()).isZero();
        assertThat(usage.costYuan()).isEqualByComparingTo("0");
        assertThat(usage.model()).isEqualTo("deepseek-chat");
    }

    @Test
    void invalidTokenUsageValuesAreNeutralized() {
        // 用程序化节点构造非法值（负 token、无穷成本、超长模型名），绕开 JSON 解析器对越界数字的限制
        var node = objectMapper.createObjectNode();
        node.put("input_tokens", -5);
        node.put("cost_yuan", Double.POSITIVE_INFINITY);
        node.put("model", "x".repeat(200));

        TokenUsage usage = sanitizer.sanitizeTokenUsage(node);

        assertThat(usage.inputTokens()).isZero();
        assertThat(usage.costYuan()).isEqualByComparingTo("0");
        assertThat(usage.model()).isEqualTo("deepseek-chat");
    }
}
