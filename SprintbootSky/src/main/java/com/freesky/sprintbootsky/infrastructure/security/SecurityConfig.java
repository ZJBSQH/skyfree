package com.freesky.sprintbootsky.infrastructure.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Spring Security 配置。
 *
 * <p>接口边界：
 * <ul>
 *   <li>公开接口（无需 JWT）：/api/health、/api/hello、/api/agent/health、/api/auth/register、/api/auth/login；</li>
 *   <li>受保护接口（需要 JWT）：/api/auth/me、/api/novels 及后续新增业务接口。</li>
 * </ul>
 *
 * <p>后端保持 stateless（无 session）；认证失败由自定义入口点统一返回 JSON，
 * 绝不输出 Spring Security 默认 HTML 错误页。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    /** 认证入口点的 JSON 序列化用；独立实例，不依赖容器中的 ObjectMapper Bean。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/health",
                                "/api/hello",
                                "/api/agent/health",
                                "/api/auth/register",
                                "/api/auth/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(jsonAuthenticationEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 认证失败统一返回 JSON 401，避免 Spring Security 默认 HTML 错误页。 */
    private AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JSON.writeValueAsString(
                    Map.of("status", 401, "error", "未认证或登录已过期")));
        };
    }
}
