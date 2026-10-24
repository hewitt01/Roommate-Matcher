package com.roommatch.model;

/** Thrown when profile data fails validation (F02 error cases). */
public class InvalidProfileException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public InvalidProfileException(String message) {
        super(message);
    }
}
