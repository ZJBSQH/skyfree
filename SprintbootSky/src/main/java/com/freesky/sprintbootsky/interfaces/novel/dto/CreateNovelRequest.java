package com.freesky.sprintbootsky.interfaces.novel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建小说请求。校验失败时由 ApiErrorHandler 返回 Vue 兼容的小说失败响应。
 */
public record CreateNovelRequest(
        @NotBlank(message = "创作灵感不能为空")
        @Size(max = 2000, message = "创作灵感不能超过2000字符")
        String idea) {
}
