package com.freesky.sprintbootsky.application.novel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.freesky.sprintbootsky.application.port.out.AgentSkyCreateResult;
import com.freesky.sprintbootsky.application.port.out.AgentSkyGateway;
import com.freesky.sprintbootsky.application.port.out.AgentSkyNovelResult;
import com.freesky.sprintbootsky.application.port.out.NovelCachePort;
import com.freesky.sprintbootsky.domain.novel.Chapter;
import com.freesky.sprintbootsky.domain.novel.NovelProject;
import com.freesky.sprintbootsky.domain.novel.RunLog;
import com.freesky.sprintbootsky.domain.user.UserNotFoundException;
import com.freesky.sprintbootsky.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 创建小说的用例编排。
 *
 * <p>完整业务流程（对应三个短事务 + 缓存边界）：
 * <ol>
 *   <li>校验用户与请求；</li>
 *   <li>短事务：创建 NovelProject(status=CREATING)，提交并取得 novelId；</li>
 *   <li>无数据库事务：调用 AgentSky、解析并清洗响应——AgentSky 属于外部 HTTP/LLM 长任务，
 *       绝不能占用数据库连接与事务，因此本类不声明任何 @Transactional；</li>
 *   <li>短事务：保存全部结果并更新为 COMPLETED，或保存安全错误并更新为 FAILED；</li>
 *   <li>事务提交后：刷新 Redis（缓存失败只记日志，不影响接口结果）；</li>
 *   <li>返回 Vue 兼容响应。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelApplicationService {

    /** 与 Vue 端 parseAgentLogs 保持一致的代理前缀。 */
    private static final Pattern AGENT_PREFIX = Pattern.compile("^\\[([A-Za-z]+Agent)]");
    private static final Set<String> KNOWN_AGENTS = Set.of(
            "SupervisorAgent", "SettingAgent", "CharacterAgent",
            "PlotAgent", "WriterAgent", "ReviewerAgent");

    private final NovelTransactionService transactionService;
    private final AgentSkyGateway agentSkyGateway;
    private final NovelCachePort novelCachePort;
    private final UserRepository userRepository;

    public NovelCreationResult createNovel(Long userId, String idea) {
        // 1. 校验用户：JWT 合法但用户已删除时直接 404，避免产生无主项目
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 2. 短事务1：先落 CREATING 记录并提交
        NovelProject project = NovelProject.start(userId, idea);
        Long novelId = transactionService.startCreation(project);

        // 3. 无数据库事务：调用 AgentSky（网关已把 429/超时/5xx/业务失败转为稳定的失败结果）
        AgentSkyCreateResult outcome = agentSkyGateway.create(idea);

        if (!outcome.success()) {
            // 短事务2b：FAILED 先提交再返回，失败结果不会被后续异常回滚
            transactionService.markCreationFailed(novelId, outcome.error());
            // 事务提交后刷新缓存
            novelCachePort.evictRecentProjects(userId);
            return NovelCreationResult.failure(
                    outcome.error(),
                    outcome.logs(),
                    NovelCreationResult.ResultPayload.from(outcome.result()),
                    outcome.tokenUsage());
        }

        try {
            project.complete(
                    outcome.result().worldSettings(),
                    outcome.result().plotOutline(),
                    outcome.result().characters(),
                    toChapters(outcome.result()),
                    toRunLogs(outcome.logs()),
                    outcome.tokenUsage(),
                    outcome.result().reviewRound());

            // 短事务2a：多表整体落库，任一表失败整体回滚（不产生伪 COMPLETED）
            transactionService.completeCreation(project);
        } catch (RuntimeException persistenceFailure) {
            // 不可恢复的持久化失败：completeCreation 事务已回滚，
            // 这里在新事务中补记 FAILED 后按业务失败返回
            log.error("小说结果持久化失败，novelId={}", novelId, persistenceFailure);
            transactionService.markCreationFailed(novelId, "创作结果保存失败，请稍后重试");
            novelCachePort.evictRecentProjects(userId);
            return NovelCreationResult.failure("创作结果保存失败，请稍后重试", outcome.tokenUsage());
        }

        // 4. MySQL 事务已提交后才写缓存；Redis 异常只在适配器内部记日志
        NovelSummary summary = NovelSummary.of(project);
        novelCachePort.cacheLatestNovel(userId, summary);
        novelCachePort.cacheNovelSummary(novelId, summary);
        novelCachePort.evictRecentProjects(userId);

        return NovelCreationResult.success(project);
    }

    private List<Chapter> toChapters(AgentSkyNovelResult result) {
        List<Chapter> chapters = new ArrayList<>();
        List<String> contents = result.completedChapters();
        for (int i = 0; i < contents.size(); i++) {
            chapters.add(new Chapter(i + 1, contents.get(i)));
        }
        return chapters;
    }

    /** 将清洗后的整行日志解析为（代理名, 原文）并编号，落库后仍可按 Vue 前缀格式还原返回。 */
    private List<RunLog> toRunLogs(List<String> lines) {
        List<RunLog> logs = new ArrayList<>();
        int sequence = 1;
        for (String line : lines) {
            Matcher matcher = AGENT_PREFIX.matcher(line);
            String agent = "system";
            if (matcher.find() && KNOWN_AGENTS.contains(matcher.group(1))) {
                agent = matcher.group(1);
            }
            logs.add(new RunLog(sequence++, agent, line));
        }
        return logs;
    }
}
