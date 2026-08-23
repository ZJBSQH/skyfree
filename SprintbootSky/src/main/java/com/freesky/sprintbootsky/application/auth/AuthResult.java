package com.freesky.sprintbootsky.application.auth;

import com.freesky.sprintbootsky.domain.user.User;

/**
 * 认证用例结果。只包含对外安全字段，绝不携带 password / passwordHash。
 * token 仅在注册/登录时存在；查询当前用户时为空。
 */
public record AuthResult(String token, Long id, String username, String email) {

    public static AuthResult withToken(String token, User user) {
        return new AuthResult(token, user.getId(), user.getUsername(), user.getEmail());
    }

    public static AuthResult userOnly(User user) {
        return new AuthResult(null, user.getId(), user.getUsername(), user.getEmail());
    }
}
