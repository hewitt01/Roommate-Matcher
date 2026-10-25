package com.roommatch.llm;

/** The LLM service failed (network, authentication, rate limit, bad response). Never contains the API key. */
public class LLMException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LLMException(String message) {
        super(message);
    }
}
