package com.roommatch.agent;

/**
 * State pattern, the State role: one mode of the elicitation conversation. Each mode decides
 * what the agent says next and which mode comes after, so ElicitationAgent needs no phase flags
 * or if/else chains.
 */
public interface ElicitationState {
    /**
     * Handles the person's answer and returns the agent's next message. Implementations must call
     * the model FIRST and only change the agent afterwards, so a failed call leaves everything as it was.
     */
    String handle(ElicitationAgent agent, String answer);

    /** What the agent is currently waiting to be answered. */
    String prompt(ElicitationAgent agent);
}
