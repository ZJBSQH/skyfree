package com.freesky.sprintbootsky.application.novel;

import java.util.List;

import com.freesky.sprintbootsky.application.port.out.AgentSkyNovelResult;
import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.NovelProject;
import com.freesky.sprintbootsky.domain.novel.PlotOutlineItem;
import com.freesky.sprintbootsky.domain.novel.ReviewIssue;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;
import com.freesky.sprintbootsky.domain.novel.WorldSetting;

/**
 * 创建小说的用例结果。Controller 用它转换为 Vue 兼容的 CreateNovelResponse。
 * 成功与失败都携带完整字段：logs 已清洗、token_usage 永远字段完整。
 */
public record NovelCreationResult(
        boolean success,
        List<String> logs,
        ResultPayload result,
        String error,
        TokenUsage tokenUsage) {

    /** 创作结果载荷，字段语义与 Vue 契约一致（JSON 字段名由接口层 DTO 映射）。 */
    public record ResultPayload(
            List<String> completedChapters,
            List<CharacterProfile> characters,
            List<WorldSetting> worldSettings,
            List<PlotOutlineItem> plotOutline,
            int reviewRound,
            String currentDraft,
            List<ReviewIssue> reviewIssues) {

        public ResultPayload {
            completedChapters = completedChapters == null ? List.of() : List.copyOf(completedChapters);
            characters = characters == null ? List.of() : List.copyOf(characters);
            worldSettings = worldSettings == null ? List.of() : List.copyOf(worldSettings);
            plotOutline = plotOutline == null ? List.of() : List.copyOf(plotOutline);
            currentDraft = currentDraft == null ? "" : currentDraft;
            reviewIssues = reviewIssues == null ? List.of() : List.copyOf(reviewIssues);
        }

        public static ResultPayload empty() {
            return new ResultPayload(List.of(), List.of(), List.of(), List.of(), 0, "", List.of());
        }

        public static ResultPayload from(NovelProject project) {
            return new ResultPayload(
                    project.getChapters().stream().map(ch -> ch.content()).toList(),
                    project.getCharacters(),
                    project.getWorldSettings(),
                    project.getPlotOutline(),
                    project.getReviewRound(),
                    "",
                    List.of());
        }

        public static ResultPayload from(AgentSkyNovelResult result) {
            if (result == null) {
                return empty();
            }
            return new ResultPayload(
                    result.completedChapters(),
                    result.characters(),
                    result.worldSettings(),
                    result.plotOutline(),
                    result.reviewRound(),
                    result.currentDraft(),
                    result.reviewIssues());
        }
    }

    public NovelCreationResult {
        logs = logs == null ? List.of() : List.copyOf(logs);
        error = error == null ? "" : error;
        tokenUsage = tokenUsage == null ? TokenUsage.zero() : tokenUsage;
    }

    public static NovelCreationResult success(NovelProject project) {
        List<String> responseLogs = project.getLogs().stream().map(log -> log.message()).toList();
        return new NovelCreationResult(true, responseLogs, ResultPayload.from(project), "",
                project.getTokenUsage() == null ? TokenUsage.zero() : project.getTokenUsage());
    }

    public static NovelCreationResult failure(String error, TokenUsage tokenUsage) {
        return new NovelCreationResult(false, List.of(), ResultPayload.empty(), error, tokenUsage);
    }

    public static NovelCreationResult failure(
            String error,
            List<String> logs,
            ResultPayload result,
            TokenUsage tokenUsage) {
        return new NovelCreationResult(false, logs, result == null ? ResultPayload.empty() : result, error, tokenUsage);
    }
}
