package com.roommatch.agent;

import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The ranking agent (F03). It plans a multi-step task: check the requester is ready, USE A TOOL
 * to retrieve candidates, score each one through the scoring strategy, handle the ones that
 * cannot be scored, then rank the rest. The agent never knows how scoring is done, only
 * that a CompatibilityScorer does it.
 *
 * Failures to score ONE candidate are recorded and skipped. Failures of the model service itself
 * (LLMException) are not caught here: if the service is down every call would fail, so the
 * whole run stops and the caller reports it.
 */
public class CompatibilityAgent {
    private final ToolManager tools;
    private final CompatibilityScorer scorer;
    private final Map<String, Map<String, CompatibilityResult>> resultsByRequester = new HashMap<>();

    public CompatibilityAgent(ToolManager tools, CompatibilityScorer scorer) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    public RankingOutcome rankCandidates(User requester, HardFilters filters) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(filters, "filters");
        requireReadyRequester(requester);

        List<User> pool = tools.invoke(ProfileAccessTool.NAME, new ProfileQuery(requester, filters));

        List<CompatibilityResult> scored = new ArrayList<>();
        List<RankingOutcome.Skipped> skipped = new ArrayList<>();
        for (User candidate : pool) {
            try {
                scored.add(scorer.score(requester, candidate));
            } catch (InsufficientDataException e) {
                skipped.add(new RankingOutcome.Skipped(candidate, e.getMessage()));
            } catch (ScoringException e) {
                skipped.add(new RankingOutcome.Skipped(candidate, "Could not be scored: " + e.getMessage()));
            }
        }
        List<CompatibilityResult> ranked = rankByOverallScore(scored);

        Map<String, CompatibilityResult> stored = new HashMap<>();
        ranked.forEach(r -> stored.put(r.getCandidate().getId(), r));
        resultsByRequester.put(requester.getId(), stored);

        return new RankingOutcome(ranked, skipped, pool.size());
    }

    /** The result from this requester's most recent ranking, if that candidate was scored. */
    public Optional<CompatibilityResult> getResult(User requester, User candidate) {
        return Optional.ofNullable(resultsByRequester.getOrDefault(requester.getId(), Map.of())
                .get(candidate.getId()));
    }

    private static void requireReadyRequester(User requester) {
        Optional<LifestyleProfile> profile = requester.getLifestyleProfile();
        if (profile.isEmpty()) {
            throw new ProfileIncompleteException(LifestyleProfile.DIMENSIONS);
        }
        if (!profile.get().isComplete()) {
            throw new ProfileIncompleteException(profile.get().missingDimensions());
        }
    }

    /** Best first. Ties go to the pair with the higher average, then to the lower id so order is stable. */
    private static List<CompatibilityResult> rankByOverallScore(List<CompatibilityResult> results) {
        List<CompatibilityResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator
                .comparingDouble(CompatibilityResult::overallScore).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (CompatibilityResult r) -> (r.getScoreAtoB() + r.getScoreBtoA()) / 2).reversed())
                .thenComparing(r -> r.getCandidate().getId()));
        return sorted;
    }
}
