package com.freesky.sprintbootsky;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api")
public class SkyController {

    private final AgentSkyClient agentSkyClient;

    public SkyController(AgentSkyClient agentSkyClient) {
        this.agentSkyClient = agentSkyClient;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "service", "SprintbootSky");
    }

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "message", "Freesky backend connected",
                "service", "SprintbootSky",
                "agentService", "AgentSky");
    }

    @GetMapping("/agent/health")
    public ResponseEntity<Map<String, Object>> agentHealth() {
        try {
            return ResponseEntity.ok(agentSkyClient.health());
        } catch (RestClientException exception) {
            return agentUnavailable(exception);
        }
    }

    @PostMapping("/novels")
    public ResponseEntity<Map<String, Object>> createNovel(@Valid @RequestBody CreateNovelRequest request) {
        try {
            Map<String, Object> response = agentSkyClient.create(request.idea());
            if (Boolean.FALSE.equals(response.get("success"))) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "success", false,
                        "logs", java.util.List.of(),
                        "result", Map.of(),
                        "error", "We could not complete your novel. Please try again."));
            }
            return ResponseEntity.ok(response);
        } catch (RestClientException | IllegalStateException exception) {
            return agentUnavailable(exception);
        }
    }

    private ResponseEntity<Map<String, Object>> agentUnavailable(Exception exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "unavailable",
                "service", "agentsky",
                "error", "AgentSky is unavailable"));
    }

    public record CreateNovelRequest(
            @NotBlank(message = "idea must not be blank")
            @Size(max = 2000, message = "idea must not exceed 2000 characters")
            String idea) {
    }
}
