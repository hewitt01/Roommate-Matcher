package com.roommatch.service;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.model.User;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RankingServiceTest {
    private User user(String id) {
        return new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void nothingIsStaleInitially() {
        assertFalse(new RankingService().isStale(user("a")));
    }

    @Test
    void editAfterRankingMakesResultsStale() {
        RankingService rs = new RankingService();
        User u = user("a");
        u.attach(rs);
        rs.markRanked(u);
        u.applyUpdate(Map.of("budget", "1100"));
        assertTrue(rs.isStale(u));
    }

    @Test
    void editBeforeAnyRankingIsNotFlagged() {
        RankingService rs = new RankingService();
        User u = user("a");
        u.attach(rs);
        u.applyUpdate(Map.of("budget", "1100"));
        assertFalse(rs.isStale(u), "no results exist yet, so nothing can be outdated");
    }

    @Test
    void reRankingClearsTheMarker() {
        RankingService rs = new RankingService();
        User u = user("a");
        u.attach(rs);
        rs.markRanked(u);
        u.applyUpdate(Map.of("budget", "1100"));
        rs.markRanked(u);
        assertFalse(rs.isStale(u));
        u.applyUpdate(Map.of("budget", "1200"));
        assertTrue(rs.isStale(u), "later edits flag it again");
    }

    @Test
    void clearStaleOnlyClearsTheMarker() {
        RankingService rs = new RankingService();
        User u = user("a");
        u.attach(rs);
        rs.markRanked(u);
        u.applyUpdate(Map.of("budget", "1100"));
        rs.clearStale(u);
        assertFalse(rs.isStale(u));
    }

    @Test
    void stalenessIsTrackedPerUser() {
        RankingService rs = new RankingService();
        User a = user("a");
        User b = user("b");
        a.attach(rs);
        b.attach(rs);
        rs.markRanked(a);
        rs.markRanked(b);
        a.applyUpdate(Map.of("budget", "1100"));
        assertTrue(rs.isStale(a));
        assertFalse(rs.isStale(b));
    }

    @Test
    void detachedServiceNoLongerHearsAboutChanges() {
        RankingService rs = new RankingService();
        User u = user("a");
        u.attach(rs);
        rs.markRanked(u);
        u.detach(rs);
        u.applyUpdate(Map.of("budget", "1100"));
        assertFalse(rs.isStale(u));
    }
}
