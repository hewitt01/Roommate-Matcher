package com.roommatch.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.llm.LLMException;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompatibilityAgentTest {
    private static User user(String id, boolean completeProfile) {
        User u = new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
        LifestyleProfile p = new LifestyleProfile();
        p.set("cleanliness", "tidy");
        if (completeProfile) {
            p.set("noiseTolerance", "quiet");
            p.set("guestFrequency", "rarely");
            p.set("sleepSchedule", "early");
        }
        u.attachLifestyleProfile(p);
        return u;
    }

    /** Fake repository that records what it was asked. */
    private static class FakeRepo implements CandidateRepository {
        final List<User> pool;
        HardFilters lastFilters;
        FakeRepo(User... pool) { this.pool = List.of(pool); }
        @Override public List<User> getCandidates(User user, HardFilters filters) {
            lastFilters = filters;
            return pool;
        }
        @Override public Optional<User> getProfile(String id) { return Optional.empty(); }
    }

    private static CompatibilityAgent agent(CandidateRepository repo, CompatibilityScorer scorer) {
        ToolManager tm = new ToolManager();
        tm.register(new ProfileAccessTool(repo));
        return new CompatibilityAgent(tm, scorer);
    }

    private static CompatibilityScorer scores(Map<String, double[]> byCandidateId) {
        return (requester, candidate) -> {
            double[] s = byCandidateId.get(candidate.getId());
            return new CompatibilityResult(candidate, s[0], s[1]);
        };
    }

    @Test
    void ranksByTheLowerDirectionSoOneSidedPairsDoNotWin() {
        FakeRepo repo = new FakeRepo(user("lopsided", true), user("balanced", true), user("poor", true));
        CompatibilityAgent a = agent(repo, scores(Map.of(
                "lopsided", new double[] {95, 20},
                "balanced", new double[] {60, 60},
                "poor", new double[] {30, 30})));
        RankingOutcome out = a.rankCandidates(user("me", true), HardFilters.none());
        assertEquals(List.of("balanced", "poor", "lopsided"),
                out.ranked().stream().map(r -> r.getCandidate().getId()).toList());
    }

    @Test
    void tiesGoToTheHigherAverageThenTheLowerId() {
        FakeRepo repo = new FakeRepo(user("b", true), user("a", true), user("c", true));
        CompatibilityAgent agent = agent(repo, scores(Map.of(
                "a", new double[] {50, 50},
                "b", new double[] {50, 50},
                "c", new double[] {50, 90})));
        List<String> order = agent.rankCandidates(user("me", true), HardFilters.none())
                .ranked().stream().map(r -> r.getCandidate().getId()).toList();
        assertEquals(List.of("c", "a", "b"), order);
    }

    @Test
    void passesTheHardFiltersThroughTheTool() {
        FakeRepo repo = new FakeRepo();
        HardFilters f = HardFilters.none().withCity("Toronto");
        agent(repo, scores(Map.of())).rankCandidates(user("me", true), f);
        assertSame(f, repo.lastFilters);
    }

    @Test
    void emptyPoolGivesAnEmptyOutcomeNotAnError() {
        RankingOutcome out = agent(new FakeRepo(), scores(Map.of())).rankCandidates(user("me", true), HardFilters.none());
        assertTrue(out.isEmptyPool());
        assertTrue(out.ranked().isEmpty());
    }

    @Test
    void candidatesThatCannotBeScoredAreSkippedWithAReasonAndTheRestStillRank() {
        FakeRepo repo = new FakeRepo(user("good", true), user("nodata", true), user("broken", true));
        CompatibilityScorer scorer = (requester, candidate) -> switch (candidate.getId()) {
            case "nodata" -> throw new InsufficientDataException("No shared dimensions.");
            case "broken" -> throw new ScoringException("Model returned junk.");
            default -> new CompatibilityResult(candidate, 70, 70);
        };
        RankingOutcome out = agent(repo, scorer).rankCandidates(user("me", true), HardFilters.none());
        assertEquals(1, out.ranked().size());
        assertEquals(2, out.skipped().size());
        assertEquals(3, out.poolSize());
        assertEquals("No shared dimensions.", out.skipped().get(0).reason());
        assertTrue(out.skipped().get(1).reason().contains("Model returned junk."));
    }

    @Test
    void aFailingModelServiceStopsTheWholeRunInsteadOfSkippingEveryone() {
        FakeRepo repo = new FakeRepo(user("a", true), user("b", true));
        CompatibilityScorer down = (r, c) -> { throw new LLMException("service down"); };
        assertThrows(LLMException.class, () -> agent(repo, down).rankCandidates(user("me", true), HardFilters.none()));
    }

    @Test
    void incompleteOrMissingRequesterProfileBlocksRankingBeforeAnyWork() {
        List<String> calls = new ArrayList<>();
        FakeRepo repo = new FakeRepo(user("a", true)) {
            @Override public List<User> getCandidates(User u, HardFilters f) { calls.add("fetch"); return pool; }
        };
        CompatibilityScorer scorer = (r, c) -> { calls.add("score"); return new CompatibilityResult(c, 1, 1); };
        CompatibilityAgent a = agent(repo, scorer);

        ProfileIncompleteException partial = assertThrows(ProfileIncompleteException.class,
                () -> a.rankCandidates(user("me", false), HardFilters.none()));
        assertTrue(partial.getMessage().contains("noiseTolerance"));

        User noProfile = new User("np", 1, "Toronto", LocalDate.of(2026, 12, 1));
        assertThrows(ProfileIncompleteException.class, () -> a.rankCandidates(noProfile, HardFilters.none()));
        assertTrue(calls.isEmpty(), "no retrieval or scoring should happen");
    }

    @Test
    void remembersResultsPerRequesterAndReplacesThemOnReRank() {
        User me = user("me", true);
        User cand = user("c", true);
        double[] first = {40, 40};
        CompatibilityScorer scorer = (r, c) -> new CompatibilityResult(c, first[0], first[1]);
        CompatibilityAgent a = agent(new FakeRepo(cand), scorer);

        assertTrue(a.getResult(me, cand).isEmpty(), "nothing before the first ranking");
        a.rankCandidates(me, HardFilters.none());
        assertEquals(40, a.getResult(me, cand).orElseThrow().getScoreAtoB());

        first[0] = 80;
        first[1] = 80;
        a.rankCandidates(me, HardFilters.none());
        assertEquals(80, a.getResult(me, cand).orElseThrow().getScoreAtoB());
        assertTrue(a.getResult(user("someoneElse", true), cand).isEmpty());
    }
}
