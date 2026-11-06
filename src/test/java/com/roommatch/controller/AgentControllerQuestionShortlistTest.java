package com.roommatch.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.roommatch.agent.CompatibilityAgent;
import com.roommatch.agent.MatchAnswer;
import com.roommatch.agent.MatchQuestionAnswerer;
import com.roommatch.agent.ProfileAccessTool;
import com.roommatch.agent.ToolManager;
import com.roommatch.model.CompatibilityResult;
import com.roommatch.model.HardFilters;
import com.roommatch.model.LifestyleProfile;
import com.roommatch.model.Shortlist.AddResult;
import com.roommatch.model.User;
import com.roommatch.repository.CandidateRepository;
import com.roommatch.service.RankingService;
import com.roommatch.service.ShortlistService;
import com.roommatch.service.ShortlistStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentControllerQuestionShortlistTest {
    private User me;
    private User cand;
    private int scoreCalls;
    private int questionCalls;
    private final Map<String, List<String>> saved = new HashMap<>();
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
        CandidateRepository repo = new CandidateRepository() {
            @Override public List<User> getCandidates(User u, HardFilters f) { return List.of(cand); }
            @Override public Optional<User> getProfile(String id) { return id.equals("c") ? Optional.of(cand) : Optional.empty(); }
        };
        ToolManager tools = new ToolManager();
        tools.register(new ProfileAccessTool(repo));
        CompatibilityAgent agent = new CompatibilityAgent(tools, (r, c) -> {
            scoreCalls++;
            return new CompatibilityResult(c, 70, 60);
        });
        ShortlistStore store = new ShortlistStore() {
            @Override public List<String> load(String userId) { return saved.getOrDefault(userId, List.of()); }
            @Override public void save(String userId, List<String> ids) { saved.put(userId, new ArrayList<>(ids)); }
        };
        MatchQuestionAnswerer answerer = new MatchQuestionAnswerer(prompt -> {
            questionCalls++;
            return prompt.contains("pets")
                    ? "{\"answerable\": false, \"answer\": \"Nothing about pets.\"}"
                    : "{\"answerable\": true, \"answer\": \"Answer " + questionCalls + "\"}";
        });
        controller = AgentController.builder(agent, new RankingService())
                .questions(answerer).shortlists(new ShortlistService(repo, store)).build();
        controller.getRankedMatches(me, HardFilters.none());
        scoreCalls = 0;
    }

    // ---- F07 ----

    @Test
    void aFreshResultAnswersWithoutRescoring() {
        MatchAnswer a = controller.answerMatchQuestion(me, cand, "How tidy are they?");
        assertEquals("Answer 1", a.text());
        assertTrue(a.answerable());
        assertFalse(a.rescored());
        assertEquals(0, scoreCalls);
    }

    @Test
    void questionsAreNotCachedBecauseEachOneIsDifferent() {
        controller.answerMatchQuestion(me, cand, "q1?");
        controller.answerMatchQuestion(me, cand, "q2?");
        assertEquals(2, questionCalls);
    }

    @Test
    void unanswerableQuestionsAreFlagged() {
        MatchAnswer a = controller.answerMatchQuestion(me, cand, "Do they have pets?");
        assertFalse(a.answerable());
        assertEquals("Nothing about pets.", a.text());
    }

    @Test
    void anOutdatedResultIsRescoredBeforeAnswering() {
        me.applyUpdate(Map.of("budget", "1500"));
        MatchAnswer a = controller.answerMatchQuestion(me, cand, "q?");
        assertTrue(a.rescored());
        assertEquals(1, scoreCalls);
    }

    @Test
    void aBlankQuestionIsRejectedBeforeAnyModelCall() {
        assertThrows(IllegalArgumentException.class, () -> controller.answerMatchQuestion(me, cand, " "));
        assertEquals(0, questionCalls);
    }

    // ---- F09 ----

    @Test
    void bookmarkingSavesAndStartingASessionRestoresIt() {
        assertEquals(AddResult.ADDED, controller.bookmark(me, cand));
        assertEquals(AddResult.ALREADY_BOOKMARKED, controller.bookmark(me, cand));
        assertEquals(List.of("c"), saved.get("me"));

        User sameUserNextDay = user("me");
        controller.startSession(sameUserNextDay);
        assertEquals(1, sameUserNextDay.getShortlist().getBookmarked().size());
        assertEquals(1, controller.getShortlist(sameUserNextDay).size());
    }

    @Test
    void removingBookmarksWorksAndReportsWhetherAnythingWasThere() {
        controller.bookmark(me, cand);
        assertTrue(controller.removeBookmark(me, "c"));
        assertFalse(controller.removeBookmark(me, "c"));
        assertTrue(controller.getShortlist(me).isEmpty());
    }

    @Test
    void featuresThatWereNotConfiguredSaySoClearly() {
        AgentController plain = new AgentController(
                new CompatibilityAgent(new ToolManager(), (r, c) -> new CompatibilityResult(c, 1, 1)), new RankingService());
        assertTrue(assertThrows(IllegalStateException.class, () -> plain.answerMatchQuestion(me, cand, "q?")).getMessage().contains("not configured"));
        assertTrue(assertThrows(IllegalStateException.class, () -> plain.bookmark(me, cand)).getMessage().contains("not configured"));
        assertTrue(assertThrows(IllegalStateException.class, () -> plain.getShortlist(me)).getMessage().contains("not configured"));
        assertDoesNotThrow(() -> plain.startSession(me), "a session still starts without shortlists");
    }
}
