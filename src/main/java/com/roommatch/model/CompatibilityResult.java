package com.roommatch.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pairwise, direction-specific compatibility judgment for one candidate (F04).
 * scoreAtoB: how well the candidate fits the requesting user's tolerances.
 * scoreBtoA: how well the requesting user fits the candidate's tolerances.
 * Scores are on a 0-100 scale, higher is better.
 * The rationale is the scorer's short reasoning; notAssessedDimensions lists dimensions that
 * were left out because one side had no data (F04 error case).
 */
public class CompatibilityResult {
    public static final double MIN_SCORE = 0.0;
    public static final double MAX_SCORE = 100.0;

    private final User candidate;
    private final double scoreAtoB;
    private final double scoreBtoA;
    private final String rationale;
    private final List<String> notAssessedDimensions;
    private String explanation;

    public CompatibilityResult(User candidate, double scoreAtoB, double scoreBtoA) {
        this(candidate, scoreAtoB, scoreBtoA, "", List.of());
    }

    public CompatibilityResult(User candidate, double scoreAtoB, double scoreBtoA,
                               String rationale, List<String> notAssessedDimensions) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        this.scoreAtoB = checkRange(scoreAtoB, "scoreAtoB");
        this.scoreBtoA = checkRange(scoreBtoA, "scoreBtoA");
        this.rationale = rationale == null ? "" : rationale;
        this.notAssessedDimensions = List.copyOf(notAssessedDimensions);
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
    /**
     * The single number used to rank candidates: the LOWER of the two directional scores.
     * A pair that is great for one person but bad for the other will not live well together,
     * so it must not rank above a pair that is merely good for both.
     */
    public double overallScore() {
        return Math.min(scoreAtoB, scoreBtoA);
    }

    public String getRationale() { return rationale; }
    public List<String> getNotAssessedDimensions() { return notAssessedDimensions; }
    public Optional<String> getExplanation() { return Optional.ofNullable(explanation); }

    /** Stores the F05 explanation on the result it describes. */
    public void attachExplanation(String text) {
        this.explanation = Objects.requireNonNull(text, "text");
    }
}
