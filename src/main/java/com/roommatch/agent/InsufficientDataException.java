package com.roommatch.agent;

/** The two users share no lifestyle dimension with data on both sides, so there is nothing to compare. */
public class InsufficientDataException extends ScoringException {
    private static final long serialVersionUID = 1L;

    public InsufficientDataException(String message) {
        super(message);
    }
}
