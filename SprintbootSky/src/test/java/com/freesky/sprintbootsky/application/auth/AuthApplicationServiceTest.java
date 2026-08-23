package com.freesky.sprintbootsky.application.auth;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.freesky.sprintbootsky.application.port.out.TokenIssuer;
import com.freesky.sprintbootsky.domain.user.EmailAlreadyExistsException;
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.domain.user.UserNotFoundException;
import com.freesky.sprintbootsky.domain.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuer tokenIssuer;

    @InjectMocks
    private AuthApplicationService authApplicationService;

    private User savedUser() {
        User user = new User("zheng", "zheng@example.com", "hash");
        user.assignId(1L);
        return user;
    }

    @Test
    void registerReturnsTokenAndUser() {
        when(userRepository.findByEmail("zheng@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.assignId(1L);
            return user;
        });
        when(tokenIssuer.issue(1L, "zheng@example.com")).thenReturn("jwt-token");

        AuthResult result = authApplicationService.register("zheng", "Zheng@Example.com", "password123");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("zheng");
        assertThat(result.email()).isEqualTo("zheng@example.com");
        // 邮箱注册前完成 trim + 小写规范化
        verify(userRepository).findByEmail("zheng@example.com");
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void duplicateEmailThrowsConflict() {
        when(userRepository.findByEmail("zheng@example.com")).thenReturn(Optional.of(savedUser()));

        assertThatThrownBy(() -> authApplicationService.register("other", "zheng@example.com", "password123"))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenAndUser() {
        when(userRepository.findByEmail("zheng@example.com")).thenReturn(Optional.of(savedUser()));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(tokenIssuer.issue(1L, "zheng@example.com")).thenReturn("jwt-token");

        AuthResult result = authApplicationService.login("zheng@example.com", "password123");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.email()).isEqualTo("zheng@example.com");
    }

    @Test
    void wrongPasswordThrowsInvalidCredentials() {
        when(userRepository.findByEmail("zheng@example.com")).thenReturn(Optional.of(savedUser()));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authApplicationService.login("zheng@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenIssuer, never()).issue(any(), any());
    }

    @Test
    void unknownEmailThrowsInvalidCredentials() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authApplicationService.login("nobody@example.com", "x"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void currentUserReturnsUserWithoutToken() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser()));

        AuthResult result = authApplicationService.currentUser(1L);

        assertThat(result.token()).isNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("zheng");
        verify(tokenIssuer, never()).issue(any(), any());
    }

    @Test
    void currentUserMissingThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authApplicationService.currentUser(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
