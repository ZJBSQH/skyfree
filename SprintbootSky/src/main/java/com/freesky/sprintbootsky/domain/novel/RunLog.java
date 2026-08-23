package com.freesky.sprintbootsky.domain.novel;

/**
 * 运行日志条目（NovelProject 聚合内部实体）。
 * message 保留原始整行（含 [WriterAgent] 前缀），保证返回前端时与 Vue 解析逻辑兼容；
 * agent 为解析出的代理名，便于后续按代理过滤。
 */
public record RunLog(int sequence, String agent, String message) {
}
