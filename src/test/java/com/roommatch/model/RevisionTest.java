package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RevisionTest {
    private User user() {
        return new User("u", 1000, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void startsAtZeroAndGoesUpOnEverySuccessfulChange() {
        User u = user();
        assertEquals(0, u.getRevision());
        u.applyUpdate(Map.of("budget", "1100"));
        assertEquals(1, u.getRevision());
        u.attachLifestyleProfile(new LifestyleProfile());
        assertEquals(2, u.getRevision());
    }

    @Test
    void failedUpdatesDoNotChangeTheRevision() {
        User u = user();
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of("budget", "-1")));
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of()));
        assertEquals(0, u.getRevision());
    }

    @Test
    void observersSeeTheNewRevisionWhenTheyAreNotified() {
        User u = user();
        int[] seen = {-1};
        u.attach(changed -> seen[0] = changed.getRevision());
        u.applyUpdate(Map.of("budget", "1100"));
        assertEquals(1, seen[0]);
    }

    @Test
    void resultsStartUnstampedAndRememberTheStampTheyAreGiven() {
        CompatibilityResult r = new CompatibilityResult(user(), 50, 50);
        assertEquals(-1, r.getScoredAtRevision());
        r.stampRevision(3);
        assertEquals(3, r.getScoredAtRevision());
    }
}
