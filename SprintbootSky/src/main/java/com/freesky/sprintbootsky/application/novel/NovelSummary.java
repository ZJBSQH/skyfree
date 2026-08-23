package com.freesky.sprintbootsky.application.novel;

import com.freesky.sprintbootsky.domain.novel.NovelProject;

/**
 * 小说摘要（缓存专用 DTO）。
 * 只缓存摘要字段，不缓存完整聚合或持久化实体，避免大正文进 Redis。
 */
public record NovelSummary(
        Long novelId,
        Long userId,
        String title,
        String idea,
        String status,
        int reviewRound,
        int chapterCount) {

    /** 缓存中的 idea 截断到 200 字符，控制缓存体积。 */
    public static NovelSummary of(NovelProject project) {
        String idea = project.getIdea() == null ? "" : project.getIdea();
        String excerpt = idea.length() <= 200 ? idea : idea.substring(0, 200);
        return new NovelSummary(
                project.getId(),
                project.getUserId(),
                project.getTitle(),
                excerpt,
                project.getStatus().name(),
                project.getReviewRound(),
                project.getChapters().size());
    }
}
