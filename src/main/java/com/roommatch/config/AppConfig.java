package com.roommatch.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads settings from a local ".env" file (git-ignored) and from environment variables.
 * Environment variables win over the file. The API key is only ever handed out through
 * llmApiKey() and is never part of any message or toString().
 */
public final class AppConfig {
    public static final String KEY_API_KEY = "LLM_API_KEY";
    public static final String KEY_MODEL = "LLM_MODEL";
    public static final String DEFAULT_MODEL = "claude-haiku-5-5";
    static final String PLACEHOLDER = "YOUR_LOCAL_API_KEY_HERE";

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) {
        this.values = values;
    }

    /** Loads the .env file (if present) and the real environment. */
    public static AppConfig load(Path envFile) {
        return load(envFile, System.getenv());
    }

    /** Same, with an explicit environment so tests do not depend on the machine. */
    public static AppConfig load(Path envFile, Map<String, String> environment) {
        Map<String, String> merged = new HashMap<>();
        if (envFile != null && Files.isRegularFile(envFile)) {
            try {
                merged.putAll(parse(Files.readString(envFile, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new ConfigException("Cannot read " + envFile);
            }
        }
        merged.putAll(environment);
        return new AppConfig(merged);
    }

    static Map<String, String> parse(String text) {
        Map<String, String> result = new HashMap<>();
        if (text.startsWith("\uFEFF")) {          // Windows Notepad sometimes adds a BOM
            text = text.substring(1);
        }
        for (String line : text.split("\\R")) {   // handles both LF and CRLF
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            int eq = s.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = s.substring(0, eq).trim();
            String value = s.substring(eq + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            result.put(key, value);
        }
        return result;
    }

    /** @throws ConfigException if the key is missing, blank, or still the example placeholder */
    public String llmApiKey() {
        String key = values.get(KEY_API_KEY);
        if (key == null || key.isBlank() || key.equals(PLACEHOLDER)) {
            throw new ConfigException(KEY_API_KEY + " is not set. Copy .env.example to .env "
                    + "and put your own key in it.");
        }
        return key;
    }

    public String llmModel() {
        String model = values.get(KEY_MODEL);
        return (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    }

    @Override
    public String toString() {
        return "AppConfig[model=" + llmModel() + ", apiKey=hidden]";
    }
}
