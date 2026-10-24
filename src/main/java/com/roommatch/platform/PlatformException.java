package com.roommatch.platform;

/** The platform could not be reached or returned unreadable data. */
public class PlatformException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PlatformException(String message) {
        super(message);
    }

    public PlatformException(String message, Throwable cause) {
        super(message, cause);
    }
}
