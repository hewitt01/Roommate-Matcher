package com.roommatch.llm;

/**
 * The only door to the language model. Everything AI-related in RoomMatch depends on this
 * interface, so the model can be replaced, or faked in tests, without touching agent code.
 */
@FunctionalInterface
public interface LLMClient {
    /** Sends one prompt and returns the model's text reply. */
    String generate(String prompt);
}
