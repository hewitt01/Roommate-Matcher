package com.roommatch.agent;

import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.User;
import java.util.List;

/**
 * Everything one ranking run produced: the ordered results, the candidates that could not be
 * scored (with the reason, never silently dropped), and how many candidates were considered.
 */
public record RankingOutcome(List<CompatibilityResult> ranked, List<Skipped> skipped, int poolSize) {
    public RankingOutcome {
        ranked = List.copyOf(ranked);
        skipped = List.copyOf(skipped);
    }

    /** A candidate that was in the pool but could not be scored. */
    public record Skipped(User candidate, String reason) { }

    public boolean isEmptyPool() {
        return poolSize == 0;
    }
}
