package com.freesky.sprintbootsky.application.port.out;

/**
 * AgentSky 健康状态（类型化端口模型）。
 *
 * @param status  ok / unavailable
 * @param service 服务名，固定 agentsky
 */
public record AgentSkyHealth(String status, String service) {
}
