package com.roommatch.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A person on the housing platform, either the requesting user or a candidate (F02).
 * Owns its own validation so an invalid User can never exist, and owns its Shortlist (F09).
 * Acts as the Subject of the Observer pattern: observers are told after every successful change (F10).
 */
public class User implements ProfileSubject {
    public static final String FIELD_BUDGET = "budget";
    public static final String FIELD_LOCATION = "location";
    public static final String FIELD_MOVE_IN_DATE = "moveInDate";

    private final String id;
    private double budget;
    private String location;
    private LocalDate moveInDate;
    private boolean active = true;
    private LifestyleProfile lifestyleProfile;
    private final Shortlist shortlist = new Shortlist();
    private final List<ProfileObserver> observers = new ArrayList<>();

    public User(String id, double budget, String location, LocalDate moveInDate) {
        this.id = requireNonBlank(id, "id");
        this.budget = checkBudget(budget);
        this.location = requireNonBlank(location, FIELD_LOCATION);
        this.moveInDate = Objects.requireNonNull(moveInDate, FIELD_MOVE_IN_DATE);
    }

    /**
     * Validates every field first, then applies them all, so a bad value never leaves
     * the user half-updated. Accepted keys: budget, location, moveInDate (ISO yyyy-MM-dd).
     */
    public void applyUpdate(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new InvalidProfileException("No fields to update");
        }
        Double newBudget = null;
        String newLocation = null;
        LocalDate newMoveIn = null;

        for (Map.Entry<String, String> e : fields.entrySet()) {
            String value = e.getValue();
            switch (e.getKey()) {
                case FIELD_BUDGET -> newBudget = parseBudget(value);
                case FIELD_LOCATION -> newLocation = requireNonBlank(value, FIELD_LOCATION);
                case FIELD_MOVE_IN_DATE -> newMoveIn = parseDate(value);
                default -> throw new InvalidProfileException("Unknown field: " + e.getKey());
            }
        }
        if (newBudget != null) budget = newBudget;
        if (newLocation != null) location = newLocation.trim();
        if (newMoveIn != null) moveInDate = newMoveIn;
        notifyObservers();
    }

    public void attachLifestyleProfile(LifestyleProfile profile) {
        this.lifestyleProfile = Objects.requireNonNull(profile, "profile");
        notifyObservers();
    }

    /** Empty until elicitation (F01) has attached a profile. */
    public Optional<LifestyleProfile> getLifestyleProfile() {
        return Optional.ofNullable(lifestyleProfile);
    }

    /** Registers an observer. Attaching the same observer twice has no extra effect. */
    @Override
    public void attach(ProfileObserver observer) {
        Objects.requireNonNull(observer, "observer");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void detach(ProfileObserver observer) {
        observers.remove(observer);
    }

    /** Iterates over a copy so an observer may detach itself while being notified. */
    @Override
    public void notifyObservers() {
        for (ProfileObserver observer : new ArrayList<>(observers)) {
            observer.onProfileChanged(this);
        }
    }

    public Shortlist getShortlist() { return shortlist; }
    public String getId() { return id; }
    public double getBudget() { return budget; }
    public String getLocation() { return location; }
    public LocalDate getMoveInDate() { return moveInDate; }
    public boolean isActive() { return active; }

    /** Marks the user as deleted/deactivated on the platform (F09 "no longer available"). */
    public void deactivate() { this.active = false; }

    private static double parseBudget(String value) {
        try {
            return checkBudget(Double.parseDouble(value == null ? "" : value.trim()));
        } catch (NumberFormatException ex) {
            throw new InvalidProfileException("Budget is not a number: " + value);
        }
    }

    private static double checkBudget(double budget) {
        if (Double.isNaN(budget) || Double.isInfinite(budget) || budget < 0) {
            throw new InvalidProfileException("Budget must be a non-negative number, got " + budget);
        }
        return budget;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException ex) {
            throw new InvalidProfileException("Move-in date must be yyyy-MM-dd, got: " + value);
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidProfileException(field + " must not be blank");
        }
        return value;
    }

    /** Identity is the platform id: two User objects with the same id are the same person. */
    @Override public boolean equals(Object o) {
        return o instanceof User other && id.equals(other.id);
    }

    @Override public int hashCode() { return id.hashCode(); }
}
