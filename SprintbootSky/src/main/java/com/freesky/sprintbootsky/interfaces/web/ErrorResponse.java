package com.freesky.sprintbootsky.interfaces.web;

/**
 * 统一 Web 错误响应体（JSON），认证失败与业务异常共用该结构。
 */
public record ErrorResponse(int status, String error) {
}
