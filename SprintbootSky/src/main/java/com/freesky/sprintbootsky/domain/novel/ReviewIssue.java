package com.freesky.sprintbootsky.domain.novel;

/**
 * AgentSky 审核问题。失败响应和带警告结果都会把它返回给 Vue 展示。
 */
public record ReviewIssue(
        String severity,
        String category,
        String description,
        String targetAgent,
        String suggestion) {

    public ReviewIssue {
        severity = severity == null ? "" : severity;
        category = category == null ? "" : category;
        description = description == null ? "" : description;
        targetAgent = targetAgent == null ? "" : targetAgent;
        suggestion = suggestion == null ? "" : suggestion;
    }
}
