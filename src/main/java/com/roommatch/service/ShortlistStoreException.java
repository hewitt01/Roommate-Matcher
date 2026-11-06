package com.roommatch.service;

/** The saved shortlists could not be read or written. */
public class ShortlistStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ShortlistStoreException(String message) {
        super(message);
    }

    public ShortlistStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
