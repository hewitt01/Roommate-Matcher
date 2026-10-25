package com.roommatch.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.roommatch.config.AppConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** LLMClient backed by Anthropic's Messages API over plain HTTPS. */
public class ClaudeLLMClient implements LLMClient {
    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final Duration timeout;
    private final int maxTokens;
    private final HttpClient http;

    public ClaudeLLMClient(String apiKey, String model, URI endpoint, Duration timeout, int maxTokens) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.maxTokens = maxTokens;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public static ClaudeLLMClient fromConfig(AppConfig config) {
        return new ClaudeLLMClient(config.llmApiKey(), config.llmModel(),
                DEFAULT_ENDPOINT, Duration.ofSeconds(60), 1024);
    }

    @Override
    public String generate(String prompt) {
        Objects.requireNonNull(prompt, "prompt");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(prompt), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new LLMException("The language model did not answer in time.");
        } catch (IOException e) {
            throw new LLMException("Could not reach the language model service.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("The request to the language model was interrupted.");
        }
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new LLMException(describeFailure(status, response.body()));
        }
        return extractText(response.body());
    }

    private String buildBody(String prompt) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.add("messages", messages);
        return body.toString();
    }

    private String describeFailure(int status, String body) {
        String base = switch (status) {
            case 401, 403 -> "The language model rejected the API key (HTTP " + status + "). Check LLM_API_KEY in .env.";
            case 429 -> "The language model is rate limiting requests (HTTP 429). Try again shortly.";
            default -> status >= 500
                    ? "The language model service had an error (HTTP " + status + ")."
                    : "The language model rejected the request (HTTP " + status + ").";
        };
        String detail = apiErrorMessage(body);
        return detail.isEmpty() ? base : base + " " + detail;
    }

    private String apiErrorMessage(String body) {
        try {
            JsonElement error = JsonParser.parseString(body).getAsJsonObject().get("error");
            if (error != null && error.isJsonObject() && error.getAsJsonObject().has("message")) {
                String msg = error.getAsJsonObject().get("message").getAsString();
                msg = msg.replace(apiKey, "***");
                return msg.length() > 200 ? msg.substring(0, 200) : msg;
            }
        } catch (RuntimeException ignored) {
            // body was not the expected JSON; fall through to no detail
        }
        return "";
    }

    private String extractText(String body) {
        try {
            JsonArray content = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("content");
            StringBuilder text = new StringBuilder();
            if (content != null) {
                for (JsonElement block : content) {
                    JsonObject b = block.getAsJsonObject();
                    if (b.has("type") && "text".equals(b.get("type").getAsString()) && b.has("text")) {
                        text.append(b.get("text").getAsString());
                    }
                }
            }
            if (text.length() == 0) {
                throw new LLMException("The language model returned an empty reply.");
            }
            return text.toString();
        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException e) {
            throw new LLMException("The language model returned a reply in an unexpected format.");
        }
    }
}
