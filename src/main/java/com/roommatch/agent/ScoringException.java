package com.roommatch.agent;

/** A pair could not be scored (for example the model kept returning unusable output). */
public class ScoringException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ScoringException(String message) {
        super(message);
    }
}
