package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LifestyleProfileTest {
    @Test
    void startsIncompleteWithAllDimensionsMissing() {
        LifestyleProfile p = new LifestyleProfile();
        assertFalse(p.isComplete());
        assertEquals(LifestyleProfile.DIMENSIONS, p.missingDimensions());
    }

    @Test
    void becomesCompleteOnlyWhenEveryDimensionIsSet() {
        LifestyleProfile p = new LifestyleProfile();
        p.set("cleanliness", "very tidy");
        p.set("noiseTolerance", "low");
        p.set("guestFrequency", "rarely");
        assertFalse(p.isComplete());
        assertEquals(java.util.List.of("sleepSchedule"), p.missingDimensions());
        p.set("sleepSchedule", "early riser");
        assertTrue(p.isComplete());
        assertEquals("very tidy", p.get("cleanliness").orElseThrow());
    }

    @Test
    void rejectsUnknownDimensionAndBlankValue() {
        LifestyleProfile p = new LifestyleProfile();
        assertThrows(InvalidProfileException.class, () -> p.set("pets", "dog"));
        assertThrows(InvalidProfileException.class, () -> p.set("cleanliness", "  "));
        assertThrows(InvalidProfileException.class, () -> p.set("cleanliness", null));
    }

    @Test
    void asMapIsReadOnly() {
        LifestyleProfile p = new LifestyleProfile();
        p.set("cleanliness", "tidy");
        assertThrows(UnsupportedOperationException.class, () -> p.asMap().put("x", "y"));
    }
}
