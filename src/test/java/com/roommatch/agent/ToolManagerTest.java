package com.roommatch.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolManagerTest {
    private static Tool<String, Integer> lengthTool(String name) {
        return new Tool<>() {
            @Override public String name() { return name; }
            @Override public Integer execute(String input) { return input.length(); }
        };
    }

    @Test
    void invokesTheNamedToolWithTypedInputAndOutput() {
        ToolManager tm = new ToolManager();
        tm.register(lengthTool("length"));
        int result = tm.invoke("length", "hello");
        assertEquals(5, result);
    }

    @Test
    void unknownToolIsAClearError() {
        UnknownToolException e = assertThrows(UnknownToolException.class, () -> new ToolManager().invoke("nope", "x"));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void duplicateOrUnnamedToolsAreRejected() {
        ToolManager tm = new ToolManager();
        tm.register(lengthTool("a"));
        assertThrows(IllegalArgumentException.class, () -> tm.register(lengthTool("a")));
        assertThrows(IllegalArgumentException.class, () -> tm.register(lengthTool(" ")));
    }

    @Test
    void listsRegisteredToolNames() {
        ToolManager tm = new ToolManager();
        tm.register(lengthTool("a"));
        tm.register(lengthTool("b"));
        assertEquals(List.of("a", "b"), tm.toolNames().stream().sorted().toList());
    }
}
