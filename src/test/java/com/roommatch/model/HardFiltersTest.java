package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HardFiltersTest {
    @Test
    void noneHasNoConstraints() {
        HardFilters f = HardFilters.none();
        assertTrue(f.getCity().isEmpty());
        assertTrue(f.getMinBudget().isEmpty());
        assertTrue(f.getMaxBudget().isEmpty());
        assertTrue(f.getEarliestMoveIn().isEmpty());
        assertTrue(f.getLatestMoveIn().isEmpty());
    }

    @Test
    void withersReturnNewFiltersAndLeaveTheOriginalAlone() {
        HardFilters base = HardFilters.none();
        HardFilters f = base.withCity(" Toronto ").withBudgetRange(800.0, 1200.0)
                .withMoveInWindow(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31));
        assertEquals("Toronto", f.getCity().orElseThrow());
        assertEquals(800.0, f.getMinBudget().orElseThrow());
        assertEquals(1200.0, f.getMaxBudget().orElseThrow());
        assertTrue(base.getCity().isEmpty(), "original must be unchanged");
    }

    @Test
    void rejectsContradictoryOrInvalidConstraints() {
        assertThrows(InvalidProfileException.class, () -> HardFilters.none().withCity("  "));
        assertThrows(InvalidProfileException.class, () -> HardFilters.none().withBudgetRange(-1.0, null));
        assertThrows(InvalidProfileException.class, () -> HardFilters.none().withBudgetRange(1500.0, 1000.0));
        assertThrows(InvalidProfileException.class, () -> HardFilters.none()
                .withMoveInWindow(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void openEndedRangesAreAllowed() {
        assertDoesNotThrow(() -> HardFilters.none().withBudgetRange(null, 1000.0));
        assertDoesNotThrow(() -> HardFilters.none().withMoveInWindow(LocalDate.of(2026, 12, 1), null));
    }
}
