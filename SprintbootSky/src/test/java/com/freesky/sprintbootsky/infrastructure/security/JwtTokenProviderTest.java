package com.freesky.sprintbootsky.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET =
            "unit-test-secret-0123456789abcdef0123456789abcdef0123456789abcdef";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties(SECRET, 3600));

    @Test
    void issueThenParseReturnsUserId() {
        String token = provider.issue(42L, "zheng@example.com");

        assertThat(provider.parseUserId(token)).contains(42L);
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = Jwts.builder()
                .subject("42")
                .claim("userId", 42L)
                .claim("email", "zheng@example.com")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.parseUserId(expired)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = provider.issue(42L, "zheng@example.com");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(provider.parseUserId(tampered)).isEmpty();
    }

    @Test
    void garbageTokenIsRejected() {
        assertThat(provider.parseUserId("not-a-jwt")).isEmpty();
        assertThat(provider.parseUserId("")).isEmpty();
    }

    @Test
    void missingUserIdClaimIsRejected() {
        String token = Jwts.builder()
                .subject("42")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.parseUserId(token)).isEqualTo(Optional.empty());
    }

    @Test
    void blankSecretFailsFast() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new JwtProperties("  ", 3600));
    }
}
