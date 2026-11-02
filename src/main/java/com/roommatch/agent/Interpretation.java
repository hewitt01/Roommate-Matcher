package com.roommatch.agent;

import java.util.Map;

/**
 * What the model understood from one answer: short summaries for every lifestyle dimension the
 * answer clearly covered (possibly more than the one asked about), and a follow-up question
 * for when the asked-about dimension was not clearly covered.
 */
public record Interpretation(Map<String, String> dimensions, String followUp) {
    public Interpretation {
        dimensions = Map.copyOf(dimensions);
    }

    public boolean covers(String dimension) {
        return dimensions.containsKey(dimension);
    }
}
