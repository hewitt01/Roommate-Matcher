package com.roommatch.model;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Non-negotiable constraints applied before any AI reasoning (F08).
 * Every constraint is optional; an absent constraint matches everyone. Immutable.
 */
public class HardFilters {
    private final String city;
    private final Double minBudget;
    private final Double maxBudget;
    private final LocalDate earliestMoveIn;
    private final LocalDate latestMoveIn;

    private HardFilters(String city, Double minBudget, Double maxBudget,
                        LocalDate earliestMoveIn, LocalDate latestMoveIn) {
        if (city != null && city.isBlank()) {
            throw new InvalidProfileException("City filter must not be blank");
        }
        if ((minBudget != null && minBudget < 0) || (maxBudget != null && maxBudget < 0)) {
            throw new InvalidProfileException("Budget filter must not be negative");
        }
        if (minBudget != null && maxBudget != null && minBudget > maxBudget) {
            throw new InvalidProfileException("Minimum budget is above maximum budget");
        }
        if (earliestMoveIn != null && latestMoveIn != null && earliestMoveIn.isAfter(latestMoveIn)) {
            throw new InvalidProfileException("Earliest move-in date is after latest move-in date");
        }
        this.city = city == null ? null : city.trim();
        this.minBudget = minBudget;
        this.maxBudget = maxBudget;
        this.earliestMoveIn = earliestMoveIn;
        this.latestMoveIn = latestMoveIn;
    }

    /** No constraints at all. */
    public static HardFilters none() {
        return new HardFilters(null, null, null, null, null);
    }

    public HardFilters withCity(String newCity) {
        return new HardFilters(newCity, minBudget, maxBudget, earliestMoveIn, latestMoveIn);
    }

    public HardFilters withBudgetRange(Double newMin, Double newMax) {
        return new HardFilters(city, newMin, newMax, earliestMoveIn, latestMoveIn);
    }

    public HardFilters withMoveInWindow(LocalDate earliest, LocalDate latest) {
        return new HardFilters(city, minBudget, maxBudget, earliest, latest);
    }

    public Optional<String> getCity() { return Optional.ofNullable(city); }
    public Optional<Double> getMinBudget() { return Optional.ofNullable(minBudget); }
    public Optional<Double> getMaxBudget() { return Optional.ofNullable(maxBudget); }
    public Optional<LocalDate> getEarliestMoveIn() { return Optional.ofNullable(earliestMoveIn); }
    public Optional<LocalDate> getLatestMoveIn() { return Optional.ofNullable(latestMoveIn); }
}
