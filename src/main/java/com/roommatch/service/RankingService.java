package com.roommatch.service;

import com.roommatch.model.ProfileObserver;
import com.roommatch.model.User;
import java.util.HashSet;
import java.util.Set;

/**
 * Concrete Observer (F10): remembers which users have ranking results that are now out of date.
 * It only flags users who actually have results, so someone editing their profile before
 * their first ranking is not prompted to "re-rank".
 */
public class RankingService implements ProfileObserver {
    private final Set<String> rankedUserIds = new HashSet<>();
    private final Set<String> staleUserIds = new HashSet<>();

    /** Observer callback: a user's data changed. */
    @Override
    public void onProfileChanged(User user) {
        flagStale(user);
    }

    /** Marks the user's existing results as outdated. Does nothing if they have no results yet. */
    public void flagStale(User user) {
        if (rankedUserIds.contains(user.getId())) {
            staleUserIds.add(user.getId());
        }
    }

    public boolean isStale(User user) {
        return staleUserIds.contains(user.getId());
    }

    /** Clears the outdated marker (results are fresh again). */
    public void clearStale(User user) {
        staleUserIds.remove(user.getId());
    }

    /** Call after a ranking completes: records that results exist and that they are fresh. */
    public void markRanked(User user) {
        rankedUserIds.add(user.getId());
        clearStale(user);
    }
}
