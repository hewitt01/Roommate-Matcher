package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserTest {
    private User newUser() {
        return new User("u1", 1200, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void constructsWithValidData() {
        User u = newUser();
        assertEquals("u1", u.getId());
        assertEquals(1200, u.getBudget());
        assertTrue(u.isActive());
        assertTrue(u.getLifestyleProfile().isEmpty());
    }

    @Test
    void rejectsNegativeBudgetAndBlankLocationAtConstruction() {
        LocalDate d = LocalDate.of(2026, 12, 1);
        assertThrows(InvalidProfileException.class, () -> new User("u", -1, "Toronto", d));
        assertThrows(InvalidProfileException.class, () -> new User("u", 100, "  ", d));
        assertThrows(InvalidProfileException.class, () -> new User(" ", 100, "Toronto", d));
    }

    @Test
    void applyUpdateChangesValidFields() {
        User u = newUser();
        u.applyUpdate(Map.of("budget", "1500", "location", " Vaughan ", "moveInDate", "2027-01-15"));
        assertEquals(1500, u.getBudget());
        assertEquals("Vaughan", u.getLocation());
        assertEquals(LocalDate.of(2027, 1, 15), u.getMoveInDate());
    }

    @Test
    void invalidUpdateLeavesUserCompletelyUnchanged() {
        User u = newUser();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("budget", "2000");          // valid, processed first
        fields.put("moveInDate", "not-a-date"); // invalid
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(fields));
        assertEquals(1200, u.getBudget(), "valid field must not be applied when another is invalid");
    }

    @Test
    void applyUpdateRejectsBadInput() {
        User u = newUser();
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of("budget", "-5")));
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of("budget", "abc")));
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of("location", "")));
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of("colour", "red")));
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of()));
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(null));
    }

    @Test
    void attachesLifestyleProfile() {
        User u = newUser();
        LifestyleProfile p = new LifestyleProfile();
        u.attachLifestyleProfile(p);
        assertSame(p, u.getLifestyleProfile().orElseThrow());
        assertThrows(NullPointerException.class, () -> u.attachLifestyleProfile(null));
    }

    @Test
    void equalityIsByIdOnly() {
        User a = new User("same", 1, "A", LocalDate.of(2026, 1, 1));
        User b = new User("same", 999, "B", LocalDate.of(2027, 1, 1));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, newUser());
    }
}
