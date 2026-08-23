package com.freesky.sprintbootsky.interfaces.health;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freesky.sprintbootsky.application.port.out.AgentSkyGateway;
import com.freesky.sprintbootsky.application.port.out.AgentSkyHealth;

import lombok.RequiredArgsConstructor;

/**
 * 公开健康检查接口（均无需 JWT）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final AgentSkyGateway agentSkyGateway;

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
    public ResponseEntity<?> agentHealth() {
        AgentSkyHealth health = agentSkyGateway.checkHealth();
        if ("ok".equals(health.status())) {
            return ResponseEntity.ok(health);
        }
        return ResponseEntity.status(503).body(Map.of(
                "status", "unavailable",
                "service", "agentsky",
                "error", "AgentSky is unavailable"));
    }
}
