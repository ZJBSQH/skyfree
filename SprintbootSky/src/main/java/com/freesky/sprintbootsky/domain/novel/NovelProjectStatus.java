package com.freesky.sprintbootsky.domain.novel;

/**
 * 小说项目状态机：CREATING（初始短事务落库）→ COMPLETED / FAILED。
 * 状态只能通过 {@link NovelProject} 上的明确方法转换。
 */
public enum NovelProjectStatus {
    CREATING,
    COMPLETED,
    FAILED
}
