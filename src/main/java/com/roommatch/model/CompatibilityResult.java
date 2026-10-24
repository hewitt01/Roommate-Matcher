package com.roommatch.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Pairwise, direction-specific compatibility judgment for one candidate (F04).
 * scoreAtoB: how well the candidate fits the requesting user's tolerances.
 * scoreBtoA: how well the requesting user fits the candidate's tolerances.
 * Scores are on a 0-100 scale, higher is better.
 */
public class CompatibilityResult {
    public static final double MIN_SCORE = 0.0;
    public static final double MAX_SCORE = 100.0;

    private final User candidate;
    private final double scoreAtoB;
    private final double scoreBtoA;
    private String explanation;

    public CompatibilityResult(User candidate, double scoreAtoB, double scoreBtoA) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        this.scoreAtoB = checkRange(scoreAtoB, "scoreAtoB");
        this.scoreBtoA = checkRange(scoreBtoA, "scoreBtoA");
    }

    private static double checkRange(double score, String name) {
        if (Double.isNaN(score) || score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException(name + " must be between 0 and 100, got " + score);
        }
        return score;
    }

    public User getCandidate() { return candidate; }
    public double getScoreAtoB() { return scoreAtoB; }
    public double getScoreBtoA() { return scoreBtoA; }
    public Optional<String> getExplanation() { return Optional.ofNullable(explanation); }

    /** Stores the F05 explanation on the result it describes. */
    public void attachExplanation(String text) {
        this.explanation = Objects.requireNonNull(text, "text");
    }
}
