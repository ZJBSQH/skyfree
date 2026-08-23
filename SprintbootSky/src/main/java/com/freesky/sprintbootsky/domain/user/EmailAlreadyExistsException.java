package com.freesky.sprintbootsky.domain.user;

/**
 * 邮箱已被注册。由并发重复注册时数据库唯一约束触发，HTTP 层统一映射为 409。
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("邮箱已被注册: " + email);
    }
}
