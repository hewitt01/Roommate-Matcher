package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompatibilityResultRationaleTest {
    private final User c = new User("c", 900, "Toronto", LocalDate.of(2026, 12, 1));

    @Test
    void shortConstructorLeavesRationaleEmptyAndNothingNotAssessed() {
        CompatibilityResult r = new CompatibilityResult(c, 50, 60);
        assertEquals("", r.getRationale());
        assertTrue(r.getNotAssessedDimensions().isEmpty());
    }

    @Test
    void keepsRationaleAndNotAssessedDimensions() {
        List<String> missing = new ArrayList<>(List.of("sleepSchedule"));
        CompatibilityResult r = new CompatibilityResult(c, 80, 30, "Tidiness drives A to B.", missing);
        missing.add("changed later");
        assertEquals("Tidiness drives A to B.", r.getRationale());
        assertEquals(List.of("sleepSchedule"), r.getNotAssessedDimensions());
        assertThrows(UnsupportedOperationException.class, () -> r.getNotAssessedDimensions().add("x"));
    }

    @Test
    void nullRationaleBecomesEmpty() {
        assertEquals("", new CompatibilityResult(c, 1, 1, null, List.of()).getRationale());
    }
}
