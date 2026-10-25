package com.roommatch.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Registry through which the agent finds and calls its tools (tool use). */
public class ToolManager {
    private final Map<String, Tool<?, ?>> tools = new LinkedHashMap<>();

    public void register(Tool<?, ?> tool) {
        String name = tool.name();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A tool needs a name");
        }
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("A tool named '" + name + "' is already registered");
        }
        tools.put(name, tool);
    }

    /** Calls the named tool. The caller states the expected types by assigning the result. */
    @SuppressWarnings("unchecked")
    public <I, O> O invoke(String toolName, I input) {
        Tool<I, O> tool = (Tool<I, O>) tools.get(toolName);
        if (tool == null) {
            throw new UnknownToolException(toolName);
        }
        return tool.execute(input);
    }

    public Set<String> toolNames() {
        return Set.copyOf(tools.keySet());
    }
}
