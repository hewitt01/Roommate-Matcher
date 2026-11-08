package com.roommatch.ui;

import com.roommatch.controller.AgentController;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the GUI panels share: the Facade, the repository (used only to look a user up by id),
 * the task runner, and who is logged in. Panels register to hear about login and profile changes.
 */
final class GuiSession {
    final AgentController controller;
    final CandidateRepository repository;
    final GuiTasks tasks;
    private User user;
    private final List<Runnable> loginListeners = new ArrayList<>();
    private final List<Runnable> profileListeners = new ArrayList<>();
    private final List<Runnable> shortlistListeners = new ArrayList<>();

    GuiSession(AgentController controller, CandidateRepository repository, GuiTasks tasks) {
        this.controller = controller;
        this.repository = repository;
        this.tasks = tasks;
    }

    User user() { return user; }

    boolean loggedIn() { return user != null; }

    void onLoginChange(Runnable listener) { loginListeners.add(listener); }

    void onProfileChange(Runnable listener) { profileListeners.add(listener); }

    void onShortlistChange(Runnable listener) { shortlistListeners.add(listener); }

    /** @throws IllegalArgumentException with a readable message if the id is unknown or deactivated */
    void login(String id) {
        String trimmed = id == null ? "" : id.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Enter a user id, for example p001.");
        }
        Optional<User> found = repository.getProfile(trimmed);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("No user with id '" + trimmed + "' on the platform.");
        }
        if (!found.get().isActive()) {
            throw new IllegalArgumentException("The account '" + trimmed + "' is deactivated.");
        }
        logout();
        user = found.get();
        controller.startSession(user);
        loginListeners.forEach(Runnable::run);
    }

    void logout() {
        if (user != null) {
            controller.cancelElicitation(user);
            controller.endSession(user);
            user = null;
            loginListeners.forEach(Runnable::run);
        }
    }

    /** Panels call this after the logged-in user's profile changed. */
    void profileChanged() {
        profileListeners.forEach(Runnable::run);
    }

    void shortlistChanged() {
        shortlistListeners.forEach(Runnable::run);
    }
}
