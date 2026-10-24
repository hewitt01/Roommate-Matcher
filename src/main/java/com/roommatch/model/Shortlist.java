package com.roommatch.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A user's persistent bookmarks (F09). Plain domain object, no AI involved. */
public class Shortlist {
    public enum AddResult { ADDED, ALREADY_BOOKMARKED, UNAVAILABLE }

    private final List<User> bookmarked = new ArrayList<>();

    public AddResult add(User candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.isActive()) {
            return AddResult.UNAVAILABLE;
        }
        if (bookmarked.contains(candidate)) {
            return AddResult.ALREADY_BOOKMARKED;
        }
        bookmarked.add(candidate);
        return AddResult.ADDED;
    }

    /** @return true if the candidate was bookmarked and has been removed. */
    public boolean remove(User candidate) {
        return bookmarked.remove(candidate);
    }

    public List<User> getBookmarked() {
        return Collections.unmodifiableList(bookmarked);
    }
}
