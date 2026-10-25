package com.roommatch.llm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Talks to a fake Anthropic server on localhost: no network, no key, no cost. */
class ClaudeLLMClientTest {
    private static final String KEY = "fake-test-key-0000";
    private HttpServer server;
    private final AtomicReference<String> seenKey = new AtomicReference<>();
    private final AtomicReference<String> seenVersion = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private ClaudeLLMClient clientAnswering(int status, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            seenKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            seenVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IOException ignored) {
                // client gave up (timeout test)
            }
        });
        server.start();
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages");
        return new ClaudeLLMClient(KEY, "test-model", uri, Duration.ofMillis(delayMillis > 0 ? 300 : 5000), 256);
    }

    private ClaudeLLMClient clientAnswering(int status, String body) throws IOException {
        return clientAnswering(status, body, 0);
    }

    @Test
    void sendsKeyVersionModelAndPromptThenReturnsTheText() throws IOException {
        ClaudeLLMClient c = clientAnswering(200, "{\"content\":[{\"type\":\"text\",\"text\":\"hello there\"}]}");
        assertEquals("hello there", c.generate("Say hi"));
        assertEquals(KEY, seenKey.get());
        assertEquals("2023-06-01", seenVersion.get());
        JsonObject sent = JsonParser.parseString(seenBody.get()).getAsJsonObject();
        assertEquals("test-model", sent.get("model").getAsString());
        assertEquals(256, sent.get("max_tokens").getAsInt());
        JsonObject message = sent.getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals("user", message.get("role").getAsString());
        assertEquals("Say hi", message.get("content").getAsString());
    }

    @Test
    void joinsSeveralTextBlocksAndSkipsOtherTypes() throws IOException {
        ClaudeLLMClient c = clientAnswering(200,
                "{\"content\":[{\"type\":\"text\",\"text\":\"one \"},{\"type\":\"tool_use\"},{\"type\":\"text\",\"text\":\"two\"}]}");
        assertEquals("one two", c.generate("x"));
    }

    @Test
    void promptWithQuotesAndNewlinesSurvivesAsValidJson() throws IOException {
        ClaudeLLMClient c = clientAnswering(200, "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");
        String tricky = "line1\nHe said \"hi\" \\ and {braces}";
        c.generate(tricky);
        JsonObject sent = JsonParser.parseString(seenBody.get()).getAsJsonObject();
        assertEquals(tricky, sent.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void authenticationFailureExplainsWithoutLeakingTheKey() throws IOException {
        ClaudeLLMClient c = clientAnswering(401,
                "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid key " + KEY + "\"}}");
        LLMException e = assertThrows(LLMException.class, () -> c.generate("x"));
        assertTrue(e.getMessage().contains("API key"));
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void rateLimitAndServerErrorsAreDistinguished() throws IOException {
        LLMException limited = assertThrows(LLMException.class, () -> clientAnswering(429, "{}").generate("x"));
        assertTrue(limited.getMessage().contains("rate limiting"));
        server.stop(0);
        LLMException broken = assertThrows(LLMException.class, () -> clientAnswering(500, "oops").generate("x"));
        assertTrue(broken.getMessage().contains("HTTP 500"));
    }

    @Test
    void unexpectedOrEmptyRepliesBecomeLLMException() throws IOException {
        assertThrows(LLMException.class, () -> clientAnswering(200, "not json at all").generate("x"));
        server.stop(0);
        assertThrows(LLMException.class, () -> clientAnswering(200, "{\"content\":[]}").generate("x"));
        server.stop(0);
        assertThrows(LLMException.class, () -> clientAnswering(200, "{\"nothing\":1}").generate("x"));
        server.stop(0);
        assertThrows(LLMException.class, () -> clientAnswering(200, "[1,2,3]").generate("x"));
    }

    @Test
    void slowServerTimesOutCleanly() throws IOException {
        ClaudeLLMClient c = clientAnswering(200, "{\"content\":[{\"type\":\"text\",\"text\":\"late\"}]}", 1500);
        LLMException e = assertThrows(LLMException.class, () -> c.generate("x"));
        assertTrue(e.getMessage().contains("in time"));
    }

    @Test
    void unreachableServerBecomesLLMException() throws IOException {
        ClaudeLLMClient c = clientAnswering(200, "{}");
        URI dead = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages");
        server.stop(0);
        ClaudeLLMClient d = new ClaudeLLMClient(KEY, "m", dead, Duration.ofSeconds(2), 10);
        LLMException e = assertThrows(LLMException.class, () -> d.generate("x"));
        assertFalse(e.getMessage().contains(KEY));
        assertNotNull(c);
    }
}
