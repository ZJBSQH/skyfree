package com.freesky.sprintbootsky.application.port.out;

/**
 * AgentSky 网关端口（防腐层面向 Application 的稳定接口）。
 * Application 只依赖本接口；AgentSky 的 HTTP 细节、原始 DTO、底层异常都不得越过实现边界。
 */
public interface AgentSkyGateway {

    AgentSkyHealth checkHealth();

    AgentSkyCreateResult create(String idea);
}
