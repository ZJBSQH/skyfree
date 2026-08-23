package com.freesky.sprintbootsky.infrastructure.agentsky;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AgentSky HTTP 客户端装配。RestClient 统一在此构建（含连接/读取超时），
 * 客户端自身不再替换请求工厂，便于测试用 MockRestServiceServer 拦截。
 */
@Configuration
@EnableConfigurationProperties(AgentSkyProperties.class)
public class AgentSkyConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient agentSkyRestClient(AgentSkyProperties properties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }
}
