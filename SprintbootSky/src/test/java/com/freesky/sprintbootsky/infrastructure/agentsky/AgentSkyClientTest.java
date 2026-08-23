package com.freesky.sprintbootsky.infrastructure.agentsky;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AgentSkyClientTest {

    private static final AgentSkyProperties PROPERTIES = new AgentSkyProperties(
            "http://127.0.0.1:8765", "service-secret",
            Duration.ofSeconds(5), Duration.ofMinutes(10), "deepseek-chat");

    private MockRestServiceServer server;
    private AgentSkyClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(PROPERTIES.baseUrl()).build();
        client = new AgentSkyClient(restClient, PROPERTIES);
    }

    @Test
    void healthCallsAgentSkyHealthEndpoint() throws Exception {
        server.expect(once(), requestTo("http://127.0.0.1:8765/api/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"status\":\"ok\",\"service\":\"agentsky\"}", MediaType.APPLICATION_JSON));

        var response = objectMapper.readTree(client.health());

        assertThat(response.path("status").asText()).isEqualTo("ok");
        assertThat(response.path("service").asText()).isEqualTo("agentsky");
        server.verify();
    }

    @Test
    void createSendsServiceTokenAndIdea() throws Exception {
        server.expect(once(), requestTo("http://127.0.0.1:8765/api/create"))
                .andExpect(method(POST))
                .andExpect(header("X-AgentSky-Token", "service-secret"))
                .andExpect(content().string(containsString("A city above the clouds")))
                .andRespond(withSuccess(
                        "{\"success\":true,\"logs\":[],\"result\":{\"completed_chapters\":[\"正文\"],"
                                + "\"characters\":[],\"world_settings\":[],\"plot_outline\":[],\"review_round\":1},"
                                + "\"token_usage\":{\"input_tokens\":0}}",
                        MediaType.APPLICATION_JSON));

        var response = objectMapper.readTree(client.create("A city above the clouds"));

        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("result").path("completed_chapters").get(0).asText()).isEqualTo("正文");
        server.verify();
    }

    @Test
    void createWithoutTokenFailsFast() {
        AgentSkyProperties withoutToken = new AgentSkyProperties(
                "http://127.0.0.1:8765", "",
                Duration.ofSeconds(5), Duration.ofMinutes(10), "deepseek-chat");
        AgentSkyClient clientWithoutToken = new AgentSkyClient(
                RestClient.create("http://127.0.0.1:8765"), withoutToken);

        assertThatThrownBy(() -> clientWithoutToken.create("idea"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AGENTSKY_API_TOKEN");
    }

    @Test
    void serverErrorPropagatesAsRestClientException() {
        server.expect(once(), requestTo("http://127.0.0.1:8765/api/create"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.create("idea"))
                .isInstanceOf(HttpServerErrorException.class)
                .extracting(exception -> ((HttpServerErrorException) exception).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        server.verify();
    }
}
