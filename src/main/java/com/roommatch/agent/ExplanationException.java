package com.roommatch.agent;

/** An explanation could not be produced (the model returned nothing usable, or profile data was missing). */
public class ExplanationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExplanationException(String message) {
        super(message);
    }
}
