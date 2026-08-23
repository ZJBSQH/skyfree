package com.freesky.sprintbootsky.interfaces.auth.dto;

import com.freesky.sprintbootsky.application.auth.AuthResult;

/**
 * 注册/登录响应：token + user。绝不含 password / passwordHash。
 */
public record AuthResponse(String token, UserView user) {

    /** /api/auth/me 返回的用户视图。 */
    public record UserView(Long id, String username, String email) {

        public static UserView from(AuthResult result) {
            return new UserView(result.id(), result.username(), result.email());
        }
    }

    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(result.token(), UserView.from(result));
    }
}
