package com.freesky.sprintbootsky.domain.user;

/**
 * 用户不存在。用于 JWT 合法但用户已被删除等场景，HTTP 层统一映射为 404。
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("用户不存在: " + userId);
    }
}
