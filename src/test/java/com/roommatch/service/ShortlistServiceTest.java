package com.roommatch.service;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.model.HardFilters;
import com.roommatch.model.Shortlist.AddResult;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShortlistServiceTest {
    private final Map<String, User> platform = new HashMap<>();
    private final Map<String, List<String>> saved = new HashMap<>();
    private ShortlistService service;
    private User me;

    private static User user(String id) {
        return new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @BeforeEach
    void setUp() {
        for (String id : List.of("a", "b", "c")) {
            platform.put(id, user(id));
        }
        me = user("me");
        CandidateRepository repo = new CandidateRepository() {
            @Override public List<User> getCandidates(User u, HardFilters f) { return List.of(); }
            @Override public Optional<User> getProfile(String id) { return Optional.ofNullable(platform.get(id)); }
        };
        ShortlistStore store = new ShortlistStore() {
            @Override public List<String> load(String userId) { return saved.getOrDefault(userId, List.of()); }
            @Override public void save(String userId, List<String> ids) { saved.put(userId, new ArrayList<>(ids)); }
        };
        service = new ShortlistService(repo, store);
    }

    @Test
    void addingSavesTheIdAndKeepsOrder() {
        assertEquals(AddResult.ADDED, service.add(me, platform.get("b")));
        assertEquals(AddResult.ADDED, service.add(me, platform.get("a")));
        assertEquals(List.of("b", "a"), saved.get("me"));
        assertEquals(2, me.getShortlist().getBookmarked().size());
    }

    @Test
    void addingTwiceDoesNotDuplicate() {
        service.add(me, platform.get("a"));
        assertEquals(AddResult.ALREADY_BOOKMARKED, service.add(me, platform.get("a")));
        assertEquals(List.of("a"), saved.get("me"));
    }

    @Test
    void aDeactivatedCandidateCannotBeAddedAndIsNotSaved() {
        platform.get("c").deactivate();
        assertEquals(AddResult.UNAVAILABLE, service.add(me, platform.get("c")));
        assertTrue(saved.getOrDefault("me", List.of()).isEmpty());
    }

    @Test
    void removingForgetsTheIdAndTheInMemoryEntry() {
        service.add(me, platform.get("a"));
        service.add(me, platform.get("b"));
        assertTrue(service.remove(me, "a"));
        assertEquals(List.of("b"), saved.get("me"));
        assertEquals(1, me.getShortlist().getBookmarked().size());
        assertFalse(service.remove(me, "a"), "second removal finds nothing");
        assertFalse(service.remove(me, "zzz"));
    }

    @Test
    void restoreLoadsSavedBookmarksIntoAFreshUserObject() {
        saved.put("me", new ArrayList<>(List.of("a", "b")));
        User again = user("me");
        service.restore(again);
        assertEquals(2, again.getShortlist().getBookmarked().size());
    }

    @Test
    void listShowsGoneAndDeactivatedBookmarksAsUnavailableInsteadOfDroppingThem() {
        saved.put("me", new ArrayList<>(List.of("a", "deleted", "c")));
        platform.get("c").deactivate();
        List<ShortlistService.Entry> entries = service.list(me);
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).available());
        assertFalse(entries.get(1).available());
        assertNull(entries.get(1).user());
        assertFalse(entries.get(2).available());
        assertNotNull(entries.get(2).user());
    }

    @Test
    void usersDoNotSeeEachOthersShortlists() {
        service.add(me, platform.get("a"));
        assertTrue(service.list(user("someoneElse")).isEmpty());
    }
}
