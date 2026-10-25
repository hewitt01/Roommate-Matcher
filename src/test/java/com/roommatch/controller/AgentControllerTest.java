package com.roommatch.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ProfileAccessTool;
import com.roommatch.agent.RankingOutcome;
import com.roommatch.agent.ToolManager;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.InvalidProfileException;
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

/** Exercises Facade + Observer + agent together, the whole F10 story. */
class AgentControllerTest {
    private AgentController controller;
    private User me;

    @BeforeEach
    void setUp() {
        User candidate = new User("c1", 900, "Toronto", LocalDate.of(2026, 12, 1));
        CandidateRepository repo = new CandidateRepository() {
            @Override public List<User> getCandidates(User u, HardFilters f) { return List.of(candidate); }
            @Override public Optional<User> getProfile(String id) { return Optional.empty(); }
        };
        ToolManager tools = new ToolManager();
        tools.register(new ProfileAccessTool(repo));
        CompatibilityAgent agent = new CompatibilityAgent(tools, (r, c) -> new CompatibilityResult(c, 70, 60));
        controller = new AgentController(agent, new RankingService());

        me = new User("me", 1000, "Toronto", LocalDate.of(2026, 12, 1));
        LifestyleProfile p = new LifestyleProfile();
        LifestyleProfile.DIMENSIONS.forEach(d -> p.set(d, "x"));
        me.attachLifestyleProfile(p);
    }

    @Test
    void rankingReturnsTheAgentsOutcome() {
        RankingOutcome out = controller.getRankedMatches(me, HardFilters.none());
        assertEquals(1, out.ranked().size());
        assertEquals(60, out.ranked().get(0).overallScore());
    }

    @Test
    void editAfterRankingMarksResultsOutdatedUntilReRanked() {
        controller.startSession(me);
        controller.getRankedMatches(me, HardFilters.none());
        assertFalse(controller.isStale(me));

        controller.updateProfile(me, Map.of("budget", "1500"));
        assertTrue(controller.isStale(me), "edit after ranking must flag the results");

        controller.getRankedMatches(me, HardFilters.none());
        assertFalse(controller.isStale(me), "re-ranking clears the flag");
    }

    @Test
    void editBeforeAnyRankingDoesNotPromptAReRank() {
        controller.startSession(me);
        controller.updateProfile(me, Map.of("budget", "1500"));
        assertFalse(controller.isStale(me));
    }

    @Test
    void invalidEditIsRejectedAndDoesNotFlagAnything() {
        controller.startSession(me);
        controller.getRankedMatches(me, HardFilters.none());
        assertThrows(InvalidProfileException.class, () -> controller.updateProfile(me, Map.of("budget", "-5")));
        assertFalse(controller.isStale(me));
        assertEquals(1000, me.getBudget());
    }

    @Test
    void afterTheSessionEndsEditsAreNoLongerTracked() {
        controller.startSession(me);
        controller.getRankedMatches(me, HardFilters.none());
        controller.endSession(me);
        controller.updateProfile(me, Map.of("budget", "1500"));
        assertFalse(controller.isStale(me));
    }
}
