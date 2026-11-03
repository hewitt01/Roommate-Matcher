package com.roommatch.agent;

/** A friction map could not be produced (the model returned nothing usable, or profile data was missing). */
public class FrictionMapException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FrictionMapException(String message) {
        super(message);
    }
}
