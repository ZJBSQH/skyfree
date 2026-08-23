package com.freesky.sprintbootsky.application.auth;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.freesky.sprintbootsky.application.port.out.TokenIssuer;
import com.freesky.sprintbootsky.domain.user.EmailAlreadyExistsException;
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.domain.user.UserNotFoundException;
import com.freesky.sprintbootsky.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证用例编排：注册、登录、查询当前用户。
 * Application 层不感知 JWT、SecurityContext，Token 由 TokenIssuer 端口签发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    /** 注册：邮箱先规范化（trim + 小写），并发重复注册由数据库唯一约束兜底（映射 409）。 */
    public AuthResult register(String username, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        userRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            throw new EmailAlreadyExistsException(normalizedEmail);
        });

        User user = new User(username.trim(), normalizedEmail, passwordEncoder.encode(rawPassword));
        User saved = userRepository.save(user);
        return AuthResult.withToken(tokenIssuer.issue(saved.getId(), saved.getEmail()), saved);
    }

    /** 登录：统一返回“邮箱或密码错误”，不暴露账号是否存在。 */
    public AuthResult login(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return AuthResult.withToken(tokenIssuer.issue(user.getId(), user.getEmail()), user);
    }

    /** 当前用户：仅依据 JWT 中的 userId 查询，不返回密码哈希。 */
    public AuthResult currentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return AuthResult.userOnly(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
