package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultAndMapTest {
    private User candidate() {
        return new User("c1", 900, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void resultKeepsBothDirectionsSeparate() {
        CompatibilityResult r = new CompatibilityResult(candidate(), 80, 35);
        assertEquals(80, r.getScoreAtoB());
        assertEquals(35, r.getScoreBtoA());
        assertTrue(r.getExplanation().isEmpty());
    }

    @Test
    void resultRejectsOutOfRangeScoresAndNullCandidate() {
        User c = candidate();
        assertThrows(IllegalArgumentException.class, () -> new CompatibilityResult(c, -0.1, 50));
        assertThrows(IllegalArgumentException.class, () -> new CompatibilityResult(c, 50, 100.1));
        assertThrows(IllegalArgumentException.class, () -> new CompatibilityResult(c, Double.NaN, 50));
        assertThrows(NullPointerException.class, () -> new CompatibilityResult(null, 50, 50));
    }

    @Test
    void explanationCanBeAttached() {
        CompatibilityResult r = new CompatibilityResult(candidate(), 50, 50);
        r.attachExplanation("You are both night owls.");
        assertEquals("You are both night owls.", r.getExplanation().orElseThrow());
    }

    @Test
    void frictionMapIsImmutableSnapshot() {
        List<String> points = new ArrayList<>(List.of("Guests on weeknights"));
        FrictionMap m = new FrictionMap(points, List.of("No guests after 10pm"));
        points.add("changed later");
        assertEquals(1, m.getFrictionPoints().size());
        assertThrows(UnsupportedOperationException.class, () -> m.getSuggestedRules().add("x"));
    }
}
