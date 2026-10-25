package com.roommatch.agent;

import java.util.List;

/** Ranking is blocked until the requesting user's own lifestyle profile is complete (F03 error case). */
public class ProfileIncompleteException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ProfileIncompleteException(List<String> missing) {
        super("Your lifestyle profile is incomplete (missing: " + String.join(", ", missing) + ").");
    }
}
