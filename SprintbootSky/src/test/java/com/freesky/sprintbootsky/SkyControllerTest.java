package com.freesky.sprintbootsky;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkyController.class)
class SkyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentSkyClient agentSkyClient;

    @Test
    void healthReturnsBackendStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.service", is("SprintbootSky")));
    }

    @Test
    void helloReturnsFrontendConnectivityMessage() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Freesky backend connected")))
                .andExpect(jsonPath("$.agentService", is("AgentSky")));
    }

    @Test
    void agentHealthReturnsAgentSkyStatus() throws Exception {
        when(agentSkyClient.health()).thenReturn(Map.of("status", "ok", "service", "agentsky"));

        mockMvc.perform(get("/api/agent/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.service", is("agentsky")));
    }

    @Test
    void createNovelProxiesCompletedChapterAndTokenUsage() throws Exception {
        when(agentSkyClient.create(any())).thenReturn(Map.of(
                "success", true,
                "logs", List.of("[WriterAgent] Drafted Chapter 1"),
                "result", Map.of("completed_chapters", List.of("Reviewed chapter")),
                "token_usage", Map.of("total_tokens", 120)));

        mockMvc.perform(post("/api/novels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"A city above the clouds\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.result.completed_chapters[0]", is("Reviewed chapter")))
                .andExpect(jsonPath("$.token_usage.total_tokens", is(120)));
    }

    @Test
    void createNovelSanitizesUnsafeAgentSkyFailure() throws Exception {
        when(agentSkyClient.create(any())).thenReturn(Map.of(
                "success", false,
                "error", "Traceback: provider credential leaked",
                "logs", List.of("[ERROR] Traceback: provider credential leaked"),
                "result", Map.of("debug", "unsafe")));

        mockMvc.perform(post("/api/novels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idea\":\"A city above the clouds\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", is("We could not complete your novel. Please try again.")))
                .andExpect(jsonPath("$.logs").isEmpty())
                .andExpect(jsonPath("$.result").isEmpty());
    }
}
