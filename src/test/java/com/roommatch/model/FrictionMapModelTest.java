package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrictionMapModelTest {
    @Test
    void shortConstructorMeansNothingWasLeftOut() {
        FrictionMap m = new FrictionMap(List.of("a"), List.of("b"));
        assertTrue(m.getNotAssessedDimensions().isEmpty());
    }

    @Test
    void partialMapsRecordWhichDimensionsWereLeftOutAndAreImmutable() {
        List<String> missing = new ArrayList<>(List.of("sleepSchedule"));
        FrictionMap m = new FrictionMap(List.of("a"), List.of("b"), missing);
        missing.add("changed later");
        assertEquals(List.of("sleepSchedule"), m.getNotAssessedDimensions());
        assertThrows(UnsupportedOperationException.class, () -> m.getNotAssessedDimensions().add("x"));
    }

    @Test
    void aResultStartsWithoutAMapAndKeepsTheOneAttached() {
        CompatibilityResult r = new CompatibilityResult(new User("c", 1, "Toronto", LocalDate.of(2026, 12, 1)), 50, 50);
        assertTrue(r.getFrictionMap().isEmpty());
        FrictionMap m = new FrictionMap(List.of("a"), List.of("b"));
        r.attachFrictionMap(m);
        assertSame(m, r.getFrictionMap().orElseThrow());
        assertThrows(NullPointerException.class, () -> r.attachFrictionMap(null));
    }
}
