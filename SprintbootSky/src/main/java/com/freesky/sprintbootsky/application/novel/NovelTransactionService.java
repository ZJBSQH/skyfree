package com.freesky.sprintbootsky.application.novel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freesky.sprintbootsky.domain.novel.NovelProject;
import com.freesky.sprintbootsky.domain.novel.NovelProjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * 小说持久化事务边界。
 * 创作流程拆成三个短事务，严禁用覆盖整个流程的事务包住 AgentSky HTTP 调用：
 *
 * <pre>
 *   校验用户与请求
 *     ↓
 *   短事务1 {@link #startCreation}    ：插入 NovelProject(CREATING)，提交并取得 novelId
 *     ↓
 *   无数据库事务                        ：调用 AgentSky、解析并清洗响应
 *     ↓
 *   短事务2 {@link #completeCreation}  ：多表整体保存并更新 COMPLETED，任一失败整体回滚
 *     或 {@link #markCreationFailed}  ：写入安全错误并更新 FAILED
 *     ↓
 *   事务提交后                          ：刷新 Redis
 * </pre>
 *
 * FAILED 记录必须先提交再返回业务失败结果，因此本服务不允许“写 FAILED 后继续抛
 * RuntimeException”的流程；也禁止在同类中自调用（Spring 代理下 this.xxx() 不会开启事务）。
 */
@Service
@RequiredArgsConstructor
public class NovelTransactionService {

    private final NovelProjectRepository novelProjectRepository;

    /** 短事务1：先以 CREATING 落库并回填 novelId，随后立即提交，不占用数据库连接等待 LLM。 */
    @Transactional
    public Long startCreation(NovelProject project) {
        return novelProjectRepository.begin(project).getId();
    }

    /** 短事务2a：成功路径，多表写入必须在同一事务内完成，任何一张业务表失败整体回滚。 */
    @Transactional
    public void completeCreation(NovelProject project) {
        novelProjectRepository.saveCompleted(project);
    }

    /**
     * 短事务2b：失败路径。先读出项目状态、走领域状态迁移（CREATING → FAILED），
     * 再只更新状态与安全错误信息；本方法正常返回即代表 FAILED 已提交。
     */
    @Transactional
    public void markCreationFailed(Long novelProjectId, String safeErrorMessage) {
        NovelProject project = novelProjectRepository.findProjectState(novelProjectId)
                .orElseThrow(() -> new IllegalStateException("小说项目不存在，无法标记失败: " + novelProjectId));
        project.markFailed(safeErrorMessage);
        novelProjectRepository.saveFailedState(project);
    }
}
