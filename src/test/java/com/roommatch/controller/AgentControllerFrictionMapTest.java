package com.roommatch.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ExplanationGenerator;
import com.roommatch.agent.FrictionMapGenerator;
import com.roommatch.agent.FrictionMapView;
import com.roommatch.agent.InsufficientDataException;
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

/** F06 logic in the Facade, and its sharing of the re-score rule with F05. */
class AgentControllerFrictionMapTest {
    private User me;
    private User cand;
    private int scoreCalls;
    private int mapCalls;
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
        FrictionMapGenerator maps = new FrictionMapGenerator(prompt -> {
            mapCalls++;
            return "{\"frictionPoints\": [\"point " + mapCalls + "\"], \"suggestedRules\": [\"rule\"]}";
        });
        ExplanationGenerator explanations = new ExplanationGenerator(prompt -> {
            explainCalls++;
            return "Explanation";
        });
        controller = new AgentController(agent, new RankingService(), () -> null, explanations, maps);
        controller.getRankedMatches(me, HardFilters.none());
        scoreCalls = 0;
        mapCalls = 0;
        explainCalls = 0;
    }

    @Test
    void aFreshResultGetsAMapWithoutRescoring() {
        FrictionMapView v = controller.getFrictionMap(me, cand);
        assertEquals(List.of("point 1"), v.map().getFrictionPoints());
        assertFalse(v.rescored());
        assertEquals(0, scoreCalls);
        assertEquals(1, mapCalls);
    }

    @Test
    void theMapIsKeptOnTheResultSoRevisitingCostsNothing() {
        FrictionMapView first = controller.getFrictionMap(me, cand);
        FrictionMapView second = controller.getFrictionMap(me, cand);
        assertSame(first.map(), second.map());
        assertEquals(1, mapCalls);
        assertSame(first.map(), first.result().getFrictionMap().orElseThrow());
    }

    @Test
    void anOutdatedResultIsRescoredAndGetsANewMap() {
        controller.getFrictionMap(me, cand);
        me.applyUpdate(Map.of("budget", "1500"));
        FrictionMapView v = controller.getFrictionMap(me, cand);
        assertTrue(v.rescored());
        assertEquals(1, scoreCalls);
        assertEquals(List.of("point 2"), v.map().getFrictionPoints(), "the old map described old numbers");
    }

    @Test
    void explanationAndMapShareOneRescoreAfterAnEdit() {
        me.applyUpdate(Map.of("budget", "1500"));
        controller.getExplanation(me, cand);
        FrictionMapView v = controller.getFrictionMap(me, cand);
        assertFalse(v.rescored(), "the explanation already re-scored this pair");
        assertEquals(1, scoreCalls);
        assertEquals(1, explainCalls);
        assertEquals(1, mapCalls);
    }

    @Test
    void aCandidateThatWasNeverScoredIsScoredOnDemand() {
        FrictionMapView v = controller.getFrictionMap(me, user("other"));
        assertTrue(v.rescored());
        assertEquals(1, scoreCalls);
    }

    @Test
    void ifRescoringFailsNoMapIsInvented() {
        me.applyUpdate(Map.of("budget", "1500"));
        scorerFails = true;
        assertThrows(InsufficientDataException.class, () -> controller.getFrictionMap(me, cand));
        assertEquals(0, mapCalls);
    }

    @Test
    void controllerWithoutAGeneratorSaysFrictionMapsAreNotConfigured() {
        AgentController plain = new AgentController(
                new CompatibilityAgent(new ToolManager(), (r, c) -> new CompatibilityResult(c, 1, 1)),
                new RankingService());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> plain.getFrictionMap(me, cand));
        assertTrue(e.getMessage().contains("not configured"));
    }
}
