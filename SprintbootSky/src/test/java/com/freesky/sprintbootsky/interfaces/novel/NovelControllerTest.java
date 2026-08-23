package com.freesky.sprintbootsky.interfaces.novel;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freesky.sprintbootsky.application.novel.NovelApplicationService;
import com.freesky.sprintbootsky.application.novel.NovelCreationResult;
import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.CharacterRelationship;
import com.freesky.sprintbootsky.domain.novel.Chapter;
import com.freesky.sprintbootsky.domain.novel.NovelProject;
import com.freesky.sprintbootsky.domain.novel.PlotOutlineItem;
import com.freesky.sprintbootsky.domain.novel.ReviewIssue;
import com.freesky.sprintbootsky.domain.novel.RunLog;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;
import com.freesky.sprintbootsky.domain.novel.WorldSetting;
import com.freesky.sprintbootsky.infrastructure.security.JwtAuthenticationFilter;
import com.freesky.sprintbootsky.infrastructure.security.JwtTokenProvider;
import com.freesky.sprintbootsky.infrastructure.security.SecurityConfig;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = NovelController.class, properties = {
        "security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789abcdef"})
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class})
class NovelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private NovelApplicationService novelApplicationService;

    private NovelCreationResult successResult() {
        NovelProject project = NovelProject.start(1L, "一座云上之城");
        project.assignId(10L);
        project.complete(
                List.of(new WorldSetting("世界观", "k1", "云上之城", 1)),
                List.of(new PlotOutlineItem("n1", "序章", "摘要", List.of("伏笔"),
                        List.of("林风"), List.of("k1"), "主线", "铺垫")),
                List.of(new CharacterProfile("林风", "protagonist", "", "", "", "", "",
                        List.of(new CharacterRelationship("师父", "师徒", "逐渐疏远")))),
                List.of(new Chapter(1, "正文"), new Chapter(2, "第二章")),
                List.of(new RunLog(1, "WriterAgent", "[WriterAgent] Drafted chapter")),
                new TokenUsage(8000, 4450, 12450, 8, new BigDecimal("0.0169"), "deepseek-chat"),
                1);
        return NovelCreationResult.success(project);
    }

    @Test
    void createNovelWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(post("/api/novels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"一座云上之城\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("未认证或登录已过期")));
    }

    @Test
    void createNovelWithInvalidTokenReturns401Json() throws Exception {
        mockMvc.perform(post("/api/novels")
                        .header("Authorization", "Bearer garbage-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"一座云上之城\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void createNovelSuccessReturnsVueCompatibleBody() throws Exception {
        when(novelApplicationService.createNovel(eq(1L), eq("一座云上之城")))
                .thenReturn(successResult());
        String token = jwtTokenProvider.issue(1L, "zheng@example.com");

        mockMvc.perform(post("/api/novels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"一座云上之城\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.logs[0]", is("[WriterAgent] Drafted chapter")))
                .andExpect(jsonPath("$.result.completed_chapters[0]", is("正文")))
                .andExpect(jsonPath("$.result.characters[0].name", is("林风")))
                .andExpect(jsonPath("$.result.characters[0].role_type", is("protagonist")))
                .andExpect(jsonPath("$.result.characters[0].relationships[0].name", is("师父")))
                .andExpect(jsonPath("$.result.characters[0].relationships[0].relation", is("师徒")))
                .andExpect(jsonPath("$.result.world_settings[0].category", is("世界观")))
                .andExpect(jsonPath("$.result.plot_outline[0].tension_level", is("铺垫")))
                .andExpect(jsonPath("$.result.review_round", is(1)))
                .andExpect(jsonPath("$.error", is("")))
                .andExpect(jsonPath("$.token_usage.input_tokens", is(8000)))
                .andExpect(jsonPath("$.token_usage.output_tokens", is(4450)))
                .andExpect(jsonPath("$.token_usage.total_tokens", is(12450)))
                .andExpect(jsonPath("$.token_usage.call_count", is(8)))
                .andExpect(jsonPath("$.token_usage.cost_yuan", is(0.0169)))
                .andExpect(jsonPath("$.token_usage.model", is("deepseek-chat")));
    }

    @Test
    void createNovelFailureReturnsCompleteVueCompatibleBody() throws Exception {
        when(novelApplicationService.createNovel(eq(1L), any()))
                .thenReturn(NovelCreationResult.failure(
                        "创作流程执行失败，请稍后重试",
                        List.of("[ReviewerAgent] FAIL (1个问题)"),
                        new NovelCreationResult.ResultPayload(
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                3,
                                "最后一版草稿",
                                List.of(new ReviewIssue("major", "logic_flaw", "动机铺垫不足", "writer", "补充主角行动原因"))),
                        new TokenUsage(0, 0, 0, 0, BigDecimal.ZERO, "deepseek-chat")));
        String token = jwtTokenProvider.issue(1L, "zheng@example.com");

        mockMvc.perform(post("/api/novels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"一座云上之城\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.logs[0]", is("[ReviewerAgent] FAIL (1个问题)")))
                .andExpect(jsonPath("$.result").isNotEmpty())
                .andExpect(jsonPath("$.result.completed_chapters").isEmpty())
                .andExpect(jsonPath("$.result.current_draft", is("最后一版草稿")))
                .andExpect(jsonPath("$.result.review_round", is(3)))
                .andExpect(jsonPath("$.result.review_issues[0].description", is("动机铺垫不足")))
                .andExpect(jsonPath("$.error", is("创作流程执行失败，请稍后重试")))
                .andExpect(jsonPath("$.token_usage.input_tokens", is(0)))
                .andExpect(jsonPath("$.token_usage.output_tokens", is(0)))
                .andExpect(jsonPath("$.token_usage.total_tokens", is(0)))
                .andExpect(jsonPath("$.token_usage.call_count", is(0)))
                .andExpect(jsonPath("$.token_usage.cost_yuan", is(0)))
                .andExpect(jsonPath("$.token_usage.model", is("deepseek-chat")));
    }

    @Test
    void blankIdeaReturns400WithNovelShapedBody() throws Exception {
        String token = jwtTokenProvider.issue(1L, "zheng@example.com");

        mockMvc.perform(post("/api/novels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.logs").isEmpty())
                .andExpect(jsonPath("$.result").isNotEmpty())
                .andExpect(jsonPath("$.error", is("创作灵感不能为空")))
                .andExpect(jsonPath("$.token_usage.input_tokens", is(0)))
                .andExpect(jsonPath("$.token_usage.model", is("deepseek-chat")));
    }

    @Test
    void ideaOver2000CharsReturns400WithNovelShapedBody() throws Exception {
        String token = jwtTokenProvider.issue(1L, "zheng@example.com");
        String longIdea = "长".repeat(2001);

        mockMvc.perform(post("/api/novels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"" + longIdea + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("创作灵感不能超过2000字符")))
                .andExpect(jsonPath("$.token_usage.total_tokens", is(0)));
    }
}
