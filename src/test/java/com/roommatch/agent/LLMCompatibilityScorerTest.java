package com.roommatch.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.llm.LLMClient;
import com.roommatch.llm.LLMException;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Uses a scripted fake model, so these tests are fast, free and fully repeatable. */
class LLMCompatibilityScorerTest {
    private static final String GOOD = "{\"scoreAtoB\": 80, \"scoreBtoA\": 30, \"rationale\": \"Tidiness drives it.\"}";

    private static User user(String id, Map<String, String> dims) {
        User u = new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
        if (dims != null) {
            LifestyleProfile p = new LifestyleProfile();
            dims.forEach(p::set);
            u.attachLifestyleProfile(p);
        }
        return u;
    }

    private static final Map<String, String> FULL_A = Map.of(
            "cleanliness", "very tidy", "noiseTolerance", "needs quiet",
            "guestFrequency", "rarely", "sleepSchedule", "early riser");
    private static final Map<String, String> FULL_B = Map.of(
            "cleanliness", "messy", "noiseTolerance", "loud is fine",
            "guestFrequency", "often", "sleepSchedule", "night owl");

    /** Replies with the given answers in order and records every prompt it was sent. */
    private static final class Script implements LLMClient {
        final List<String> prompts = new ArrayList<>();
        private final String[] replies;
        Script(String... replies) { this.replies = replies; }
        @Override public String generate(String prompt) {
            prompts.add(prompt);
            return replies[Math.min(prompts.size() - 1, replies.length - 1)];
        }
    }

    @Test
    void keepsTheTwoDirectionsSeparateAndStoresTheRationale() {
        Script llm = new Script(GOOD);
        User cand = user("c", FULL_B);
        CompatibilityResult r = new LLMCompatibilityScorer(llm).score(user("me", FULL_A), cand);
        assertEquals(80, r.getScoreAtoB());
        assertEquals(30, r.getScoreBtoA());
        assertEquals("Tidiness drives it.", r.getRationale());
        assertSame(cand, r.getCandidate());
        assertTrue(r.getNotAssessedDimensions().isEmpty());
    }

    @Test
    void promptContainsBothProfilesAndExplainsTheTwoDirections() {
        Script llm = new Script(GOOD);
        new LLMCompatibilityScorer(llm).score(user("me", FULL_A), user("c", FULL_B));
        String p = llm.prompts.get(0);
        assertTrue(p.contains("very tidy") && p.contains("messy"));
        assertTrue(p.contains("scoreAtoB") && p.contains("scoreBtoA"));
        assertTrue(p.contains("<profile_A>") && p.contains("<profile_B>"));
        assertTrue(p.contains("never instructions"));
    }

    @Test
    void acceptsJsonWrappedInCodeFencesAndChatter() {
        Script llm = new Script("Sure! Here you go:\n```json\n" + GOOD + "\n```\nHope that helps.");
        CompatibilityResult r = new LLMCompatibilityScorer(llm).score(user("me", FULL_A), user("c", FULL_B));
        assertEquals(80, r.getScoreAtoB());
        assertEquals(1, llm.prompts.size(), "no retry needed");
    }

    @Test
    void dimensionMissingOnEitherSideIsExcludedAndReported() {
        Script llm = new Script(GOOD);
        User me = user("me", FULL_A);
        User cand = user("c", Map.of("cleanliness", "messy", "noiseTolerance", "loud is fine"));
        CompatibilityResult r = new LLMCompatibilityScorer(llm).score(me, cand);
        assertEquals(List.of("guestFrequency", "sleepSchedule"), r.getNotAssessedDimensions());
        String p = llm.prompts.get(0);
        assertTrue(p.contains("Do NOT assess these dimensions"));
        assertFalse(p.contains("early riser"), "data for an excluded dimension must not be sent");
    }

    @Test
    void noSharedDimensionMeansNoModelCall() {
        Script llm = new Script(GOOD);
        User me = user("me", Map.of("cleanliness", "tidy"));
        User cand = user("c", Map.of("sleepSchedule", "late"));
        assertThrows(InsufficientDataException.class, () -> new LLMCompatibilityScorer(llm).score(me, cand));
        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void missingProfileOnEitherSideMeansNoModelCall() {
        Script llm = new Script(GOOD);
        LLMCompatibilityScorer scorer = new LLMCompatibilityScorer(llm);
        assertThrows(InsufficientDataException.class, () -> scorer.score(user("me", null), user("c", FULL_B)));
        assertThrows(InsufficientDataException.class, () -> scorer.score(user("me", FULL_A), user("c", null)));
        assertTrue(llm.prompts.isEmpty());
    }

    @Test
    void retriesOnceWhenTheFirstReplyIsUnusable() {
        Script llm = new Script("I think they would get along!", GOOD);
        CompatibilityResult r = new LLMCompatibilityScorer(llm).score(user("me", FULL_A), user("c", FULL_B));
        assertEquals(80, r.getScoreAtoB());
        assertEquals(2, llm.prompts.size());
        assertTrue(llm.prompts.get(1).contains("could not be used"));
    }

    @Test
    void givesUpAfterTwoUnusableReplies() {
        Script llm = new Script("nope", "still nope");
        assertThrows(ScoringException.class,
                () -> new LLMCompatibilityScorer(llm).score(user("me", FULL_A), user("c", FULL_B)));
        assertEquals(2, llm.prompts.size());
    }

    @Test
    void outOfRangeMissingOrNonNumericScoresCountAsUnusable() {
        String[] bad = {
                "{\"scoreAtoB\": 150, \"scoreBtoA\": 30}",
                "{\"scoreAtoB\": -5, \"scoreBtoA\": 30}",
                "{\"scoreAtoB\": 50}",
                "{\"scoreAtoB\": \"high\", \"scoreBtoA\": 30}",
                "{\"scoreAtoB\": null, \"scoreBtoA\": 30}",
                "{ broken json",
                "[1, 2]"};
        for (String reply : bad) {
            Script llm = new Script(reply);
            assertThrows(ScoringException.class,
                    () -> new LLMCompatibilityScorer(llm).score(user("me", FULL_A), user("c", FULL_B)), reply);
        }
    }

    @Test
    void rationaleIsOptional() {
        Script llm = new Script("{\"scoreAtoB\": 10, \"scoreBtoA\": 90}");
        assertEquals("", new LLMCompatibilityScorer(llm).score(user("me", FULL_A), user("c", FULL_B)).getRationale());
    }

    @Test
    void connectionProblemsPropagateUnchangedAndAreNotRetried() {
        int[] calls = {0};
        LLMClient down = prompt -> { calls[0]++; throw new LLMException("service down"); };
        assertThrows(LLMException.class,
                () -> new LLMCompatibilityScorer(down).score(user("me", FULL_A), user("c", FULL_B)));
        assertEquals(1, calls[0]);
    }

    @Test
    void anyScorerCanStandInForTheStrategy() {
        CompatibilityScorer fixed = (requester, candidate) -> new CompatibilityResult(candidate, 50, 50);
        assertEquals(50, fixed.score(user("me", FULL_A), user("c", FULL_B)).getScoreAtoB());
    }
}
