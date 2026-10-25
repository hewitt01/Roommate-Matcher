package com.roommatch.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {
    private static final Map<String, String> NO_ENV = Map.of();

    private Path envFile(Path dir, String content) throws IOException {
        Path f = dir.resolve(".env");
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    void readsKeyAndModelFromFile(@TempDir Path dir) throws IOException {
        AppConfig c = AppConfig.load(envFile(dir, "LLM_API_KEY=fake-key-123\nLLM_MODEL=my-model\n"), NO_ENV);
        assertEquals("fake-key-123", c.llmApiKey());
        assertEquals("my-model", c.llmModel());
    }

    @Test
    void ignoresCommentsBlankLinesAndStripsQuotes(@TempDir Path dir) throws IOException {
        String text = "# comment\n\nLLM_API_KEY=\"quoted-key\"\n  LLM_MODEL = 'm1' \nnot a setting\n";
        AppConfig c = AppConfig.load(envFile(dir, text), NO_ENV);
        assertEquals("quoted-key", c.llmApiKey());
        assertEquals("m1", c.llmModel());
    }

    @Test
    void handlesWindowsLineEndingsAndByteOrderMark(@TempDir Path dir) throws IOException {
        AppConfig c = AppConfig.load(envFile(dir, "\uFEFFLLM_API_KEY=win-key\r\nLLM_MODEL=m2\r\n"), NO_ENV);
        assertEquals("win-key", c.llmApiKey());
        assertEquals("m2", c.llmModel());
    }

    @Test
    void environmentVariablesOverrideTheFile(@TempDir Path dir) throws IOException {
        AppConfig c = AppConfig.load(envFile(dir, "LLM_API_KEY=from-file\n"), Map.of("LLM_API_KEY", "from-env"));
        assertEquals("from-env", c.llmApiKey());
    }

    @Test
    void worksWithNoFileWhenEnvironmentHasTheKey(@TempDir Path dir) {
        AppConfig c = AppConfig.load(dir.resolve("absent.env"), Map.of("LLM_API_KEY", "k"));
        assertEquals("k", c.llmApiKey());
    }

    @Test
    void modelDefaultsWhenUnset(@TempDir Path dir) throws IOException {
        assertEquals(AppConfig.DEFAULT_MODEL, AppConfig.load(envFile(dir, "LLM_API_KEY=k\n"), NO_ENV).llmModel());
    }

    @Test
    void missingBlankOrPlaceholderKeyGivesAHelpfulError(@TempDir Path dir) throws IOException {
        for (String content : new String[] {"", "LLM_API_KEY=\n", "LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE\n"}) {
            AppConfig c = AppConfig.load(envFile(dir, content), NO_ENV);
            ConfigException e = assertThrows(ConfigException.class, c::llmApiKey);
            assertTrue(e.getMessage().contains(".env"));
        }
    }

    @Test
    void toStringNeverRevealsTheKey() {
        AppConfig c = AppConfig.load(null, Map.of("LLM_API_KEY", "super-secret-value"));
        assertFalse(c.toString().contains("super-secret-value"));
    }
}
