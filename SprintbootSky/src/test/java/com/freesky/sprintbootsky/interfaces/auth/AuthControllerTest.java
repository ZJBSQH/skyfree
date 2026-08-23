package com.freesky.sprintbootsky.interfaces.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freesky.sprintbootsky.application.auth.AuthApplicationService;
import com.freesky.sprintbootsky.application.auth.AuthResult;
import com.freesky.sprintbootsky.application.auth.InvalidCredentialsException;
import com.freesky.sprintbootsky.domain.user.EmailAlreadyExistsException;
import com.freesky.sprintbootsky.domain.user.User;
import com.freesky.sprintbootsky.infrastructure.security.JwtAuthenticationFilter;
import com.freesky.sprintbootsky.infrastructure.security.JwtTokenProvider;
import com.freesky.sprintbootsky.infrastructure.security.SecurityConfig;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AuthController.class, properties = {
        "security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789abcdef"})
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuthApplicationService authApplicationService;

    @Test
    void registerReturnsTokenAndUser() throws Exception {
        when(authApplicationService.register(eq("zheng"), eq("zheng@example.com"), eq("password123")))
                .thenReturn(new AuthResult("jwt-token", 1L, "zheng", "zheng@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zheng\",\"email\":\"zheng@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt-token")))
                .andExpect(jsonPath("$.user.id", is(1)))
                .andExpect(jsonPath("$.user.username", is("zheng")))
                .andExpect(jsonPath("$.user.email", is("zheng@example.com")));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(authApplicationService.register(any(), any(), any()))
                .thenThrow(new EmailAlreadyExistsException("zheng@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zheng\",\"email\":\"zheng@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("该邮箱已被注册")));
    }

    @Test
    void registerWithInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"zheng\",\"email\":\"not-an-email\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("邮箱格式不正确")));
    }

    @Test
    void loginReturnsTokenAndUser() throws Exception {
        when(authApplicationService.login("zheng@example.com", "password123"))
                .thenReturn(new AuthResult("jwt-token", 1L, "zheng", "zheng@example.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"zheng@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("jwt-token")))
                .andExpect(jsonPath("$.user.id", is(1)));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        when(authApplicationService.login(any(), any()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"zheng@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("邮箱或密码错误")));
    }

    @Test
    void meWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("未认证或登录已过期")));
    }

    @Test
    void meWithValidTokenReturnsCurrentUser() throws Exception {
        when(authApplicationService.currentUser(7L))
                .thenReturn(new AuthResult(null, 7L, "zheng", "zheng@example.com"));
        String token = jwtTokenProvider.issue(7L, "zheng@example.com");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(7)))
                .andExpect(jsonPath("$.username", is("zheng")))
                .andExpect(jsonPath("$.email", is("zheng@example.com")));
    }

    @Test
    void meWithInvalidTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    void meReturns404WhenUserDeleted() throws Exception {
        when(authApplicationService.currentUser(anyLong()))
                .thenThrow(new com.freesky.sprintbootsky.domain.user.UserNotFoundException(7L));
        String token = jwtTokenProvider.issue(7L, "zheng@example.com");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}
