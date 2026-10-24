package com.roommatch.repository;

import com.roommatch.model.HardFilters;
import com.roommatch.model.InvalidProfileException;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import com.roommatch.platform.HousingPlatformClient;
import com.roommatch.platform.PlatformSearchFilter;
import com.roommatch.platform.PlatformUserRecord;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Adapter: makes the platform's API look like a CandidateRepository. It translates in
 * both directions: RoomMatch HardFilters become a PlatformSearchFilter going out, and
 * PlatformUserRecords become RoomMatch Users (and LifestyleProfiles) coming back.
 */
public class PlatformProfileAdapter implements CandidateRepository {
    /** Platform habit key -> RoomMatch lifestyle dimension. */
    private static final Map<String, String> HABIT_TO_DIMENSION = Map.of(
            "tidiness", "cleanliness",
            "noise", "noiseTolerance",
            "guests", "guestFrequency",
            "sleep", "sleepSchedule");

    private final HousingPlatformClient client;

    public PlatformProfileAdapter(HousingPlatformClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public List<User> getCandidates(User user, HardFilters filters) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(filters, "filters");
        List<User> candidates = new ArrayList<>();
        for (PlatformUserRecord record : client.listCandidates(toPlatformFilter(filters))) {
            if (record.getUserId() != null && record.getUserId().equals(user.getId())) {
                continue;
            }
            toUser(record).ifPresent(candidates::add);
        }
        return candidates;
    }

    @Override
    public Optional<User> getProfile(String id) {
        return client.fetchUserRecord(id).flatMap(PlatformProfileAdapter::toUser);
    }

    private static PlatformSearchFilter toPlatformFilter(HardFilters f) {
        return new PlatformSearchFilter(
                f.getCity().orElse(null),
                f.getMinBudget().orElse(null),
                f.getMaxBudget().orElse(null),
                f.getEarliestMoveIn().map(LocalDate::toString).orElse(null),
                f.getLatestMoveIn().map(LocalDate::toString).orElse(null));
    }

    /** Empty if the record lacks data a RoomMatch User requires (id, city, rent, move-in date). */
    private static Optional<User> toUser(PlatformUserRecord r) {
        if (r.getRentMax() == null || r.getAvailableFrom() == null) {
            return Optional.empty();
        }
        try {
            User user = new User(r.getUserId(), r.getRentMax(), r.getCity(), LocalDate.parse(r.getAvailableFrom()));
            if (!r.isActive()) {
                user.deactivate();
            }
            LifestyleProfile profile = toProfile(r.getHabits());
            if (!profile.asMap().isEmpty()) {
                user.attachLifestyleProfile(profile);
            }
            return Optional.of(user);
        } catch (InvalidProfileException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static LifestyleProfile toProfile(Map<String, String> habits) {
        LifestyleProfile profile = new LifestyleProfile();
        habits.forEach((habit, text) -> {
            String dimension = HABIT_TO_DIMENSION.get(habit);
            if (dimension != null && text != null && !text.isBlank()) {
                profile.set(dimension, text);
            }
        });
        return profile;
    }
}
