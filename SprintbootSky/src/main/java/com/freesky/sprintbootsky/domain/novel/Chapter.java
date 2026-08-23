package com.freesky.sprintbootsky.domain.novel;

/**
 * 章节（NovelProject 聚合内部实体，无独立生命周期）。
 *
 * @param chapterIndex 章节序号，从 1 开始
 * @param content      章节正文
 */
public record Chapter(int chapterIndex, String content) {
}
