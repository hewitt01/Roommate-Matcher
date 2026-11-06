package com.roommatch.service;

import java.util.List;

/** Where bookmarked candidate ids are kept between runs (F09 is a persistent shortlist). */
public interface ShortlistStore {
    /** The user's bookmarked candidate ids in the order they were added. Empty if none. */
    List<String> load(String userId);

    void save(String userId, List<String> candidateIds);
}
