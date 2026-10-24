package com.roommatch.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.Shortlist.AddResult;
import com.roommatch.model.User;
import com.roommatch.platform.HousingPlatformClient;
import com.roommatch.platform.PlatformUserRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformProfileAdapterTest {
    private static final Map<String, String> ALL_HABITS = Map.of(
            "tidiness", "very tidy", "noise", "needs quiet", "guests", "rarely", "sleep", "early");

    private static PlatformUserRecord rec(String id, String city, Double rent, String from,
                                          String status, Map<String, String> habits) {
        return new PlatformUserRecord(id, city, rent, from, status, habits);
    }

    private PlatformProfileAdapter adapter(PlatformUserRecord... records) {
        return new PlatformProfileAdapter(new HousingPlatformClient(List.of(records)));
    }

    private User me() {
        return new User("me", 1000, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void translatesPlatformFieldsIntoAUser() {
        User u = adapter(rec("p1", "Toronto", 1100.0, "2026-12-01", "ACTIVE", ALL_HABITS))
                .getCandidates(me(), HardFilters.none()).get(0);
        assertEquals("p1", u.getId());
        assertEquals("Toronto", u.getLocation());          // platform "city"
        assertEquals(1100.0, u.getBudget());               // platform "rent_max"
        assertEquals(LocalDate.of(2026, 12, 1), u.getMoveInDate()); // platform "available_from"
        assertTrue(u.isActive());
    }

    @Test
    void translatesPlatformHabitsIntoLifestyleDimensions() {
        LifestyleProfile p = adapter(rec("p1", "Toronto", 1100.0, "2026-12-01", "ACTIVE", ALL_HABITS))
                .getProfile("p1").orElseThrow().getLifestyleProfile().orElseThrow();
        assertEquals("very tidy", p.get("cleanliness").orElseThrow());
        assertEquals("needs quiet", p.get("noiseTolerance").orElseThrow());
        assertEquals("rarely", p.get("guestFrequency").orElseThrow());
        assertEquals("early", p.get("sleepSchedule").orElseThrow());
        assertTrue(p.isComplete());
    }

    @Test
    void partialHabitsGiveAnIncompleteProfileAndNoHabitsGiveNone() {
        PlatformProfileAdapter a = adapter(
                rec("part", "Toronto", 900.0, "2026-12-01", "ACTIVE", Map.of("tidiness", "tidy", "unknownKey", "x")),
                rec("none", "Toronto", 900.0, "2026-12-01", "ACTIVE", Map.of()));
        LifestyleProfile partial = a.getProfile("part").orElseThrow().getLifestyleProfile().orElseThrow();
        assertFalse(partial.isComplete());
        assertEquals(3, partial.missingDimensions().size());
        assertTrue(a.getProfile("none").orElseThrow().getLifestyleProfile().isEmpty());
    }

    @Test
    void hardFiltersAreForwardedToThePlatform() {
        PlatformProfileAdapter a = adapter(
                rec("t", "Toronto", 1000.0, "2026-12-01", "ACTIVE", Map.of()),
                rec("v", "Vaughan", 1000.0, "2026-12-01", "ACTIVE", Map.of()));
        List<User> result = a.getCandidates(me(), HardFilters.none().withCity("Vaughan"));
        assertEquals(1, result.size());
        assertEquals("v", result.get(0).getId());
    }

    @Test
    void neverReturnsTheRequestingUserAsTheirOwnCandidate() {
        PlatformProfileAdapter a = adapter(
                rec("me", "Toronto", 1000.0, "2026-12-01", "ACTIVE", Map.of()),
                rec("other", "Toronto", 1000.0, "2026-12-01", "ACTIVE", Map.of()));
        List<User> result = a.getCandidates(me(), HardFilters.none());
        assertEquals(1, result.size());
        assertEquals("other", result.get(0).getId());
    }

    @Test
    void emptyPoolIsAnEmptyListNotAnError() {
        assertTrue(adapter().getCandidates(me(), HardFilters.none()).isEmpty());
        PlatformProfileAdapter a = adapter(rec("v", "Vaughan", 1000.0, "2026-12-01", "ACTIVE", Map.of()));
        assertTrue(a.getCandidates(me(), HardFilters.none().withCity("Ottawa")).isEmpty());
    }

    @Test
    void recordsMissingRequiredDataAreSkippedNotFatal() {
        PlatformProfileAdapter a = adapter(
                rec("norent", "Toronto", null, "2026-12-01", "ACTIVE", Map.of()),
                rec("nodate", "Toronto", 900.0, null, "ACTIVE", Map.of()),
                rec("baddate", "Toronto", 900.0, "soon", "ACTIVE", Map.of()),
                rec("nocity", null, 900.0, "2026-12-01", "ACTIVE", Map.of()),
                rec("good", "Toronto", 900.0, "2026-12-01", "ACTIVE", Map.of()));
        List<User> result = a.getCandidates(me(), HardFilters.none());
        assertEquals(1, result.size());
        assertEquals("good", result.get(0).getId());
    }

    @Test
    void deactivatedUserIsNotACandidateButShowsAsInactiveByIdAndCannotBeBookmarked() {
        PlatformProfileAdapter a = adapter(rec("gone", "Toronto", 900.0, "2026-12-01", "DEACTIVATED", Map.of()));
        assertTrue(a.getCandidates(me(), HardFilters.none()).isEmpty());
        User gone = a.getProfile("gone").orElseThrow();
        assertFalse(gone.isActive());
        assertEquals(AddResult.UNAVAILABLE, me().getShortlist().add(gone));
    }

    @Test
    void unknownIdIsEmpty() {
        assertTrue(adapter().getProfile("nobody").isEmpty());
    }
}
