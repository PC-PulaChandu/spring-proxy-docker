package com.example.simpleproxy;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProxyIntegrationTest {

    // A fake backend that lives only for this test class
    private static final HttpServer STUB = startStub();

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String requestBody = new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int status;
                String response;
                switch (path) {
                    case "/hello" -> {
                        status = 200;
                        response = "stub hello";
                    }
                    case "/echo" -> {
                        status = 200;
                        response = "echo:" + requestBody;
                    }
                    case "/fail" -> {
                        status = 500;
                        response = "boom";
                    }
                    default -> {
                        status = 404;
                        response = "not found";
                    }
                }
                byte[] out = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, out.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(out);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        String stubUrl = "http://127.0.0.1:" + STUB.getAddress().getPort();
        String deadUrl = "http://127.0.0.1:1"; // nothing listens here

        // Own database, so this context never clashes with the contextLoads test
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:integration;MODE=MySQL");

        registry.add("proxy.routes[0].name", () -> "stub-service");
        registry.add("proxy.routes[0].prefix", () -> "/stubA");
        registry.add("proxy.routes[0].urls[0]", () -> stubUrl);

        registry.add("proxy.routes[1].name", () -> "dead-service");
        registry.add("proxy.routes[1].prefix", () -> "/deadA");
        registry.add("proxy.routes[1].urls[0]", () -> deadUrl);

        registry.add("proxy.routes[2].name", () -> "ha-service");
        registry.add("proxy.routes[2].prefix", () -> "/haA");
        registry.add("proxy.routes[2].urls[0]", () -> deadUrl);
        registry.add("proxy.routes[2].urls[1]", () -> stubUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonitoringRecordRepository repository;

    @Test
    void forwardsGetRequestToTheBackend() throws Exception {
        mockMvc.perform(get("/stubA/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("stub hello"));

        assertLogged("/stubA/hello", 200, "stub-service");
    }

    @Test
    void forwardsThePostBody() throws Exception {
        mockMvc.perform(post("/stubA/echo")
                .contentType(MediaType.TEXT_PLAIN)
                .content("hi there"))
                .andExpect(status().isOk())
                .andExpect(content().string("echo:hi there"));
    }

    @Test
    void passesThroughTheBackendErrorStatusAndBody() throws Exception {
        mockMvc.perform(get("/stubA/fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("boom"));

        assertLogged("/stubA/fail", 500, "stub-service");
    }

    @Test
    void passesThroughABackend404() throws Exception {
        mockMvc.perform(get("/stubA/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("not found"));
    }

    @Test
    void returns404ForAnUnknownRoute() throws Exception {
        mockMvc.perform(get("/xyz"))
                .andExpect(status().isNotFound());

        assertLogged("/xyz", 404, "unknown");
    }

    @Test
    void returns502WhenTheBackendIsUnreachable() throws Exception {
        mockMvc.perform(get("/deadA/hello"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(containsString("unreachable")));

        assertLogged("/deadA/hello", 502, "dead-service");
    }

    @Test
    void failsOverToAHealthyInstanceWhenOneIsDown() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/haA/hello"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("stub hello"));
        }
    }

    @Test
    void recordsAreWrittenWithADuration() {
        // Uses a fresh request so the log check below is independent of other tests
        try {
            mockMvc.perform(get("/stubA/hello?check=duration")).andExpect(status().isOk());
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(repository.findAll()).anyMatch(r -> "/stubA/hello".equals(r.getRequestPath())
                        && r.getDurationMs() != null
                        && r.getDurationMs() >= 0));
    }

    // Logging is asynchronous, so wait (up to 5 seconds) instead of checking at
    // once
    private void assertLogged(String path, int status, String service) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(repository.findAll()).anyMatch(r -> path.equals(r.getRequestPath())
                        && r.getHttpStatus() == status
                        && service.equals(r.getTargetService())
                        && r.getDurationMs() != null));
    }
}