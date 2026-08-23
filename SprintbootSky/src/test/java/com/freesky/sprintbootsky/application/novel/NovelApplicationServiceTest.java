package com.freesky.sprintbootsky.application.novel;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freesky.sprintbootsky.application.port.out.AgentSkyCreateResult;
import com.freesky.sprintbootsky.application.port.out.AgentSkyGateway;
import com.freesky.sprintbootsky.application.port.out.AgentSkyNovelResult;
import com.freesky.sprintbootsky.application.port.out.NovelCachePort;
import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.CharacterRelationship;
import com.freesky.sprintbootsky.domain.novel.ReviewIssue;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;
import com.freesky.sprintbootsky.domain.novel.WorldSetting;
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.domain.user.UserNotFoundException;
import com.freesky.sprintbootsky.domain.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelApplicationServiceTest {

    @Mock
    private NovelTransactionService transactionService;
    @Mock
    private AgentSkyGateway agentSkyGateway;
    @Mock
    private NovelCachePort novelCachePort;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NovelApplicationService novelApplicationService;

    private User existingUser() {
        User user = new User("zheng", "zheng@example.com", "hash");
        user.assignId(1L);
        return user;
    }

    private AgentSkyCreateResult successOutcome() {
        return AgentSkyCreateResult.success(
                List.of("[WriterAgent] Drafted chapter", "[SupervisorAgent] 分配任务"),
                new AgentSkyNovelResult(
                        List.of("第一章正文"),
                        List.of(new CharacterProfile("林风", "protagonist", "", "", "", "", "",
                                List.of(new CharacterRelationship("师父", "师徒", "变化")))),
                        List.of(new WorldSetting("世界观", "key-1", "云上之城", 1)),
                        List.of(),
                        2),
                new TokenUsage(100, 200, 300, 5, null, "deepseek-chat"));
    }

    @Test
    void createNovelSuccessPersistsThenRefreshesCache() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(transactionService.startCreation(any())).thenReturn(10L);
        when(agentSkyGateway.create("idea")).thenReturn(successOutcome());

        NovelCreationResult result = novelApplicationService.createNovel(1L, "idea");

        assertThat(result.success()).isTrue();
        assertThat(result.logs()).containsExactly("[WriterAgent] Drafted chapter", "[SupervisorAgent] 分配任务");
        assertThat(result.result().completedChapters()).containsExactly("第一章正文");
        assertThat(result.result().characters().get(0).name()).isEqualTo("林风");
        assertThat(result.result().reviewRound()).isEqualTo(2);
        assertThat(result.error()).isEmpty();

        // 缓存写入必须发生在 MySQL 短事务（completeCreation）提交之后
        InOrder order = inOrder(transactionService, novelCachePort);
        order.verify(transactionService).startCreation(any());
        order.verify(transactionService).completeCreation(any());
        order.verify(novelCachePort).cacheLatestNovel(eq(1L), any());
        order.verify(novelCachePort).cacheNovelSummary(eq(10L), any());
        order.verify(novelCachePort).evictRecentProjects(1L);
    }

    @Test
    void businessFailureMarksFailedAndReturnsVueCompatibleFailure() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(transactionService.startCreation(any())).thenReturn(10L);
        TokenUsage usage = new TokenUsage(1, 2, 3, 1, null, "deepseek-chat");
        when(agentSkyGateway.create("idea"))
                .thenReturn(AgentSkyCreateResult.failure(
                        List.of("[ReviewerAgent] FAIL (1个问题)"),
                        new AgentSkyNovelResult(
                                List.of(), List.of(), List.of(), List.of(), 3,
                                "最后一版草稿",
                                List.of(new ReviewIssue("major", "logic_flaw", "动机铺垫不足", "writer", "补充主角行动原因"))),
                        "正文在最大审核轮次内未通过，请调整创作灵感后重试",
                        "REVIEW_NOT_APPROVED",
                        usage));

        NovelCreationResult result = novelApplicationService.createNovel(1L, "idea");

        assertThat(result.success()).isFalse();
        assertThat(result.logs()).containsExactly("[ReviewerAgent] FAIL (1个问题)");
        assertThat(result.error()).isEqualTo("正文在最大审核轮次内未通过，请调整创作灵感后重试");
        assertThat(result.tokenUsage().totalTokens()).isEqualTo(3);
        assertThat(result.result().completedChapters()).isEmpty();
        assertThat(result.result().currentDraft()).isEqualTo("最后一版草稿");
        assertThat(result.result().reviewRound()).isEqualTo(3);
        assertThat(result.result().reviewIssues().get(0).description()).isEqualTo("动机铺垫不足");

        verify(transactionService).markCreationFailed(10L, "正文在最大审核轮次内未通过，请调整创作灵感后重试");
        verify(transactionService, never()).completeCreation(any());
        verify(novelCachePort).evictRecentProjects(1L);
        verify(novelCachePort, never()).cacheLatestNovel(any(), any());
    }

    @Test
    void persistenceFailureReturnsFailureAndMarksFailed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(transactionService.startCreation(any())).thenReturn(10L);
        when(agentSkyGateway.create("idea")).thenReturn(successOutcome());
        doThrow(new RuntimeException("db down")).when(transactionService).completeCreation(any());

        NovelCreationResult result = novelApplicationService.createNovel(1L, "idea");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("创作结果保存失败，请稍后重试");
        verify(transactionService).markCreationFailed(10L, "创作结果保存失败，请稍后重试");
    }

    @Test
    void missingUserThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> novelApplicationService.createNovel(99L, "idea"))
                .isInstanceOf(UserNotFoundException.class);
        verify(transactionService, never()).startCreation(any());
        verify(agentSkyGateway, never()).create(any());
    }

    @Test
    void gatewayFailureStillReturnsCompleteTokenUsage() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser()));
        when(transactionService.startCreation(any())).thenReturn(10L);
        when(agentSkyGateway.create("idea"))
                .thenReturn(AgentSkyCreateResult.failure("创作服务连接失败，请稍后重试", "AGENTSKY_UNAVAILABLE", TokenUsage.zero()));

        NovelCreationResult result = novelApplicationService.createNovel(1L, "idea");

        assertThat(result.success()).isFalse();
        assertThat(result.tokenUsage().inputTokens()).isZero();
        assertThat(result.tokenUsage().model()).isEqualTo("deepseek-chat");
        verify(novelCachePort, never()).cacheNovelSummary(anyLong(), any());
    }
}
