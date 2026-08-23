package com.freesky.sprintbootsky.interfaces.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 30, message = "用户名长度需在 2-30 个字符之间")
        String username,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255 个字符")
        String email,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度需在 6-72 个字符之间")
        String password) {
}
