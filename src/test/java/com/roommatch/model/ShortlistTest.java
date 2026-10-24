package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.model.Shortlist.AddResult;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ShortlistTest {
    private User user(String id) {
        return new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void addsNewCandidate() {
        Shortlist s = new Shortlist();
        assertEquals(AddResult.ADDED, s.add(user("a")));
        assertEquals(1, s.getBookmarked().size());
    }

    @Test
    void duplicateIsNotAddedTwice() {
        Shortlist s = new Shortlist();
        s.add(user("a"));
        assertEquals(AddResult.ALREADY_BOOKMARKED, s.add(user("a")));
        assertEquals(1, s.getBookmarked().size());
    }

    @Test
    void deactivatedCandidateIsReportedUnavailable() {
        Shortlist s = new Shortlist();
        User gone = user("gone");
        gone.deactivate();
        assertEquals(AddResult.UNAVAILABLE, s.add(gone));
        assertTrue(s.getBookmarked().isEmpty());
    }

    @Test
    void removeReportsWhetherAnythingWasRemoved() {
        Shortlist s = new Shortlist();
        s.add(user("a"));
        assertTrue(s.remove(user("a")));
        assertFalse(s.remove(user("a")));
    }

    @Test
    void returnedListCannotBeModifiedFromOutside() {
        Shortlist s = new Shortlist();
        s.add(user("a"));
        assertThrows(UnsupportedOperationException.class, () -> s.getBookmarked().clear());
    }

    @Test
    void eachUserOwnsAnIndependentShortlist() {
        User x = user("x");
        User y = user("y");
        x.getShortlist().add(user("c"));
        assertTrue(y.getShortlist().getBookmarked().isEmpty());
    }
}
