package com.freesky.sprintbootsky.interfaces.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freesky.sprintbootsky.application.auth.AuthApplicationService;
import com.freesky.sprintbootsky.interfaces.auth.dto.AuthResponse;
import com.freesky.sprintbootsky.interfaces.auth.dto.LoginRequest;
import com.freesky.sprintbootsky.interfaces.auth.dto.RegisterRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 认证接口：注册、登录公开；/me 需要 JWT。
 * Controller 只做 HTTP 输入输出与参数校验，业务编排全部在 AuthApplicationService。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return AuthResponse.from(authApplicationService.register(
                request.username(), request.email(), request.password()));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.from(authApplicationService.login(
                request.email(), request.password()));
    }

    @GetMapping("/me")
    public AuthResponse.UserView me(@AuthenticationPrincipal Long userId) {
        return AuthResponse.UserView.from(authApplicationService.currentUser(userId));
    }
}
