package com.freesky.sprintbootsky;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class AgentSkyClientTest {

    private MockRestServiceServer server;
    private AgentSkyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AgentSkyClient(builder, "http://127.0.0.1:8765", "service-secret");
    }

    @Test
    void healthCallsAgentSkyHealthEndpoint() {
        server.expect(once(), requestTo("http://127.0.0.1:8765/api/health"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"status\":\"ok\",\"service\":\"agentsky\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.health();

        assertEquals("ok", response.get("status"));
        server.verify();
    }

    @Test
    void createSendsServiceTokenAndIdea() {
        server.expect(once(), requestTo("http://127.0.0.1:8765/api/create"))
                .andExpect(method(POST))
                .andExpect(header("X-AgentSky-Token", "service-secret"))
                .andExpect(content().string(containsString("A city above the clouds")))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.create("A city above the clouds");

        assertEquals(true, response.get("success"));
        server.verify();
    }
}
