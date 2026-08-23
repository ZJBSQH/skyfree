package com.freesky.sprintbootsky.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.freesky.sprintbootsky.application.port.out.TokenIssuer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 签发与校验（TokenIssuer 端口的实现）。
 * Application/Domain 只依赖 TokenIssuer 接口，不感知本实现与 JWT 细节。
 */
@Component
public class JwtTokenProvider implements TokenIssuer {

    private final SecretKey signingKey;
    private final long expiresInSeconds;

    public JwtTokenProvider(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiresInSeconds = properties.expiresInSeconds();
    }

    /** 签发含 userId 与 email 声明的 HS256 Token。 */
    @Override
    public String issue(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInSeconds)))
                .signWith(signingKey)
                .compact();
    }

    /** 解析并校验 Token，合法时返回 userId；过期、篡改、格式错误一律返回 empty。 */
    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object userId = claims.get("userId");
            if (userId instanceof Number number) {
                return Optional.of(number.longValue());
            }
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
