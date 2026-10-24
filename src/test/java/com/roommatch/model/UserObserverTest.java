package com.roommatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserObserverTest {
    private User user() {
        return new User("u1", 1200, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void attachedObserverIsToldAboutAnUpdate() {
        User u = user();
        List<User> seen = new ArrayList<>();
        u.attach(seen::add);
        u.applyUpdate(Map.of("budget", "1500"));
        assertEquals(1, seen.size());
        assertSame(u, seen.get(0));
    }

    @Test
    void attachingALifestyleProfileNotifiesToo() {
        User u = user();
        int[] calls = {0};
        u.attach(x -> calls[0]++);
        u.attachLifestyleProfile(new LifestyleProfile());
        assertEquals(1, calls[0]);
    }

    @Test
    void failedUpdateDoesNotNotify() {
        User u = user();
        int[] calls = {0};
        u.attach(x -> calls[0]++);
        assertThrows(InvalidProfileException.class, () -> u.applyUpdate(Map.of("budget", "-1")));
        assertEquals(0, calls[0]);
    }

    @Test
    void allObserversAreNotified() {
        User u = user();
        int[] calls = {0, 0};
        u.attach(x -> calls[0]++);
        u.attach(x -> calls[1]++);
        u.applyUpdate(Map.of("location", "Vaughan"));
        assertArrayEquals(new int[] {1, 1}, calls);
    }

    @Test
    void detachedObserverStopsReceivingNotifications() {
        User u = user();
        int[] calls = {0};
        ProfileObserver o = x -> calls[0]++;
        u.attach(o);
        u.detach(o);
        u.applyUpdate(Map.of("budget", "900"));
        assertEquals(0, calls[0]);
    }

    @Test
    void attachingTheSameObserverTwiceNotifiesOnce() {
        User u = user();
        int[] calls = {0};
        ProfileObserver o = x -> calls[0]++;
        u.attach(o);
        u.attach(o);
        u.applyUpdate(Map.of("budget", "900"));
        assertEquals(1, calls[0]);
    }

    @Test
    void attachRejectsNull() {
        assertThrows(NullPointerException.class, () -> user().attach(null));
    }

    @Test
    void observerCanDetachItselfWhileBeingNotified() {
        User u = user();
        int[] calls = {0};
        ProfileObserver[] self = new ProfileObserver[1];
        self[0] = x -> { calls[0]++; u.detach(self[0]); };
        u.attach(self[0]);
        u.attach(x -> calls[0]++);
        u.applyUpdate(Map.of("budget", "900"));
        assertEquals(2, calls[0], "both observers still called once");
        u.applyUpdate(Map.of("budget", "800"));
        assertEquals(3, calls[0], "self-detached observer no longer called");
    }
}
