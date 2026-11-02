package com.roommatch.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.ElicitationAgent;
import com.roommatch.agent.ProfileAccessTool;
import com.roommatch.agent.ToolManager;
import com.roommatch.llm.LLMClient;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import com.roommatch.service.RankingService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentControllerElicitationTest {
    private static final String ALL_FOUR = "{\"dimensions\": {\"cleanliness\": \"tidy\", \"noiseTolerance\": \"quiet\","
            + " \"guestFrequency\": \"rarely\", \"sleepSchedule\": \"early\"}, \"followUp\": \"\"}";

    private AgentController controller;
    private User alice;
    private User bob;

    private static CompatibilityAgent agent() {
        CandidateRepository repo = new CandidateRepository() {
            @Override public List<User> getCandidates(User u, HardFilters f) { return List.of(); }
            @Override public Optional<User> getProfile(String id) { return Optional.empty(); }
        };
        ToolManager tools = new ToolManager();
        tools.register(new ProfileAccessTool(repo));
        return new CompatibilityAgent(tools, (r, c) -> new CompatibilityResult(c, 50, 50));
    }

    @BeforeEach
    void setUp() {
        LLMClient llm = prompt -> ALL_FOUR;
        controller = new AgentController(agent(), new RankingService(), () -> new ElicitationAgent(llm));
        alice = new User("alice", 1000, "Toronto", LocalDate.of(2026, 12, 1));
        bob = new User("bob", 1000, "Toronto", LocalDate.of(2026, 12, 1));
    }

    @Test
    void conversationRunsFromFirstQuestionToSavedProfile() {
        assertFalse(controller.isElicitating(alice));
        assertTrue(controller.runElicitation(alice).contains("tidy"));
        assertTrue(controller.isElicitating(alice));

        controller.handleResponse(alice, "all of it");
        assertTrue(controller.isElicitationComplete(alice));
        assertEquals(4, controller.elicitationDraft(alice).size());
        assertTrue(controller.elicitationMissing(alice).isEmpty());

        LifestyleProfile saved = controller.confirmElicitation(alice);
        assertTrue(saved.isComplete());
        assertSame(saved, alice.getLifestyleProfile().orElseThrow());
        assertFalse(controller.isElicitating(alice), "conversation ends after saving");
    }

    @Test
    void savingAProfileAfterRankingMarksTheResultsOutdated() {
        controller.startSession(alice);
        alice.attachLifestyleProfile(completeProfile());
        controller.getRankedMatches(alice, HardFilters.none());
        assertFalse(controller.isStale(alice));

        controller.runElicitation(alice);
        controller.handleResponse(alice, "all of it");
        controller.confirmElicitation(alice);
        assertTrue(controller.isStale(alice));
    }

    @Test
    void cancelDiscardsWithoutTouchingTheUser() {
        controller.runElicitation(alice);
        controller.handleResponse(alice, "all of it");
        controller.cancelElicitation(alice);
        assertFalse(controller.isElicitating(alice));
        assertTrue(alice.getLifestyleProfile().isEmpty());
        assertDoesNotThrow(() -> controller.cancelElicitation(alice), "cancelling nothing is harmless");
    }

    @Test
    void restartingThrowsAwayTheUnfinishedConversation() {
        controller.runElicitation(alice);
        controller.handleResponse(alice, "all of it");
        assertEquals(4, controller.elicitationDraft(alice).size());
        controller.runElicitation(alice);
        assertTrue(controller.elicitationDraft(alice).isEmpty());
    }

    @Test
    void eachUserHasTheirOwnConversation() {
        controller.runElicitation(alice);
        controller.runElicitation(bob);
        controller.handleResponse(alice, "all of it");
        assertEquals(4, controller.elicitationDraft(alice).size());
        assertTrue(controller.elicitationDraft(bob).isEmpty());
    }

    @Test
    void usingAConversationThatDoesNotExistIsAClearError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> controller.handleResponse(alice, "hi"));
        assertTrue(e.getMessage().contains("elicit"));
        assertThrows(IllegalStateException.class, () -> controller.confirmElicitation(alice));
        assertThrows(IllegalStateException.class, () -> controller.elicitationDraft(alice));
        assertThrows(IllegalStateException.class, () -> controller.correctElicitation(alice, "cleanliness", "x"));
    }

    @Test
    void controllerBuiltWithoutElicitationSupportSaysSo() {
        AgentController plain = new AgentController(agent(), new RankingService());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> plain.runElicitation(alice));
        assertTrue(e.getMessage().contains("not configured"));
    }

    private static LifestyleProfile completeProfile() {
        LifestyleProfile p = new LifestyleProfile();
        LifestyleProfile.DIMENSIONS.forEach(d -> p.set(d, "x"));
        return p;
    }
}
