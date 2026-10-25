package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OverallScoreTest {
    private final User c = new User("c", 900, "Toronto", LocalDate.of(2026, 12, 1));

    @Test
    void overallScoreIsTheLowerDirection() {
        assertEquals(20, new CompatibilityResult(c, 90, 20).overallScore());
        assertEquals(20, new CompatibilityResult(c, 20, 90).overallScore());
        assertEquals(55, new CompatibilityResult(c, 55, 55).overallScore());
    }
}
