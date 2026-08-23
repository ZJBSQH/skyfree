package com.freesky.sprintbootsky.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（类型安全绑定 security.jwt.*）。
 * secret 禁止硬编码：缺失时构造即抛异常，应用直接启动失败，绝不使用不安全的默认密钥。
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long expiresInSeconds) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret 未配置（请设置环境变量 JWT_SECRET），拒绝使用不安全的默认密钥启动");
        }
        if (expiresInSeconds <= 0) {
            expiresInSeconds = 86_400;
        }
    }
}
