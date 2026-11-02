package com.roommatch.ui;

/** The person typed something the CLI cannot act on (bad option, missing argument, not logged in). */
class UsageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UsageException(String message) {
        super(message);
    }
}
