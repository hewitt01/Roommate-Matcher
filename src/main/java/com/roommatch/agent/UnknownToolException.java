package com.roommatch.agent;

/** The agent asked for a tool that is not registered. */
public class UnknownToolException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnknownToolException(String toolName) {
        super("No tool registered with the name '" + toolName + "'");
    }
}
