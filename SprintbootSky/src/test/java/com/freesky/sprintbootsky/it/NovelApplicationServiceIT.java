package com.freesky.sprintbootsky.it;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.freesky.sprintbootsky.application.novel.NovelApplicationService;
import com.freesky.sprintbootsky.application.novel.NovelCreationResult;
import com.freesky.sprintbootsky.application.novel.NovelTransactionService;
import com.freesky.sprintbootsky.application.port.out.AgentSkyCreateResult;
import com.freesky.sprintbootsky.application.port.out.AgentSkyGateway;
import com.freesky.sprintbootsky.application.port.out.AgentSkyNovelResult;
import com.freesky.sprintbootsky.domain.novel.Chapter;
import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.CharacterRelationship;
import com.freesky.sprintbootsky.domain.novel.NovelProject;
import com.freesky.sprintbootsky.domain.novel.NovelProjectRepository;
import com.freesky.sprintbootsky.domain.novel.NovelProjectStatus;
import com.freesky.sprintbootsky.domain.novel.RunLog;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.domain.user.UserRepository;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.ChapterEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.CharacterProfileEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.NovelProjectEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.RunLogEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.entity.TokenUsageEntity;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.ChapterMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.CharacterProfileMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.NovelProjectMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.RunLogMapper;
import com.freesky.sprintbootsky.infrastructure.persistence.mapper.TokenUsageMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 真实 MySQL + 真实事务边界下的创作用例集成测试（AgentSky 网关用 Mock，不依赖 AgentSky 服务）。
 */
class NovelApplicationServiceIT extends AbstractMySqlIT {

    @Autowired
    private NovelApplicationService novelApplicationService;
    @Autowired
    private NovelTransactionService transactionService;
    @Autowired
    private NovelProjectRepository novelProjectRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NovelProjectMapper novelProjectMapper;
    @Autowired
    private ChapterMapper chapterMapper;
    @Autowired
    private CharacterProfileMapper characterProfileMapper;
    @Autowired
    private RunLogMapper runLogMapper;
    @Autowired
    private TokenUsageMapper tokenUsageMapper;

    @MockitoBean
    private AgentSkyGateway agentSkyGateway;

    private Long userId;

    @BeforeEach
    void createUser() {
        userId = userRepository.save(new User("zheng", "it-" + System.nanoTime() + "@example.com", "hash")).getId();
    }

    private AgentSkyCreateResult successOutcome() {
        return AgentSkyCreateResult.success(
                List.of("[WriterAgent] Drafted chapter"),
                new AgentSkyNovelResult(
                        List.of("第一章正文"),
                        List.of(new CharacterProfile("林风", "protagonist", "", "", "", "", "",
                                List.of(new CharacterRelationship("师父", "师徒", "变化")))),
                        List.of(),
                        List.of(),
                        1),
                new TokenUsage(100, 200, 300, 4, new BigDecimal("0.0001"), "deepseek-chat"));
    }

    @Test
    void successPersistsAllTablesAndAgentSkyRunsOutsideTransaction() {
        AtomicBoolean insideTransaction = new AtomicBoolean(true);
        when(agentSkyGateway.create(anyString())).thenAnswer(invocation -> {
            insideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return successOutcome();
        });

        NovelCreationResult result = novelApplicationService.createNovel(userId, "一座云上之城");

        assertThat(result.success()).isTrue();
        // 调用 AgentSky 时绝不能处于活动 MySQL 事务中
        assertThat(insideTransaction).isFalse();

        // 五张业务表均已落库
        NovelProjectEntity project = novelProjectMapper.selectOne(
                new LambdaQueryWrapper<NovelProjectEntity>().eq(NovelProjectEntity::getUserId, userId));
        assertThat(project).isNotNull();
        assertThat(project.getStatus()).isEqualTo("COMPLETED");
        assertThat(project.getReviewRound()).isEqualTo(1);

        Long novelId = project.getId();
        assertThat(chapterMapper.selectCount(
                new LambdaQueryWrapper<ChapterEntity>().eq(ChapterEntity::getNovelProjectId, novelId)))
                .isEqualTo(1);
        assertThat(characterProfileMapper.selectCount(
                new LambdaQueryWrapper<CharacterProfileEntity>().eq(CharacterProfileEntity::getNovelProjectId, novelId)))
                .isEqualTo(1);
        assertThat(runLogMapper.selectCount(
                new LambdaQueryWrapper<RunLogEntity>().eq(RunLogEntity::getNovelProjectId, novelId)))
                .isEqualTo(1);
        assertThat(tokenUsageMapper.selectCount(
                new LambdaQueryWrapper<TokenUsageEntity>().eq(TokenUsageEntity::getNovelProjectId, novelId)))
                .isEqualTo(1);
    }

    @Test
    void gatewayFailureCommitsFailedState() {
        when(agentSkyGateway.create(anyString()))
                .thenReturn(AgentSkyCreateResult.failure("创作流程执行失败，请稍后重试", "WORKFLOW_FAILED", TokenUsage.zero()));

        NovelCreationResult result = novelApplicationService.createNovel(userId, "灵感");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("创作流程执行失败，请稍后重试");

        // FAILED 已提交，不会因后续 RuntimeException 被回滚
        NovelProjectEntity project = novelProjectMapper.selectOne(
                new LambdaQueryWrapper<NovelProjectEntity>().eq(NovelProjectEntity::getUserId, userId));
        assertThat(project.getStatus()).isEqualTo("FAILED");
        assertThat(project.getErrorMessage()).isEqualTo("创作流程执行失败，请稍后重试");
    }

    @Test
    void anyChildTableFailureRollsBackWholeCompletionTransaction() {
        NovelProject project = NovelProject.start(userId, "灵感");
        transactionService.startCreation(project);
        Long novelId = project.getId();

        // 构造一个必然触发数据库约束失败的子表数据（chapter.content 为 NOT NULL 且未做领域默认值兜底）
        project.complete(
                List.of(),
                List.of(),
                List.of(),
                List.of(new Chapter(1, null)),
                List.of(new RunLog(1, "WriterAgent", "[WriterAgent] x")),
                TokenUsage.zero(),
                1);

        assertThatThrownBy(() -> transactionService.completeCreation(project))
                .isInstanceOf(RuntimeException.class);

        // 整个短事务回滚：项目不能处于伪 COMPLETED 状态
        NovelProject state = novelProjectRepository.findProjectState(novelId).orElseThrow();
        assertThat(state.getStatus()).isEqualTo(NovelProjectStatus.CREATING);
        assertThat(chapterMapper.selectCount(
                new LambdaQueryWrapper<ChapterEntity>().eq(ChapterEntity::getNovelProjectId, novelId)))
                .isZero();
    }
}
