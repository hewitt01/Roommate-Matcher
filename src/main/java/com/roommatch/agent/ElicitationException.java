package com.roommatch.agent;

/** The model's reply to an elicitation turn could not be used, even after one retry. */
public class ElicitationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ElicitationException(String message) {
        super(message);
    }
}
