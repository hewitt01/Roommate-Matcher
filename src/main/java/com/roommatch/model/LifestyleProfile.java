package com.roommatch.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Structured tolerance profile built by the elicitation agent (F01).
 * Values are short free-text summaries; interpreting them is the LLM's job, not this class's.
 */
public class LifestyleProfile {
    public static final List<String> DIMENSIONS =
            List.of("cleanliness", "noiseTolerance", "guestFrequency", "sleepSchedule");

    private final Map<String, String> answers = new LinkedHashMap<>();

    public void set(String dimension, String value) {
        if (!DIMENSIONS.contains(dimension)) {
            throw new InvalidProfileException("Unknown lifestyle dimension: " + dimension);
        }
        if (value == null || value.isBlank()) {
            throw new InvalidProfileException("Value for '" + dimension + "' must not be blank");
        }
        answers.put(dimension, value.trim());
    }

    public Optional<String> get(String dimension) {
        return Optional.ofNullable(answers.get(dimension));
    }

    /** True once every dimension has a value. */
    public boolean isComplete() {
        return missingDimensions().isEmpty();
    }

    /** Dimensions still unanswered, used to flag gaps before ranking (F01 error case). */
    public List<String> missingDimensions() {
        return DIMENSIONS.stream().filter(d -> !answers.containsKey(d)).toList();
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(answers);
    }
}
