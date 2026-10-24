package com.roommatch.model;

/** Subject role: owns a list of observers and tells them when it changes (F10). */
public interface ProfileSubject {
    void attach(ProfileObserver observer);

    void detach(ProfileObserver observer);

    void notifyObservers();
}
