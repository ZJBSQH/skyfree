package com.freesky.sprintbootsky.domain.novel;

import java.util.List;

import lombok.Getter;

/**
 * 小说项目聚合根。
 * 章节、人物卡、运行日志、Token 用量均属于本聚合，没有独立生命周期，随聚合整体持久化。
 * 状态只能通过 {@link #complete} / {@link #markFailed} 从 CREATING 转换，
 * 禁止外部直接 setter 修改状态，保证不出现伪 COMPLETED / 非法状态。
 */
@Getter
public class NovelProject {

    private Long id;
    private final Long userId;
    private final String idea;
    private final String title;

    private NovelProjectStatus status;
    private int reviewRound;
    private List<WorldSetting> worldSettings;
    private List<PlotOutlineItem> plotOutline;
    private List<Chapter> chapters;
    private List<CharacterProfile> characters;
    private List<RunLog> logs;
    private TokenUsage tokenUsage;
    private String errorMessage;

    private NovelProject(Long userId, String idea, String title) {
        this.userId = userId;
        this.idea = idea;
        this.title = title;
        this.status = NovelProjectStatus.CREATING;
        this.reviewRound = 0;
        this.worldSettings = List.of();
        this.plotOutline = List.of();
        this.chapters = List.of();
        this.characters = List.of();
        this.logs = List.of();
        this.tokenUsage = null;
        this.errorMessage = null;
    }

    /** 发起一次创作：先以 CREATING 状态短事务落库，再在无事务环境中调用 AgentSky。 */
    public static NovelProject start(Long userId, String idea) {
        return new NovelProject(userId, idea, deriveTitle(idea));
    }

    private static String deriveTitle(String idea) {
        String trimmed = idea == null ? "" : idea.trim();
        if (trimmed.length() <= 40) {
            return trimmed;
        }
        return trimmed.substring(0, 40);
    }

    /** 插入数据库后由 Repository 回填自增主键；只允许赋值一次。 */
    public void assignId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("小说项目 ID 已分配，不允许重复赋值");
        }
        this.id = id;
    }

    /** 创作成功：CREATING → COMPLETED，一次性填充全部创作成果。 */
    public void complete(List<WorldSetting> worldSettings,
                         List<PlotOutlineItem> plotOutline,
                         List<CharacterProfile> characters,
                         List<Chapter> chapters,
                         List<RunLog> logs,
                         TokenUsage tokenUsage,
                         int reviewRound) {
        if (this.status != NovelProjectStatus.CREATING) {
            throw new IllegalStateException("仅 CREATING 状态可以转为 COMPLETED，当前: " + this.status);
        }
        this.worldSettings = List.copyOf(worldSettings);
        this.plotOutline = List.copyOf(plotOutline);
        this.characters = List.copyOf(characters);
        this.chapters = List.copyOf(chapters);
        this.logs = List.copyOf(logs);
        this.tokenUsage = tokenUsage;
        this.reviewRound = reviewRound;
        this.errorMessage = null;
        this.status = NovelProjectStatus.COMPLETED;
    }

    /** 创作失败：CREATING → FAILED，只记录经过清洗的安全错误信息。 */
    public void markFailed(String safeErrorMessage) {
        if (this.status != NovelProjectStatus.CREATING) {
            throw new IllegalStateException("仅 CREATING 状态可以转为 FAILED，当前: " + this.status);
        }
        this.errorMessage = safeErrorMessage;
        this.status = NovelProjectStatus.FAILED;
    }

    /**
     * 供持久化层从数据库重建聚合（只读回放），不作为业务状态迁移入口。
     */
    public static NovelProject reconstruct(Long id,
                                           Long userId,
                                           String idea,
                                           String title,
                                           NovelProjectStatus status,
                                           int reviewRound,
                                           List<WorldSetting> worldSettings,
                                           List<PlotOutlineItem> plotOutline,
                                           List<CharacterProfile> characters,
                                           List<Chapter> chapters,
                                           List<RunLog> logs,
                                           TokenUsage tokenUsage,
                                           String errorMessage) {
        NovelProject project = new NovelProject(userId, idea, title);
        project.id = id;
        project.status = status;
        project.reviewRound = reviewRound;
        project.worldSettings = worldSettings == null ? List.of() : List.copyOf(worldSettings);
        project.plotOutline = plotOutline == null ? List.of() : List.copyOf(plotOutline);
        project.characters = characters == null ? List.of() : List.copyOf(characters);
        project.chapters = chapters == null ? List.of() : List.copyOf(chapters);
        project.logs = logs == null ? List.of() : List.copyOf(logs);
        project.tokenUsage = tokenUsage;
        project.errorMessage = errorMessage;
        return project;
    }
}
