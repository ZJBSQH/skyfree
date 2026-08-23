package com.freesky.sprintbootsky.interfaces.novel.dto;

import java.math.BigDecimal;
import java.util.List;

// Jackson 3 与 Jackson 2 共享 jackson-annotations 注解包
import com.fasterxml.jackson.annotation.JsonProperty;
import com.freesky.sprintbootsky.application.novel.NovelCreationResult;
import com.freesky.sprintbootsky.domain.novel.CharacterProfile;
import com.freesky.sprintbootsky.domain.novel.CharacterRelationship;
import com.freesky.sprintbootsky.domain.novel.PlotOutlineItem;
import com.freesky.sprintbootsky.domain.novel.ReviewIssue;
import com.freesky.sprintbootsky.domain.novel.TokenUsage;
import com.freesky.sprintbootsky.domain.novel.WorldSetting;

/**
 * 创建小说响应（Vue 固定契约，字段名不可变：success/logs/result/error/token_usage）。
 * 成功与失败都携带完整字段；token_usage 永远包含全部固定字段。
 */
public record CreateNovelResponse(
        boolean success,
        List<String> logs,
        ResultData result,
        String error,
        @JsonProperty("token_usage") TokenUsageData tokenUsage) {

    public record ResultData(
            @JsonProperty("completed_chapters") List<String> completedChapters,
            List<CharacterData> characters,
            @JsonProperty("world_settings") List<WorldSettingData> worldSettings,
            @JsonProperty("plot_outline") List<PlotOutlineData> plotOutline,
            @JsonProperty("review_round") int reviewRound,
            @JsonProperty("current_draft") String currentDraft,
            @JsonProperty("review_issues") List<ReviewIssueData> reviewIssues) {

        static ResultData from(NovelCreationResult.ResultPayload payload) {
            return new ResultData(
                    payload.completedChapters(),
                    payload.characters().stream().map(CharacterData::from).toList(),
                    payload.worldSettings().stream().map(WorldSettingData::from).toList(),
                    payload.plotOutline().stream().map(PlotOutlineData::from).toList(),
                    payload.reviewRound(),
                    payload.currentDraft(),
                    payload.reviewIssues().stream().map(ReviewIssueData::from).toList());
        }
    }

    public record CharacterData(
            String name,
            @JsonProperty("role_type") String roleType,
            String appearance,
            String personality,
            String background,
            String ability,
            String motivation,
            List<RelationshipData> relationships) {

        static CharacterData from(CharacterProfile profile) {
            return new CharacterData(
                    profile.name(),
                    profile.roleType(),
                    profile.appearance(),
                    profile.personality(),
                    profile.background(),
                    profile.ability(),
                    profile.motivation(),
                    profile.relationships().stream().map(RelationshipData::from).toList());
        }
    }

    public record RelationshipData(String name, String relation, String dynamic) {

        static RelationshipData from(CharacterRelationship relationship) {
            return new RelationshipData(relationship.name(), relationship.relation(), relationship.dynamic());
        }
    }

    public record WorldSettingData(String category, String key, String content, Integer version) {

        static WorldSettingData from(WorldSetting setting) {
            return new WorldSettingData(setting.category(), setting.key(), setting.content(), setting.version());
        }
    }

    public record PlotOutlineData(
            String id,
            String title,
            String summary,
            List<String> foreshadowing,
            @JsonProperty("characters_involved") List<String> charactersInvolved,
            @JsonProperty("settings_revealed") List<String> settingsRevealed,
            String type,
            @JsonProperty("tension_level") String tensionLevel) {

        static PlotOutlineData from(PlotOutlineItem item) {
            return new PlotOutlineData(
                    item.id(),
                    item.title(),
                    item.summary(),
                    item.foreshadowing(),
                    item.charactersInvolved(),
                    item.settingsRevealed(),
                    item.type(),
                    item.tensionLevel());
        }
    }

    public record ReviewIssueData(
            String severity,
            String category,
            String description,
            @JsonProperty("target_agent") String targetAgent,
            String suggestion) {

        static ReviewIssueData from(ReviewIssue issue) {
            return new ReviewIssueData(
                    issue.severity(),
                    issue.category(),
                    issue.description(),
                    issue.targetAgent(),
                    issue.suggestion());
        }
    }

    public record TokenUsageData(
            @JsonProperty("input_tokens") long inputTokens,
            @JsonProperty("output_tokens") long outputTokens,
            @JsonProperty("total_tokens") long totalTokens,
            @JsonProperty("call_count") long callCount,
            @JsonProperty("cost_yuan") BigDecimal costYuan,
            String model) {

        static TokenUsageData from(TokenUsage usage) {
            return new TokenUsageData(
                    usage.inputTokens(),
                    usage.outputTokens(),
                    usage.totalTokens(),
                    usage.callCount(),
                    usage.costYuan(),
                    usage.model());
        }
    }

    /** 由用例结果转换为 Vue 兼容响应。 */
    public static CreateNovelResponse from(NovelCreationResult result) {
        return new CreateNovelResponse(
                result.success(),
                result.logs(),
                ResultData.from(result.result()),
                result.error(),
                TokenUsageData.from(result.tokenUsage()));
    }

    /** 参数校验失败响应：保持小说响应结构，token_usage 用全零默认值。 */
    public static CreateNovelResponse validationFailure(String message) {
        return failure(message, TokenUsage.zero());
    }

    public static CreateNovelResponse failure(String message, TokenUsage usage) {
        return new CreateNovelResponse(
                false,
                List.of(),
                new ResultData(List.of(), List.of(), List.of(), List.of(), 0, "", List.of()),
                message,
                TokenUsageData.from(usage));
    }
}
