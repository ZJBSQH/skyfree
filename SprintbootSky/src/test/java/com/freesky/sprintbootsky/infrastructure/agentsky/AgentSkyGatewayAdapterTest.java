package com.freesky.sprintbootsky.infrastructure.agentsky;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.ObjectMapper;

import com.freesky.sprintbootsky.application.port.out.AgentSkyCreateResult;
import com.freesky.sprintbootsky.application.port.out.AgentSkyHealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSkyGatewayAdapterTest {

    @Mock
    private AgentSkyClient agentSkyClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentSkyGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        AgentSkyProperties properties = new AgentSkyProperties(
                "http://127.0.0.1:8765", "service-secret",
                Duration.ofSeconds(5), Duration.ofMinutes(10), "deepseek-chat");
        adapter = new AgentSkyGatewayAdapter(
                agentSkyClient, new AgentSkyResponseSanitizer(properties), objectMapper);
    }

    @Test
    void healthOkIsPassedThrough() {
        when(agentSkyClient.health())
                .thenReturn("{\"status\":\"ok\",\"service\":\"agentsky\"}");

        AgentSkyHealth health = adapter.checkHealth();

        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.service()).isEqualTo("agentsky");
    }

    @Test
    void healthFailureBecomesUnavailableInsteadOfThrowing() {
        when(agentSkyClient.health()).thenThrow(new ResourceAccessException("connection refused"));

        AgentSkyHealth health = adapter.checkHealth();

        assertThat(health.status()).isEqualTo("unavailable");
    }

    @Test
    void successResponseIsParsedToTypedResult() throws Exception {
        String json = """
                {
                  "success": true,
                  "logs": ["[WriterAgent] Drafted chapter"],
                  "result": {
                    "completed_chapters": ["第一章正文"],
                    "characters": [{
                      "name": "林风",
                      "role_type": "protagonist",
                      "appearance": "", "personality": "", "background": "",
                      "ability": "", "motivation": "",
                      "relationships": [{"name": "师父", "relation": "师徒", "dynamic": "逐渐疏远"}]
                    }],
                    "world_settings": [{"category": "世界观", "key": "k1", "content": "云上之城", "version": 1}],
                    "plot_outline": [{"id": "n1", "title": "序章", "summary": "s",
                        "foreshadowing": ["f1"], "characters_involved": ["林风"],
                        "settings_revealed": ["k1"], "type": "主线", "tension_level": "铺垫"}],
                    "review_round": 2
                  },
                  "token_usage": {"input_tokens": 100, "output_tokens": 200, "total_tokens": 300,
                    "call_count": 4, "cost_yuan": 0.1234, "model": "deepseek-chat"}
                }
                """;
        when(agentSkyClient.create("idea")).thenReturn(json);

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.success()).isTrue();
        assertThat(result.logs()).containsExactly("[WriterAgent] Drafted chapter");
        assertThat(result.result().completedChapters()).containsExactly("第一章正文");
        assertThat(result.result().characters().get(0).name()).isEqualTo("林风");
        assertThat(result.result().characters().get(0).relationships().get(0).relation()).isEqualTo("师徒");
        assertThat(result.result().worldSettings().get(0).content()).isEqualTo("云上之城");
        assertThat(result.result().plotOutline().get(0).tensionLevel()).isEqualTo("铺垫");
        assertThat(result.result().reviewRound()).isEqualTo(2);
        assertThat(result.tokenUsage().totalTokens()).isEqualTo(300);
    }

    @Test
    void businessFailureUsesMappedMessageAndDropsRawError() throws Exception {
        String json = """
                {
                  "success": false,
                  "logs": ["[ReviewerAgent] FAIL (1个问题)", "[ERROR] Traceback (most recent call last):"],
                  "result": {
                    "current_draft": "最后一版草稿",
                    "review_round": 3,
                    "review_issues": [{
                      "severity": "major",
                      "category": "logic_flaw",
                      "description": "动机铺垫不足",
                      "target_agent": "writer",
                      "suggestion": "补充主角行动原因"
                    }]
                  },
                  "error": "内部细节: provider credential leaked",
                  "error_code": "REVIEW_NOT_APPROVED",
                  "token_usage": {"input_tokens": 10, "output_tokens": 20, "total_tokens": 30,
                    "call_count": 2, "cost_yuan": 0, "model": "deepseek-chat"}
                }
                """;
        when(agentSkyClient.create(anyString())).thenReturn(json);

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("正文在最大审核轮次内未通过，请调整创作灵感后重试");
        assertThat(result.logs()).containsExactly("[ReviewerAgent] FAIL (1个问题)");
        assertThat(result.result().currentDraft()).isEqualTo("最后一版草稿");
        assertThat(result.result().reviewRound()).isEqualTo(3);
        assertThat(result.result().reviewIssues().get(0).description()).isEqualTo("动机铺垫不足");
        assertThat(result.tokenUsage().inputTokens()).isEqualTo(10);
        assertThat(result.tokenUsage().totalTokens()).isEqualTo(30);
    }

    @Test
    void http429BecomesStableFailure() {
        when(agentSkyClient.create(anyString())).thenThrow(
                new RestClientResponseException(
                        "Too Many Requests", HttpStatusCode.valueOf(429), "Too Many Requests",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("创作服务繁忙，请稍后重试");
        assertThat(result.tokenUsage().inputTokens()).isZero();
        assertThat(result.tokenUsage().model()).isEqualTo("deepseek-chat");
    }

    @Test
    void http500BecomesStableFailure() {
        when(agentSkyClient.create(anyString())).thenThrow(
                new RestClientResponseException(
                        "Server Error", HttpStatusCode.valueOf(500), "Server Error",
                        HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("创作流程执行失败，请稍后重试");
    }

    @Test
    void connectionFailureBecomesStableFailure() {
        when(agentSkyClient.create(anyString()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("创作服务连接失败，请稍后重试");
    }

    @Test
    void missingAgentSkyTokenBecomesConfigFailure() {
        when(agentSkyClient.create(anyString()))
                .thenThrow(new IllegalStateException("AGENTSKY_API_TOKEN is not configured"));

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("AgentSky 服务未正确配置，请联系管理员");
    }

    @Test
    void sensitiveLogsAreSanitizedOnSuccess() throws Exception {
        String json = """
                {
                  "success": true,
                  "logs": ["[WriterAgent] 正常日志",
                           "[ERROR] Traceback (most recent call last):",
                           "  File \\"/app/graph/workflow.py\\", line 12, in invoke",
                           "[ERROR] secret=sk-abcdef leaked"],
                  "result": {"completed_chapters": ["正文"], "characters": [],
                    "world_settings": [], "plot_outline": [], "review_round": 1},
                  "token_usage": {}
                }
                """;
        when(agentSkyClient.create(anyString())).thenReturn(json);

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.logs()).containsExactly("[WriterAgent] 正常日志");
        assertThat(result.tokenUsage().model()).isEqualTo("deepseek-chat");
    }

    @Test
    void malformedBodyBecomesSafeFailure() {
        when(agentSkyClient.create(anyString())).thenReturn("this is not json {{{");

        AgentSkyCreateResult result = adapter.create("idea");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("创作流程执行失败，请稍后重试");
        assertThat(result.tokenUsage().model()).isEqualTo("deepseek-chat");
    }
}
