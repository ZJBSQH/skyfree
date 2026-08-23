package com.freesky.sprintbootsky.application.port.out;

import java.util.List;

import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.PlotOutlineItem;
import com.freesky.sprintbootsky.domain.novel.ReviewIssue;
import com.freesky.sprintbootsky.domain.novel.WorldSetting;

/**
 * AgentSky 创作结果内容（类型化端口模型）。
 * 由防腐层从原始 JSON 解析为明确类型，不允许 Map/String 越界传播。
 */
public record AgentSkyNovelResult(
        List<String> completedChapters,
        List<CharacterProfile> characters,
        List<WorldSetting> worldSettings,
        List<PlotOutlineItem> plotOutline,
        int reviewRound,
        String currentDraft,
        List<ReviewIssue> reviewIssues) {

    public AgentSkyNovelResult {
        completedChapters = completedChapters == null ? List.of() : List.copyOf(completedChapters);
        characters = characters == null ? List.of() : List.copyOf(characters);
        worldSettings = worldSettings == null ? List.of() : List.copyOf(worldSettings);
        plotOutline = plotOutline == null ? List.of() : List.copyOf(plotOutline);
        currentDraft = currentDraft == null ? "" : currentDraft;
        reviewIssues = reviewIssues == null ? List.of() : List.copyOf(reviewIssues);
    }

    public AgentSkyNovelResult(
            List<String> completedChapters,
            List<CharacterProfile> characters,
            List<WorldSetting> worldSettings,
            List<PlotOutlineItem> plotOutline,
            int reviewRound) {
        this(completedChapters, characters, worldSettings, plotOutline, reviewRound, "", List.of());
    }
}
