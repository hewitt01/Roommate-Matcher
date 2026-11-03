package com.roommatch.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ExplanationGenerator;
import com.roommatch.agent.InsufficientDataException;
import com.roommatch.agent.MatchExplanation;
import com.roommatch.agent.ProfileAccessTool;
import com.roommatch.agent.ToolManager;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import com.roommatch.service.RankingService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** F05 logic in the Facade: reuse fresh results, re-score outdated ones, never re-explain needlessly. */
class AgentControllerExplanationTest {
    private User me;
    private User cand;
    private int scoreCalls;
    private int explainCalls;
    private boolean scorerFails;
    private AgentController controller;

    private static User user(String id) {
        User u = new User(id, 1000, "Toronto", LocalDate.of(2026, 12, 1));
        LifestyleProfile p = new LifestyleProfile();
        LifestyleProfile.DIMENSIONS.forEach(d -> p.set(d, "x"));
        u.attachLifestyleProfile(p);
        return u;
    }

    @BeforeEach
    void setUp() {
        me = user("me");
        cand = user("c");
        scoreCalls = 0;
        explainCalls = 0;
        scorerFails = false;
        CandidateRepository repo = new CandidateRepository() {
            @Override public List<User> getCandidates(User u, HardFilters f) { return List.of(cand); }
            @Override public Optional<User> getProfile(String id) { return Optional.empty(); }
        };
        ToolManager tools = new ToolManager();
        tools.register(new ProfileAccessTool(repo));
        CompatibilityAgent agent = new CompatibilityAgent(tools, (r, c) -> {
            scoreCalls++;
            if (scorerFails) {
                throw new InsufficientDataException("Nothing comparable.");
            }
            return new CompatibilityResult(c, 70, 60, "notes", List.of());
        });
        ExplanationGenerator generator = new ExplanationGenerator(prompt -> {
            explainCalls++;
            return "Explanation #" + explainCalls;
        });
        controller = new AgentController(agent, new RankingService(), () -> null, generator);
        controller.getRankedMatches(me, HardFilters.none());
        scoreCalls = 0;   // ignore the ranking itself
    }

    @Test
    void aFreshResultIsExplainedWithoutRescoring() {
        MatchExplanation e = controller.getExplanation(me, cand);
        assertEquals("Explanation #1", e.text());
        assertFalse(e.rescored());
        assertEquals(0, scoreCalls);
        assertEquals(1, explainCalls);
    }

    @Test
    void theExplanationIsKeptOnTheResultAndReused() {
        MatchExplanation first = controller.getExplanation(me, cand);
        MatchExplanation second = controller.getExplanation(me, cand);
        assertEquals(first.text(), second.text());
        assertEquals(1, explainCalls, "asking again must not call the model again");
        assertSame(first.result(), second.result());
        assertEquals("Explanation #1", first.result().getExplanation().orElseThrow());
    }

    @Test
    void anOutdatedResultIsRescoredBeforeExplaining() {
        me.applyUpdate(Map.of("budget", "1500"));
        MatchExplanation e = controller.getExplanation(me, cand);
        assertTrue(e.rescored());
        assertEquals(1, scoreCalls);
        assertEquals(1, explainCalls);
    }

    @Test
    void afterRescoringTheNewResultStaysFreshSoItIsNotRescoredAgain() {
        me.applyUpdate(Map.of("budget", "1500"));
        controller.getExplanation(me, cand);
        MatchExplanation again = controller.getExplanation(me, cand);
        assertFalse(again.rescored());
        assertEquals(1, scoreCalls, "only the first request after the edit re-scores");
        assertEquals(1, explainCalls, "the explanation of the fresh result is reused");
    }

    @Test
    void anEditInvalidatesAnExplanationThatWasAlreadyGiven() {
        controller.getExplanation(me, cand);
        me.applyUpdate(Map.of("budget", "1500"));
        MatchExplanation e = controller.getExplanation(me, cand);
        assertTrue(e.rescored());
        assertEquals("Explanation #2", e.text(), "a new result needs a new explanation");
    }

    @Test
    void aCandidateThatWasNeverScoredIsScoredOnDemand() {
        User other = user("other");
        MatchExplanation e = controller.getExplanation(me, other);
        assertTrue(e.rescored());
        assertEquals(1, scoreCalls);
    }

    @Test
    void ifRescoringFailsTheExplanationFailsAndNoModelTextIsInvented() {
        me.applyUpdate(Map.of("budget", "1500"));
        scorerFails = true;
        assertThrows(InsufficientDataException.class, () -> controller.getExplanation(me, cand));
        assertEquals(0, explainCalls);
    }

    @Test
    void controllerWithoutAGeneratorSaysExplanationsAreNotConfigured() {
        AgentController plain = new AgentController(
                new CompatibilityAgent(new ToolManager(), (r, c) -> new CompatibilityResult(c, 1, 1)),
                new RankingService());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> plain.getExplanation(me, cand));
        assertTrue(e.getMessage().contains("not configured"));
    }
}
