package com.roommatch.model;

/** Observer role: something that wants to know when a user's profile data changes (F10). */
@FunctionalInterface
public interface ProfileObserver {
    void onProfileChanged(User user);
}
