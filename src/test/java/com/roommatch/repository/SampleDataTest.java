package com.roommatch.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.model.HardFilters;
import com.roommatch.model.User;
import com.roommatch.platform.HousingPlatformClient;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the committed sample file so a typo in it is caught before a demo. */
class SampleDataTest {
    private final PlatformProfileAdapter adapter =
            new PlatformProfileAdapter(HousingPlatformClient.fromJsonFile(Path.of("data", "platform_users.json")));
    private final User me = new User("me", 1000, "Toronto", LocalDate.of(2026, 12, 1));

    @Test
    void sampleFileLoadsAndHasActiveCandidatesInSeveralCities() {
        List<User> all = adapter.getCandidates(me, HardFilters.none());
        assertEquals(7, all.size(), "8 sample users, 1 of them deactivated");
        assertTrue(all.stream().anyMatch(u -> u.getLocation().equals("Vaughan")));
        assertTrue(all.stream().anyMatch(u -> u.getLocation().equals("Mississauga")));
    }

    @Test
    void sampleFilteringByCityAndBudgetWorks() {
        List<User> torontoUnder1200 = adapter.getCandidates(me,
                HardFilters.none().withCity("Toronto").withBudgetRange(null, 1200.0));
        assertEquals(List.of("p001", "p006", "p008"),
                torontoUnder1200.stream().map(User::getId).sorted().toList());
    }

    @Test
    void sampleDeactivatedUserIsFlaggedInactive() {
        assertFalse(adapter.getProfile("p005").orElseThrow().isActive());
    }
}
