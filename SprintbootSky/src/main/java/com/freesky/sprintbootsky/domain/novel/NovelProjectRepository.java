package com.freesky.sprintbootsky.domain.novel;

import java.util.Optional;

/**
 * 小说项目聚合仓储接口（领域端口）。
 * Application 只依赖本接口完成聚合整体持久化，不直接操作任何 MyBatis-Plus Mapper；
 * 子表（章节/人物/日志/用量）随聚合保存，不为它们单独建立领域仓储。
 */
public interface NovelProjectRepository {

    /** 插入 CREATING 状态的初始项目记录，并回填自增主键。 */
    NovelProject begin(NovelProject project);

    /** 在调用方事务内整体保存聚合并更新为 COMPLETED；任何一张表失败都应整体回滚。 */
    void saveCompleted(NovelProject project);

    /** 在调用方事务内将项目标记为 FAILED，只写状态与安全错误信息。 */
    void saveFailedState(NovelProject project);

    /** 只读项目主表状态（不加载子表），用于失败标记等轻量状态迁移。 */
    Optional<NovelProject> findProjectState(Long novelProjectId);

    /** 读取完整聚合（含章节、人物、日志、用量），用于后续列表/详情与测试校验。 */
    Optional<NovelProject> findById(Long novelProjectId);
}
