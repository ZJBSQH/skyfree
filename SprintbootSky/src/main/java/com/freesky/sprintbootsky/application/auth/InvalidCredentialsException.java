package com.freesky.sprintbootsky.application.auth;

/**
 * 登录凭据无效（邮箱不存在或密码错误），HTTP 层统一映射为 401。
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("邮箱或密码错误");
    }
}
