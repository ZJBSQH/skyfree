package com.freesky.sprintbootsky.interfaces.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freesky.sprintbootsky.application.port.out.AgentSkyGateway;
import com.freesky.sprintbootsky.application.port.out.AgentSkyHealth;
import com.freesky.sprintbootsky.infrastructure.security.JwtAuthenticationFilter;
import com.freesky.sprintbootsky.infrastructure.security.JwtTokenProvider;
import com.freesky.sprintbootsky.infrastructure.security.SecurityConfig;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = HealthController.class, properties = {
        "security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789abcdef"})
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentSkyGateway agentSkyGateway;

    @Test
    void healthIsPublicAndReturnsBackendStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.service", is("SprintbootSky")));
    }

    @Test
    void helloIsPublicAndReturnsConnectivityMessage() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Freesky backend connected")))
                .andExpect(jsonPath("$.service", is("SprintbootSky")))
                .andExpect(jsonPath("$.agentService", is("AgentSky")));
    }

    @Test
    void agentHealthIsPublicAndReturnsOk() throws Exception {
        when(agentSkyGateway.checkHealth()).thenReturn(new AgentSkyHealth("ok", "agentsky"));

        mockMvc.perform(get("/api/agent/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.service", is("agentsky")));
    }

    @Test
    void agentHealthIsPublicAndReturnsUnavailable() throws Exception {
        when(agentSkyGateway.checkHealth()).thenReturn(new AgentSkyHealth("unavailable", "agentsky"));

        mockMvc.perform(get("/api/agent/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is("unavailable")))
                .andExpect(jsonPath("$.service", is("agentsky")))
                .andExpect(jsonPath("$.error", is("AgentSky is unavailable")));
    }
}
